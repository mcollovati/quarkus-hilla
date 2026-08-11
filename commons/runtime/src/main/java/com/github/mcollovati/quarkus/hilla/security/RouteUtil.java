/*
 * Copyright 2025-2026 Marco Collovati, Dario Götze
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
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import com.vaadin.flow.internal.CurrentInstance;
import com.vaadin.flow.internal.menu.MenuRegistry;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.menu.AvailableViewInfo;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evaluates routes generated in Hilla's {@code file-routes.json} manifest.
 *
 * <p>Routes declared only in a custom {@code routes.tsx}, including security
 * metadata on custom parent layouts, are not present in that manifest and are
 * therefore outside this evaluator. Callers must apply another authorization
 * source to those routes. Incomplete manifest data never produces an allow or
 * no-match decision.
 */
public class RouteUtil {

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
    private final Supplier<DiscoveryResult> routeDiscovery;
    private final LongSupplier nanoTime;

    private volatile RouteSnapshot routeSnapshot;
    private volatile DiscoveryState discoveryState = DiscoveryState.UNINITIALIZED;
    private volatile long refreshAfterNanos;
    private ResourceFingerprint publishedResourceFingerprint;

    public RouteUtil(VaadinService vaadinService) {
        this.vaadinService = vaadinService;
        this.developmentMode = !vaadinService.getDeploymentConfiguration().isProductionMode();
        this.routeDiscovery = this::discoverClientRoutes;
        this.nanoTime = System::nanoTime;
    }

    RouteUtil(VaadinService vaadinService, Supplier<DiscoveryResult> routeDiscovery, LongSupplier nanoTime) {
        this(vaadinService, true, routeDiscovery, nanoTime);
    }

    RouteUtil(
            VaadinService vaadinService,
            boolean developmentMode,
            Supplier<DiscoveryResult> routeDiscovery,
            LongSupplier nanoTime) {
        this.vaadinService = vaadinService;
        this.developmentMode = developmentMode;
        this.routeDiscovery = routeDiscovery;
        this.nanoTime = nanoTime;
    }

    public boolean isRouteAllowed(RoutingContext context, SecurityIdentity identity) {
        return checkRouteAccess(context, identity) == AuthorizationDecision.ALLOW;
    }

    AuthorizationDecision checkRouteAccess(RoutingContext context, SecurityIdentity identity) {
        ensureRoutesDiscovered();
        RouteSnapshot snapshot = routeSnapshot;
        if (snapshot == null) {
            return AuthorizationDecision.DENY;
        }

        List<RoutePatternMatcher.CompiledRoute<List<List<AvailableViewInfo>>>> matchedRoutes;
        try {
            matchedRoutes = RoutePatternMatcher.bestMatches(snapshot.routes(), context.normalizedPath());
        } catch (RuntimeException exception) {
            LOGGER.debug("Cannot normalize Hilla client route path; denying access", exception);
            return AuthorizationDecision.DENY;
        }

        if (!snapshot.hierarchyComplete() || identity == null) {
            return AuthorizationDecision.DENY;
        }
        if (matchedRoutes.isEmpty()) {
            return AuthorizationDecision.NO_MATCH;
        }
        for (RoutePatternMatcher.CompiledRoute<List<List<AvailableViewInfo>>> route : matchedRoutes) {
            if (!isRouteAccessible(route, identity)) {
                return AuthorizationDecision.DENY;
            }
        }
        return AuthorizationDecision.ALLOW;
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
            LOGGER.warn("Cannot discover Hilla client routes; route access cannot be evaluated", exception);
            handleDiscoveryFailure(now);
            return;
        }
        if (result == null) {
            LOGGER.warn("Hilla client route discovery returned no result; route access cannot be evaluated");
            handleDiscoveryFailure(now);
            return;
        }

        if (result.unchanged()) {
            RouteSnapshot currentSnapshot = routeSnapshot;
            if (currentSnapshot != null && currentSnapshot.hierarchyComplete()) {
                completeDiscovery(now);
            } else {
                handleDiscoveryFailure(now);
            }
            return;
        }

        if (result.routeTree() != null) {
            try {
                publishRouteTree(result.routeTree(), now);
                publishedResourceFingerprint = result.resourceFingerprint();
                return;
            } catch (RuntimeException exception) {
                LOGGER.warn("Cannot compile Hilla client route tree; route access cannot be evaluated", exception);
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
        // Route discovery needs a current VaadinService. Matching an already
        // published snapshot does not, so keep thread-local manipulation out of
        // the per-request hot path.
        final var oldInstances = CurrentInstance.getInstances();
        VaadinService.setCurrent(vaadinService);
        try {
            var config = vaadinService.getDeploymentConfiguration();
            try {
                URL routesResource = MenuRegistry.getViewsJsonAsResource(config);
                if (routesResource != null) {
                    return readRouteResource(routesResource, developmentMode ? publishedResourceFingerprint : null);
                }
                boolean reactEnabled = config.isReactEnabled();
                boolean customRouter = false;
                if (developmentMode && reactEnabled) {
                    var frontendFolder = config.getFrontendFolder().toPath();
                    customRouter = Files.isRegularFile(frontendFolder.resolve("routes.tsx"))
                            || Files.isRegularFile(frontendFolder.resolve("routes.ts"));
                }
                return missingManifest(developmentMode, reactEnabled, customRouter);
            } catch (IOException | RuntimeException exception) {
                LOGGER.warn(
                        "Cannot load complete Hilla client route tree; route access cannot be evaluated", exception);
            }
            return DiscoveryResult.failure();
        } finally {
            CurrentInstance.clearAll();
            CurrentInstance.restoreInstances(oldInstances);
        }
    }

    void setCompleteRoutesForTesting(Map<String, AvailableViewInfo> registeredRoutes) {
        publishCompleteRoutes(registeredRoutes);
        discoveryState = DiscoveryState.COMPLETE;
    }

    void setCompleteRouteTreeForTesting(List<AvailableViewInfo> routeTree) {
        publishRouteTreeSnapshot(routeTree);
        discoveryState = DiscoveryState.COMPLETE;
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

    private void publishRouteTree(List<AvailableViewInfo> routeTree, long now) {
        publishRouteTreeSnapshot(routeTree);
        completeDiscovery(now);
    }

    private void publishRouteTreeSnapshot(List<AvailableViewInfo> routeTree) {
        Map<String, AvailableViewInfo> routes = new LinkedHashMap<>();
        Map<String, List<List<AvailableViewInfo>>> chains = new LinkedHashMap<>();
        for (AvailableViewInfo route : routeTree) {
            collectRouteTree("", List.of(), route, routes, chains);
        }
        routeSnapshot = createSnapshot(routes, chains, true);
    }

    private void handleDiscoveryFailure(long now) {
        handleDiscoveryFailure(now, false);
    }

    private void handleDiscoveryFailure(long now, boolean retryImmediately) {
        RouteSnapshot currentSnapshot = routeSnapshot;
        if (currentSnapshot != null && currentSnapshot.hierarchyComplete()) {
            completeDiscovery(now);
            return;
        }
        routeSnapshot = new RouteSnapshot(List.of(), false);
        if (developmentMode) {
            scheduleRefresh(now, retryImmediately ? 0 : DISCOVERY_REFRESH_NANOS);
        } else {
            discoveryState = DiscoveryState.FAILED;
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

    static DiscoveryResult missingManifest(boolean developmentMode, boolean reactEnabled, boolean customRouter) {
        // React/Hilla normally produces file-routes.json. During a development
        // startup it can briefly be unavailable while frontend generation is
        // still running, so deny and retry instead of publishing an empty tree.
        // Applications with a custom client router can legitimately have no
        // manifest, in which case this evaluator owns no routes.
        return developmentMode && reactEnabled && !customRouter
                ? DiscoveryResult.retryableFailure()
                : DiscoveryResult.complete(List.of());
    }

    private void publishCompleteRoutes(Map<String, AvailableViewInfo> registeredRoutes) {
        Map<String, AvailableViewInfo> routes =
                registeredRoutes == null ? Map.of() : new LinkedHashMap<>(registeredRoutes);
        Map<String, List<List<AvailableViewInfo>>> chains = new LinkedHashMap<>();
        routes.forEach((route, view) -> chains.put(route, List.of(List.of(view))));
        routeSnapshot = createSnapshot(routes, chains, true);
    }

    private static RouteSnapshot createSnapshot(
            Map<String, AvailableViewInfo> routes,
            Map<String, List<List<AvailableViewInfo>>> securityChains,
            boolean hierarchyComplete) {
        List<RoutePatternMatcher.CompiledRoute<List<List<AvailableViewInfo>>>> compiledRoutes =
                new ArrayList<>(routes.size());
        for (String route : routes.keySet()) {
            List<List<AvailableViewInfo>> chains = securityChains.get(route);
            List<List<AvailableViewInfo>> immutableChains = chains == null ? List.of() : List.copyOf(chains);
            compiledRoutes.add(
                    RoutePatternMatcher.compile(route, containsIndexRoute(immutableChains), immutableChains));
        }
        return new RouteSnapshot(List.copyOf(compiledRoutes), hierarchyComplete);
    }

    private static void collectRouteTree(
            String parentPath,
            List<AvailableViewInfo> ancestors,
            AvailableViewInfo view,
            Map<String, AvailableViewInfo> routes,
            Map<String, List<List<AvailableViewInfo>>> chains) {
        String routePath;
        try {
            routePath = appendRoute(parentPath, view.route());
        } catch (IllegalArgumentException exception) {
            LOGGER.error("Ignoring invalid Hilla client route '{}' below '{}'", view.route(), parentPath, exception);
            return;
        }
        List<AvailableViewInfo> securityChain = new ArrayList<>(ancestors);
        securityChain.add(view);
        routes.putIfAbsent(routePath, view);
        chains.computeIfAbsent(routePath, ignored -> new ArrayList<>()).add(List.copyOf(securityChain));
        if (view.children() != null) {
            for (AvailableViewInfo child : view.children()) {
                collectRouteTree(routePath, securityChain, child, routes, chains);
            }
        }
    }

    private static String appendRoute(String parentPath, String route) {
        if (route == null || route.isEmpty()) {
            return parentPath;
        }
        if (route.startsWith("/")) {
            String absoluteRoute = normalizeRoutePath(route);
            if (!parentPath.isEmpty() && !absoluteRoute.startsWith(parentPath)) {
                throw new IllegalArgumentException(
                        "Absolute child route '" + route + "' must start with parent path '" + parentPath + "'");
            }
            return normalizeRoutePath(parentPath + "/" + absoluteRoute.substring(parentPath.length()));
        }
        return normalizeRoutePath(parentPath + "/" + route);
    }

    private static String normalizeRoutePath(String route) {
        return route.replaceAll("/+", "/");
    }

    private static boolean isRouteAccessible(
            RoutePatternMatcher.CompiledRoute<List<List<AvailableViewInfo>>> route, SecurityIdentity identity) {
        List<List<AvailableViewInfo>> chains = route.target();
        return chains != null && !chains.isEmpty() && allChainsAccessible(chains, identity);
    }

    private static boolean containsIndexRoute(List<List<AvailableViewInfo>> chains) {
        for (List<AvailableViewInfo> chain : chains) {
            if (!chain.isEmpty()) {
                AvailableViewInfo target = chain.getLast();
                if ((target.route() == null || target.route().isEmpty())
                        && (target.children() == null || target.children().isEmpty())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean allChainsAccessible(List<List<AvailableViewInfo>> chains, SecurityIdentity identity) {
        for (List<AvailableViewInfo> chain : chains) {
            for (AvailableViewInfo view : chain) {
                if (!validateViewAccessible(view, identity)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean validateViewAccessible(AvailableViewInfo viewInfo, SecurityIdentity identity) {
        if (viewInfo.loginRequired() && identity.isAnonymous()) {
            return false;
        }
        String[] roles = viewInfo.rolesAllowed();
        if (roles == null || roles.length == 0) {
            return true;
        }
        for (String role : roles) {
            if (identity.hasRole(role)) {
                return true;
            }
        }
        return false;
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

    private record RouteSnapshot(
            List<RoutePatternMatcher.CompiledRoute<List<List<AvailableViewInfo>>>> routes, boolean hierarchyComplete) {}
}
