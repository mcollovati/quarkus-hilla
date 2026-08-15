/*
 * Copyright 2026 Marco Collovati, Dario Götze
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.mcollovati.quarkus.hilla.security;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.time.Duration;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import com.vaadin.flow.internal.CurrentInstance;
import com.vaadin.flow.internal.menu.MenuRegistry;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.menu.AvailableViewInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Discovers and refreshes Hilla file-route manifests and publishes immutable security snapshots.
 */
final class RouteManifestDiscovery {

    // Preserve the existing category so class-specific logging configuration
    // keeps working after extracting discovery from RouteUtil.
    private static final Logger LOGGER = LoggerFactory.getLogger(RouteUtil.class);

    // Keep internal route metadata parsing independent from application-level
    // Jackson customization, consistent with Vaadin's MenuRegistry.
    private static final ObjectMapper ROUTE_MAPPER = JsonMapper.builder()
            .disable(
                    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();
    private static final long DISCOVERY_REFRESH_NANOS = Duration.ofSeconds(1).toNanos();

    private final VaadinService vaadinService;
    private final boolean developmentMode;
    private final boolean fileRoutesManifestExpected;
    private final Supplier<DiscoveryResult> routeDiscovery;
    private final LongSupplier nanoTime;

    private volatile RouteSnapshotCompiler.RouteSnapshot routeSnapshot;
    private volatile DiscoveryState discoveryState = DiscoveryState.UNINITIALIZED;
    private volatile long refreshAfterNanos;
    // Access is guarded by the synchronized discovery monitor.
    private ResourceFingerprint publishedResourceFingerprint;

    RouteManifestDiscovery(VaadinService vaadinService, boolean fileRoutesManifestExpected) {
        this(
                vaadinService,
                !vaadinService.getDeploymentConfiguration().isProductionMode(),
                fileRoutesManifestExpected,
                null,
                System::nanoTime);
    }

    RouteManifestDiscovery(
            VaadinService vaadinService,
            boolean developmentMode,
            boolean fileRoutesManifestExpected,
            Supplier<DiscoveryResult> routeDiscovery,
            LongSupplier nanoTime) {
        this.vaadinService = vaadinService;
        this.developmentMode = developmentMode;
        this.fileRoutesManifestExpected = fileRoutesManifestExpected;
        this.routeDiscovery = routeDiscovery == null ? this::discoverClientRoutes : routeDiscovery;
        this.nanoTime = nanoTime;
    }

    RouteManifestDiscovery(RouteSnapshotCompiler.RouteSnapshot routeSnapshot) {
        this.vaadinService = null;
        this.developmentMode = false;
        this.fileRoutesManifestExpected = true;
        this.routeDiscovery = DiscoveryResult::failure;
        this.nanoTime = System::nanoTime;
        this.routeSnapshot = routeSnapshot;
        this.discoveryState = DiscoveryState.COMPLETE;
    }

    RouteSnapshotCompiler.RouteSnapshot currentSnapshot() {
        ensureRoutesDiscovered();
        return routeSnapshot;
    }

    /**
     * Loads and validates fixed production route snapshot before evaluator publication.
     */
    void initializeProductionSnapshot() {
        if (developmentMode) {
            return;
        }
        ensureRoutesDiscovered();
        if (discoveryState != DiscoveryState.COMPLETE) {
            throw new IllegalStateException(
                    "Cannot initialize Hilla route security because the production file-routes.json manifest is missing, unreadable, invalid, or incomplete");
        }
    }

    private void ensureRoutesDiscovered() {
        DiscoveryState state = discoveryState;
        if (isTerminal(state)) {
            return;
        }
        long now = nanoTime.getAsLong();
        if (!requiresDiscovery(state, now)) {
            return;
        }
        discoverRoutes(now);
    }

    private synchronized void discoverRoutes(long now) {
        DiscoveryState state = discoveryState;
        if (isTerminal(state) || !requiresDiscovery(state, now)) {
            return;
        }

        DiscoveryResult result;
        try {
            result = routeDiscovery.get();
        } catch (RuntimeException exception) {
            logDiscoveryIssue("Cannot discover Hilla client routes; route access cannot be evaluated", exception);
            handleDiscoveryFailure(now);
            return;
        }
        if (result == null) {
            logDiscoveryIssue("Hilla client route discovery returned no result; route access cannot be evaluated");
            handleDiscoveryFailure(now);
            return;
        }

        if (result.unchanged()) {
            RouteSnapshotCompiler.RouteSnapshot currentSnapshot = routeSnapshot;
            if (currentSnapshot != null && currentSnapshot.hierarchyComplete()) {
                completeDiscovery(now);
            } else {
                handleDiscoveryFailure(now);
            }
            return;
        }

        if (result.routeTree() != null) {
            try {
                routeSnapshot = RouteSnapshotCompiler.compileTree(result.routeTree());
                completeDiscovery(now);
                publishedResourceFingerprint = result.resourceFingerprint();
                return;
            } catch (RuntimeException exception) {
                logDiscoveryIssue(
                        "Cannot compile Hilla client route tree; route access cannot be evaluated", exception);
            }
        }
        handleDiscoveryFailure(now, result.retryImmediately());
    }

    private boolean requiresDiscovery(DiscoveryState state, long now) {
        return state == DiscoveryState.UNINITIALIZED
                || (state == DiscoveryState.REFRESH_PENDING && now - refreshAfterNanos >= 0);
    }

    private static boolean isTerminal(DiscoveryState state) {
        return state == DiscoveryState.COMPLETE || state == DiscoveryState.FAILED;
    }

    private DiscoveryResult discoverClientRoutes() {
        // Discovery needs a current VaadinService. Snapshot matching does not,
        // so thread-local manipulation stays outside request evaluation.
        final var oldInstances = CurrentInstance.getInstances();
        VaadinService.setCurrent(vaadinService);
        try {
            var config = vaadinService.getDeploymentConfiguration();
            try {
                return discoverFromResource(MenuRegistry.getViewsJsonAsResource(config));
            } catch (IOException | RuntimeException exception) {
                logDiscoveryIssue(
                        "Cannot load complete Hilla client route tree; route access cannot be evaluated", exception);
            }
            return DiscoveryResult.failure();
        } finally {
            CurrentInstance.clearAll();
            CurrentInstance.restoreInstances(oldInstances);
        }
    }

    /**
     * Must be called while holding the discovery monitor that guards resource fingerprint.
     */
    DiscoveryResult discoverFromResource(URL routesResource) throws IOException {
        if (routesResource != null) {
            return readRouteResource(routesResource, developmentMode ? publishedResourceFingerprint : null);
        }
        if (!developmentMode && !fileRoutesManifestExpected) {
            LOGGER.debug("No Hilla file-route manifest expected; route evaluator owns no client routes");
        }
        return missingManifest(developmentMode, fileRoutesManifestExpected);
    }

    static List<AvailableViewInfo> readRouteTree(InputStream input) throws IOException {
        return ROUTE_MAPPER.readValue(input, new TypeReference<List<AvailableViewInfo>>() {});
    }

    static DiscoveryResult readRouteResource(URL resource, ResourceFingerprint publishedFingerprint)
            throws IOException {
        URLConnection connection = resource.openConnection();
        connection.setUseCaches(false);
        ResourceFingerprint fingerprint = ResourceFingerprint.from(resource, connection);
        if (fingerprint.reliable() && fingerprint.equals(publishedFingerprint)) {
            return DiscoveryResult.unchangedResult();
        }
        try (InputStream input = connection.getInputStream()) {
            return DiscoveryResult.complete(readRouteTree(input), fingerprint);
        }
    }

    static DiscoveryResult missingManifest(boolean developmentMode, boolean fileRoutesManifestExpected) {
        if (!fileRoutesManifestExpected) {
            return DiscoveryResult.complete(List.of());
        }
        return developmentMode ? DiscoveryResult.retryableFailure() : DiscoveryResult.failure();
    }

    private void handleDiscoveryFailure(long now) {
        handleDiscoveryFailure(now, false);
    }

    private void handleDiscoveryFailure(long now, boolean retryImmediately) {
        routeSnapshot = RouteSnapshotCompiler.incompleteSnapshot();
        publishedResourceFingerprint = null;
        if (developmentMode) {
            scheduleRefresh(now, retryImmediately ? 0 : DISCOVERY_REFRESH_NANOS);
        } else {
            discoveryState = DiscoveryState.FAILED;
            LOGGER.error(
                    "Hilla route discovery failed in production because META-INF/VAADIN/file-routes.json is missing, unreadable, invalid, or incomplete; production initialization cannot continue. Enable DEBUG logging for the underlying cause");
        }
    }

    private void logDiscoveryIssue(String message) {
        if (developmentMode) {
            LOGGER.warn(message);
        } else {
            LOGGER.debug(message);
        }
    }

    private void logDiscoveryIssue(String message, Throwable exception) {
        if (developmentMode) {
            LOGGER.warn(message, exception);
        } else {
            LOGGER.debug(message, exception);
        }
    }

    private void completeDiscovery(long now) {
        if (developmentMode) {
            scheduleRefresh(now, DISCOVERY_REFRESH_NANOS);
        } else {
            discoveryState = DiscoveryState.COMPLETE;
        }
    }

    private void scheduleRefresh(long now, long delayNanos) {
        refreshAfterNanos = now + delayNanos;
        discoveryState = DiscoveryState.REFRESH_PENDING;
    }

    private enum DiscoveryState {
        UNINITIALIZED,
        REFRESH_PENDING,
        COMPLETE,
        FAILED
    }

    record DiscoveryResult(
            List<AvailableViewInfo> routeTree,
            ResourceFingerprint resourceFingerprint,
            boolean unchanged,
            boolean retryImmediately) {

        static DiscoveryResult complete(List<AvailableViewInfo> routeTree) {
            return complete(routeTree, null);
        }

        static DiscoveryResult complete(List<AvailableViewInfo> routeTree, ResourceFingerprint fingerprint) {
            return new DiscoveryResult(List.copyOf(routeTree), fingerprint, false, false);
        }

        static DiscoveryResult failure() {
            return new DiscoveryResult(null, null, false, false);
        }

        static DiscoveryResult retryableFailure() {
            return new DiscoveryResult(null, null, false, true);
        }

        static DiscoveryResult unchangedResult() {
            return new DiscoveryResult(null, null, true, false);
        }
    }

    record ResourceFingerprint(String resource, long lastModified, long contentLength) {

        static ResourceFingerprint from(URL resource, URLConnection connection) {
            return new ResourceFingerprint(
                    resource.toExternalForm(), connection.getLastModified(), connection.getContentLengthLong());
        }

        boolean reliable() {
            return lastModified > 0;
        }
    }
}
