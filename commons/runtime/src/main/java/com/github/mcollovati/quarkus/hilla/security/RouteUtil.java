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

import java.util.List;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.menu.AvailableViewInfo;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Evaluates routes generated in Hilla's {@code file-routes.json} manifest.
 *
 * <p>Routes declared only in a custom {@code routes.tsx}, including security
 * metadata on custom parent layouts, are not present in that manifest and are
 * therefore outside this evaluator. Callers must apply another authorization
 * source to those routes. An expected manifest that is incomplete or cannot be
 * read never produces an allow or no-match decision: access remains denied. An
 * absent manifest can instead mean that this evaluator owns no file routes.
 */
public class RouteUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(RouteUtil.class);

    private final RouteManifestDiscovery manifestDiscovery;

    public RouteUtil(VaadinService vaadinService) {
        this(vaadinService, true);
    }

    public RouteUtil(VaadinService vaadinService, boolean fileRoutesManifestExpected) {
        this(new RouteManifestDiscovery(vaadinService, fileRoutesManifestExpected));
    }

    RouteUtil(
            VaadinService vaadinService,
            Supplier<RouteManifestDiscovery.DiscoveryResult> routeDiscovery,
            LongSupplier nanoTime) {
        this(vaadinService, true, true, routeDiscovery, nanoTime);
    }

    RouteUtil(
            VaadinService vaadinService,
            boolean developmentMode,
            Supplier<RouteManifestDiscovery.DiscoveryResult> routeDiscovery,
            LongSupplier nanoTime) {
        this(vaadinService, developmentMode, true, routeDiscovery, nanoTime);
    }

    RouteUtil(
            VaadinService vaadinService,
            boolean developmentMode,
            boolean fileRoutesManifestExpected,
            Supplier<RouteManifestDiscovery.DiscoveryResult> routeDiscovery,
            LongSupplier nanoTime) {
        this(new RouteManifestDiscovery(
                vaadinService, developmentMode, fileRoutesManifestExpected, routeDiscovery, nanoTime));
    }

    RouteUtil(RouteSnapshotCompiler.RouteSnapshot routeSnapshot) {
        this(new RouteManifestDiscovery(routeSnapshot));
    }

    private RouteUtil(RouteManifestDiscovery manifestDiscovery) {
        this.manifestDiscovery = manifestDiscovery;
    }

    public boolean isRouteAllowed(RoutingContext context, SecurityIdentity identity) {
        return checkRouteAccess(context, identity) == AuthorizationDecision.ALLOW;
    }

    /**
     * Loads and validates the fixed production route snapshot before this evaluator is published.
     *
     * @throws IllegalStateException
     *             if expected production route metadata cannot be loaded completely
     */
    void initializeProductionSnapshot() {
        manifestDiscovery.initializeProductionSnapshot();
    }

    AuthorizationDecision checkRouteAccess(RoutingContext context, SecurityIdentity identity) {
        RouteSnapshotCompiler.RouteSnapshot snapshot = manifestDiscovery.currentSnapshot();
        if (snapshot == null || !snapshot.hierarchyComplete() || identity == null) {
            return AuthorizationDecision.DENY;
        }

        List<RoutePatternMatcher.CompiledRoute<List<List<AvailableViewInfo>>>> matchedRoutes;
        try {
            matchedRoutes = RoutePatternMatcher.bestMatches(snapshot.routes(), context.normalizedPath());
        } catch (RuntimeException exception) {
            LOGGER.debug("Cannot normalize Hilla client route path; denying access", exception);
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

    private static boolean isRouteAccessible(
            RoutePatternMatcher.CompiledRoute<List<List<AvailableViewInfo>>> route, SecurityIdentity identity) {
        List<List<AvailableViewInfo>> chains = route.target();
        return chains != null && !chains.isEmpty() && allChainsAccessible(chains, identity);
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
}
