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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.vaadin.flow.internal.CurrentInstance;
import com.vaadin.flow.internal.menu.MenuRegistry;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.menu.AvailableViewInfo;
import com.vaadin.flow.server.startup.ApplicationConfiguration;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.security.HttpSecurityUtils;
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

    private volatile RouteSnapshot routeSnapshot;
    private final VaadinService vaadinService;
    private final Supplier<VaadinSecurityRuntimeConfiguration> runtimeConfiguration;

    public RouteUtil(VaadinService vaadinService) {
        this(
                vaadinService,
                () -> new VaadinSecurityRuntimeConfiguration(
                        Map.of(), Map.of(), "/", VaadinSecurityRuntimeConfig.AnnotationConfigMismatch.OFF));
    }

    public RouteUtil(VaadinService vaadinService, Supplier<VaadinSecurityRuntimeConfiguration> runtimeConfiguration) {
        this.vaadinService = vaadinService;
        this.runtimeConfiguration = runtimeConfiguration;
    }

    public boolean isRouteAllowed(RoutingContext context, SecurityIdentity identity) {
        return checkRouteAccess(context, identity) == RouteAccess.ALLOW;
    }

    public RouteAccess checkRouteAccess(RoutingContext context, SecurityIdentity identity) {
        return checkRouteAccessSafe(context, identity);
    }

    private RouteAccess checkRouteAccessSafe(RoutingContext context, SecurityIdentity identity) {
        RouteSnapshot snapshot = routeSnapshot;
        if (snapshot == null || !snapshot.hierarchyComplete()) {
            collectClientRoutes();
            snapshot = routeSnapshot;
        }
        if (snapshot == null) {
            return RouteAccess.NO_MATCH;
        }
        String requestPath = context.normalizedPath();
        String normalizedCandidate;
        String clientCandidate;
        try {
            VaadinSecurityRuntimeConfiguration configuration = runtimeConfiguration.get();
            normalizedCandidate = configuration.relativizeApplicationPath(HttpSecurityUtils.normalizePath(requestPath));
            clientCandidate = configuration.relativizeApplicationPath(clientRouterPath(requestPath));
        } catch (RuntimeException exception) {
            LOGGER.debug("Cannot normalize Hilla client route path; denying access", exception);
            return RouteAccess.DENY;
        }
        List<CompiledRoute> matchedRoutes = new ArrayList<>(getRoutesByPath(snapshot.routes(), normalizedCandidate));
        if (!normalizedCandidate.equals(clientCandidate)) {
            for (CompiledRoute route : getRoutesByPath(snapshot.routes(), clientCandidate)) {
                if (!matchedRoutes.contains(route)) {
                    matchedRoutes.add(route);
                }
            }
        }
        if (matchedRoutes.isEmpty()) {
            return RouteAccess.NO_MATCH;
        }
        if (!snapshot.hierarchyComplete()) {
            return RouteAccess.DENY;
        }
        for (CompiledRoute route : matchedRoutes) {
            if (!isRouteAccessible(route, identity)) {
                return RouteAccess.DENY;
            }
        }
        return RouteAccess.ALLOW;
    }

    private synchronized void collectClientRoutes() {
        RouteSnapshot snapshot = routeSnapshot;
        if (snapshot != null && snapshot.hierarchyComplete()) {
            return;
        }
        // Route discovery needs a current VaadinService. Matching an already
        // published snapshot does not, so keep thread-local manipulation out of
        // the per-request hot path.
        final var oldInstances = CurrentInstance.getInstances();
        VaadinService.setCurrent(vaadinService);
        try {
            ApplicationConfiguration config = ApplicationConfiguration.get(vaadinService.getContext());
            try {
                URL routesResource = MenuRegistry.getViewsJsonAsResource(config);
                if (routesResource != null) {
                    try (InputStream input = routesResource.openStream()) {
                        List<AvailableViewInfo> routeTree =
                                ROUTE_MAPPER.readValue(input, new TypeReference<List<AvailableViewInfo>>() {});
                        setRouteTree(routeTree);
                        return;
                    }
                }
            } catch (IOException | RuntimeException exception) {
                LOGGER.warn(
                        "Cannot load complete Hilla client route tree; matched client routes will be denied",
                        exception);
            }
            Map<String, AvailableViewInfo> fallbackRoutes = MenuRegistry.collectClientMenuItems(false, config, null);
            setRoutes(fallbackRoutes, false);
        } finally {
            CurrentInstance.clearAll();
            CurrentInstance.restoreInstances(oldInstances);
        }
    }

    void setRoutes(final Map<String, AvailableViewInfo> registeredRoutes) {
        setRoutes(registeredRoutes, true);
    }

    private void setRoutes(final Map<String, AvailableViewInfo> registeredRoutes, boolean hierarchyComplete) {
        if (registeredRoutes == null) {
            routeSnapshot = null;
        } else {
            Map<String, AvailableViewInfo> routes = Map.copyOf(new LinkedHashMap<>(registeredRoutes));
            Map<String, List<List<AvailableViewInfo>>> chains = new LinkedHashMap<>();
            registeredRoutes.forEach((route, view) -> chains.put(route, List.of(List.of(view))));
            routeSnapshot = createSnapshot(routes, chains, hierarchyComplete);
        }
    }

    void setRouteTree(List<AvailableViewInfo> routeTree) {
        Map<String, AvailableViewInfo> routes = new LinkedHashMap<>();
        Map<String, List<List<AvailableViewInfo>>> chains = new LinkedHashMap<>();
        for (AvailableViewInfo route : routeTree) {
            collectRouteTree("", List.of(), route, routes, chains);
        }
        routeSnapshot = createSnapshot(routes, chains, true);
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
        String routePath = appendRoute(parentPath, view.route());
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
        int bestScore = Integer.MIN_VALUE;
        for (CompiledRoute route : availableRoutes) {
            int score = matchScore(route.segments(), pathSegments, 0, 0);
            if (score == Integer.MIN_VALUE) {
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
            return pathIndex == pathSegments.size() ? 0 : Integer.MIN_VALUE;
        }

        SegmentPattern segmentPattern = routeSegments.get(routeIndex);
        if (segmentPattern.type() == SegmentType.WILDCARD) {
            if (routeIndex == routeSegments.size() - 1) {
                return -1;
            }
            int matched = remainingSegmentsCanBeOmitted(routeSegments, routeIndex + 1) ? -1 : Integer.MIN_VALUE;
            if (pathIndex < pathSegments.size() && "*".equals(pathSegments.get(pathIndex))) {
                int remaining = matchScore(routeSegments, pathSegments, routeIndex + 1, pathIndex + 1);
                if (remaining != Integer.MIN_VALUE) {
                    matched = Math.max(matched, remaining - 1);
                }
            }
            return matched;
        }
        int skipped = segmentPattern.optional() && segmentPattern.canBeOmitted()
                ? matchScore(routeSegments, pathSegments, routeIndex + 1, pathIndex)
                : Integer.MIN_VALUE;
        if (pathIndex == pathSegments.size()) {
            return skipped;
        }
        int remaining = matchScore(routeSegments, pathSegments, routeIndex + 1, pathIndex + 1);
        if (remaining == Integer.MIN_VALUE) {
            return skipped;
        }
        String pathSegment = pathSegments.get(pathIndex);
        int consumed = Integer.MIN_VALUE;
        if (segmentPattern.type() == SegmentType.DYNAMIC && segmentPattern.matchesDynamic(pathSegment)) {
            consumed = remaining + (segmentPattern.value().isEmpty() ? 4 : 11);
        } else if (segmentPattern.type() == SegmentType.STATIC
                && segmentPattern.value().equalsIgnoreCase(pathSegment)) {
            consumed = remaining + (segmentPattern.value().isEmpty() ? 2 : 11);
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

    public enum RouteAccess {
        NO_MATCH,
        ALLOW,
        DENY
    }
}
