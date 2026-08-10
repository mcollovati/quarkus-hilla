/*
 * Copyright 2025 Marco Collovati, Dario Götze
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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.vaadin.flow.internal.CurrentInstance;
import com.vaadin.flow.internal.menu.MenuRegistry;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.menu.AvailableViewInfo;
import com.vaadin.flow.server.startup.ApplicationConfiguration;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.ext.web.RoutingContext;
import org.jboss.logging.Logger;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class RouteUtil {

    private static final Logger LOGGER = Logger.getLogger(RouteUtil.class);
    private static final ObjectMapper ROUTE_MAPPER = JsonMapper.builder()
            .disable(
                    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();
    private static final Pattern DYNAMIC_SEGMENT = Pattern.compile("^:([\\w-]+)(\\?)?(.*)$");
    private static final Pattern OPTIONAL_STATIC_SEGMENT = Pattern.compile("^[\\w-]+\\?$");
    private static final long DISCOVERY_RETRY_NANOS = Duration.ofSeconds(1).toNanos();
    private static final int NO_MATCH_SCORE = Integer.MIN_VALUE;
    private static final int WILDCARD_SCORE = -1;
    private static final int EMPTY_SEGMENT_SCORE = 2;
    private static final int DYNAMIC_SEGMENT_SCORE = 4;
    private static final int STATIC_SEGMENT_SCORE = 11;

    private final VaadinService vaadinService;
    private final Supplier<DiscoveryResult> routeDiscovery;
    private final LongSupplier nanoTime;

    private volatile RouteSnapshot routeSnapshot;
    private volatile DiscoveryState discoveryState = DiscoveryState.UNINITIALIZED;
    private volatile long retryAfterNanos;

    public RouteUtil(VaadinService vaadinService) {
        this.vaadinService = vaadinService;
        this.routeDiscovery = this::discoverClientRoutes;
        this.nanoTime = System::nanoTime;
    }

    RouteUtil(VaadinService vaadinService, Supplier<DiscoveryResult> routeDiscovery, LongSupplier nanoTime) {
        this.vaadinService = vaadinService;
        this.routeDiscovery = routeDiscovery;
        this.nanoTime = nanoTime;
    }

    public boolean isRouteAllowed(RoutingContext context, SecurityIdentity identity) {
        return checkRouteAccess(context, identity) == RouteAccess.ALLOW;
    }

    RouteAccess checkRouteAccess(RoutingContext context, SecurityIdentity identity) {
        ensureRoutesDiscovered();
        RouteSnapshot snapshot = routeSnapshot;
        if (snapshot == null) {
            return RouteAccess.NO_MATCH;
        }

        List<CompiledRoute> matchedRoutes;
        try {
            String requestPath = context.normalizedPath();
            String normalizedCandidate = requestPath;
            String clientCandidate = clientRouterPath(requestPath);
            matchedRoutes = new ArrayList<>(getRoutesByPath(snapshot.routes(), normalizedCandidate));
            if (!normalizedCandidate.equals(clientCandidate)) {
                for (CompiledRoute route : getRoutesByPath(snapshot.routes(), clientCandidate)) {
                    if (!matchedRoutes.contains(route)) {
                        matchedRoutes.add(route);
                    }
                }
            }
        } catch (RuntimeException exception) {
            LOGGER.debug("Cannot normalize Hilla client route path; denying access", exception);
            return RouteAccess.DENY;
        }

        if (matchedRoutes.isEmpty()) {
            return RouteAccess.NO_MATCH;
        }
        if (!snapshot.hierarchyComplete() || identity == null) {
            return RouteAccess.DENY;
        }
        for (CompiledRoute route : matchedRoutes) {
            if (!isRouteAccessible(route, identity)) {
                return RouteAccess.DENY;
            }
        }
        return RouteAccess.ALLOW;
    }

    private void ensureRoutesDiscovered() {
        DiscoveryState state = discoveryState;
        long now = nanoTime.getAsLong();
        if (state == DiscoveryState.COMPLETE
                || state == DiscoveryState.FALLBACK
                || (state == DiscoveryState.RETRY_PENDING && now - retryAfterNanos < 0)) {
            return;
        }
        discoverRoutes(now);
    }

    private synchronized void discoverRoutes(long now) {
        DiscoveryState state = discoveryState;
        if (state == DiscoveryState.COMPLETE
                || state == DiscoveryState.FALLBACK
                || (state == DiscoveryState.RETRY_PENDING && now - retryAfterNanos < 0)) {
            return;
        }

        DiscoveryResult result;
        try {
            result = routeDiscovery.get();
        } catch (RuntimeException exception) {
            LOGGER.warn("Cannot discover Hilla client routes; route access cannot be evaluated", exception);
            publishFallback(Map.of(), true, now);
            return;
        }
        if (result == null) {
            LOGGER.warn("Hilla client route discovery returned no result; route access cannot be evaluated");
            publishFallback(Map.of(), true, now);
            return;
        }

        if (result.routeTree() != null) {
            try {
                publishRouteTree(result.routeTree());
                return;
            } catch (RuntimeException exception) {
                LOGGER.warn("Cannot compile Hilla client route tree; route access cannot be evaluated", exception);
            }
        }
        publishFallback(result.fallbackRoutes(), result.retryable(), now);
    }

    private DiscoveryResult discoverClientRoutes() {
        // Route discovery needs a current VaadinService. Matching an already
        // published snapshot does not, so keep thread-local manipulation out of
        // the per-request hot path.
        final var oldInstances = CurrentInstance.getInstances();
        VaadinService.setCurrent(vaadinService);
        try {
            ApplicationConfiguration config = ApplicationConfiguration.get(vaadinService.getContext());
            boolean retryable = !config.isProductionMode();
            try {
                URL routesResource = MenuRegistry.getViewsJsonAsResource(config);
                if (routesResource != null) {
                    try (InputStream input = routesResource.openStream()) {
                        return DiscoveryResult.complete(readRouteTree(input));
                    }
                }
            } catch (IOException | RuntimeException exception) {
                LOGGER.warn(
                        "Cannot load complete Hilla client route tree; route access cannot be evaluated", exception);
            }
            Map<String, AvailableViewInfo> fallbackRoutes;
            try {
                fallbackRoutes = MenuRegistry.collectClientMenuItems(false, config, null);
            } catch (RuntimeException exception) {
                LOGGER.warn("Cannot collect fallback Hilla client routes; route access cannot be evaluated", exception);
                fallbackRoutes = Map.of();
            }
            return DiscoveryResult.fallback(fallbackRoutes, retryable);
        } finally {
            CurrentInstance.clearAll();
            CurrentInstance.restoreInstances(oldInstances);
        }
    }

    void setRoutes(Map<String, AvailableViewInfo> registeredRoutes) {
        publishRoutes(registeredRoutes, true);
        discoveryState = DiscoveryState.COMPLETE;
    }

    void setRouteTree(List<AvailableViewInfo> routeTree) {
        publishRouteTree(routeTree);
    }

    static List<AvailableViewInfo> readRouteTree(InputStream input) throws IOException {
        return ROUTE_MAPPER.readValue(input, new TypeReference<List<AvailableViewInfo>>() {});
    }

    private void publishRouteTree(List<AvailableViewInfo> routeTree) {
        Map<String, AvailableViewInfo> routes = new LinkedHashMap<>();
        Map<String, List<List<AvailableViewInfo>>> chains = new LinkedHashMap<>();
        for (AvailableViewInfo route : routeTree) {
            collectRouteTree("", List.of(), route, routes, chains);
        }
        routeSnapshot = createSnapshot(routes, chains, true);
        discoveryState = DiscoveryState.COMPLETE;
    }

    private void publishFallback(Map<String, AvailableViewInfo> fallbackRoutes, boolean retryable, long now) {
        publishRoutes(fallbackRoutes, false);
        if (retryable) {
            retryAfterNanos = now + DISCOVERY_RETRY_NANOS;
            discoveryState = DiscoveryState.RETRY_PENDING;
        } else {
            discoveryState = DiscoveryState.FALLBACK;
        }
    }

    private void publishRoutes(Map<String, AvailableViewInfo> registeredRoutes, boolean hierarchyComplete) {
        Map<String, AvailableViewInfo> routes =
                registeredRoutes == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(registeredRoutes));
        Map<String, List<List<AvailableViewInfo>>> chains = new LinkedHashMap<>();
        routes.forEach((route, view) -> chains.put(route, List.of(List.of(view))));
        routeSnapshot = createSnapshot(routes, chains, hierarchyComplete);
    }

    private static RouteSnapshot createSnapshot(
            Map<String, AvailableViewInfo> routes,
            Map<String, List<List<AvailableViewInfo>>> securityChains,
            boolean hierarchyComplete) {
        List<CompiledRoute> compiledRoutes = new ArrayList<>(routes.size());
        for (String route : routes.keySet()) {
            List<List<AvailableViewInfo>> chains = securityChains.get(route);
            compiledRoutes.add(
                    new CompiledRoute(route, routeSegments(route), chains == null ? List.of() : List.copyOf(chains)));
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
            LOGGER.errorf(exception, "Ignoring invalid Hilla client route '%s' below '%s'", view.route(), parentPath);
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

    private static boolean isRouteAccessible(CompiledRoute route, SecurityIdentity identity) {
        List<List<AvailableViewInfo>> chains = route.securityChains();
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

    private static List<CompiledRoute> getRoutesByPath(List<CompiledRoute> availableRoutes, String path) {
        List<CompiledRoute> bestMatches = new ArrayList<>();
        List<String> pathSegments = segments(path);
        int bestScore = NO_MATCH_SCORE;
        for (CompiledRoute route : availableRoutes) {
            int score = matchScore(route.segments(), pathSegments, 0, 0);
            if (score == NO_MATCH_SCORE) {
                continue;
            }
            if (score > bestScore) {
                bestMatches.clear();
                bestScore = score;
            }
            if (score == bestScore) {
                bestMatches.add(route);
            }
        }
        return List.copyOf(bestMatches);
    }

    private static int matchScore(
            List<SegmentPattern> routeSegments, List<String> pathSegments, int routeIndex, int pathIndex) {
        if (routeIndex == routeSegments.size()) {
            return pathIndex == pathSegments.size() ? 0 : NO_MATCH_SCORE;
        }

        // Relative weights follow React Router branch ranking: literal and
        // suffixed segments outrank plain parameters, while wildcards rank last.
        SegmentPattern segmentPattern = routeSegments.get(routeIndex);
        if (segmentPattern.type() == SegmentType.WILDCARD) {
            if (routeIndex == routeSegments.size() - 1) {
                return WILDCARD_SCORE;
            }
            int matched =
                    remainingSegmentsCanBeOmitted(routeSegments, routeIndex + 1) ? WILDCARD_SCORE : NO_MATCH_SCORE;
            if (pathIndex < pathSegments.size() && "*".equals(pathSegments.get(pathIndex))) {
                int remaining = matchScore(routeSegments, pathSegments, routeIndex + 1, pathIndex + 1);
                if (remaining != NO_MATCH_SCORE) {
                    matched = Math.max(matched, remaining + WILDCARD_SCORE);
                }
            }
            return matched;
        }
        int skipped = segmentPattern.optional() && segmentPattern.canBeOmitted()
                ? matchScore(routeSegments, pathSegments, routeIndex + 1, pathIndex)
                : NO_MATCH_SCORE;
        if (pathIndex == pathSegments.size()) {
            return skipped;
        }
        int remaining = matchScore(routeSegments, pathSegments, routeIndex + 1, pathIndex + 1);
        if (remaining == NO_MATCH_SCORE) {
            return skipped;
        }
        String pathSegment = pathSegments.get(pathIndex);
        int consumed = NO_MATCH_SCORE;
        if (segmentPattern.type() == SegmentType.DYNAMIC && segmentPattern.matchesDynamic(pathSegment)) {
            consumed = remaining + (segmentPattern.value().isEmpty() ? DYNAMIC_SEGMENT_SCORE : STATIC_SEGMENT_SCORE);
        } else if (segmentPattern.type() == SegmentType.STATIC
                && segmentPattern.value().equalsIgnoreCase(pathSegment)) {
            consumed = remaining + (segmentPattern.value().isEmpty() ? EMPTY_SEGMENT_SCORE : STATIC_SEGMENT_SCORE);
        }
        return Math.max(skipped, consumed);
    }

    private static boolean remainingSegmentsCanBeOmitted(List<SegmentPattern> routeSegments, int routeIndex) {
        for (int index = routeIndex; index < routeSegments.size(); index++) {
            SegmentPattern remaining = routeSegments.get(index);
            if (!remaining.optional() || !remaining.canBeOmitted()) {
                return false;
            }
        }
        return true;
    }

    private static SegmentPattern segmentPattern(String routeSegment) {
        if ("*".equals(routeSegment)) {
            return new SegmentPattern(SegmentType.WILDCARD, "", false);
        }
        Matcher dynamic = DYNAMIC_SEGMENT.matcher(routeSegment);
        if (dynamic.matches()) {
            return new SegmentPattern(SegmentType.DYNAMIC, dynamic.group(3), dynamic.group(2) != null);
        }
        if (OPTIONAL_STATIC_SEGMENT.matcher(routeSegment).matches()) {
            return new SegmentPattern(SegmentType.STATIC, routeSegment.substring(0, routeSegment.length() - 1), true);
        }
        return new SegmentPattern(SegmentType.STATIC, routeSegment, false);
    }

    private static List<String> segments(String path) {
        if (path == null || path.isBlank() || "/".equals(path)) {
            return List.of();
        }
        int start = 0;
        int end = path.length();
        while (start < end && path.charAt(start) == '/') {
            start++;
        }
        while (end > start && path.charAt(end - 1) == '/') {
            end--;
        }
        return start == end
                ? List.of()
                : Arrays.asList(path.substring(start, end).split("/", -1));
    }

    private static List<SegmentPattern> routeSegments(String route) {
        if (route != null && route.endsWith("*") && !"*".equals(route) && !route.endsWith("/*")) {
            route = route.substring(0, route.length() - 1) + "/*";
        }
        List<String> segments = segments(route);
        List<SegmentPattern> patterns = new ArrayList<>(segments.size());
        for (String segment : segments) {
            patterns.add(segmentPattern(segment));
        }
        return List.copyOf(patterns);
    }

    private static String clientRouterPath(String path) {
        if (path.indexOf('%') < 0) {
            return path;
        }
        return String.join(
                "/",
                Arrays.stream(path.split("/", -1))
                        .map(RouteUtil::decodeClientRouterSegment)
                        .toList());
    }

    private static String decodeClientRouterSegment(String segment) {
        try {
            return URLDecoder.decode(segment.replace("+", "%2B"), StandardCharsets.UTF_8)
                    .replace("/", "%2F");
        } catch (IllegalArgumentException exception) {
            return segment;
        }
    }

    enum RouteAccess {
        NO_MATCH,
        ALLOW,
        DENY
    }

    private enum DiscoveryState {
        UNINITIALIZED,
        RETRY_PENDING,
        COMPLETE,
        FALLBACK
    }

    record DiscoveryResult(
            List<AvailableViewInfo> routeTree, Map<String, AvailableViewInfo> fallbackRoutes, boolean retryable) {

        static DiscoveryResult complete(List<AvailableViewInfo> routeTree) {
            return new DiscoveryResult(List.copyOf(routeTree), Map.of(), false);
        }

        static DiscoveryResult fallback(Map<String, AvailableViewInfo> fallbackRoutes, boolean retryable) {
            return new DiscoveryResult(
                    null,
                    fallbackRoutes == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(fallbackRoutes)),
                    retryable);
        }
    }

    private enum SegmentType {
        STATIC,
        DYNAMIC,
        WILDCARD
    }

    private record SegmentPattern(SegmentType type, String value, boolean optional) {

        boolean canBeOmitted() {
            return type == SegmentType.STATIC || value.isEmpty();
        }

        boolean matchesDynamic(String pathSegment) {
            if (!endsWithIgnoreCase(pathSegment, value)) {
                return false;
            }
            int parameterLength = pathSegment.length() - value.length();
            return optional ? parameterLength >= 0 : parameterLength > 0;
        }

        private static boolean endsWithIgnoreCase(String value, String suffix) {
            return value.regionMatches(true, value.length() - suffix.length(), suffix, 0, suffix.length());
        }
    }

    private record CompiledRoute(
            String path, List<SegmentPattern> segments, List<List<AvailableViewInfo>> securityChains) {}

    private record RouteSnapshot(List<CompiledRoute> routes, boolean hierarchyComplete) {}
}
