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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import com.vaadin.flow.internal.CurrentInstance;
import com.vaadin.flow.internal.menu.MenuRegistry;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.startup.ApplicationConfiguration;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.security.ImmutablePathMatcher;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class RouteUtil {

    // Match MenuRegistry's metadata parsing without its lossy menu flattening.
    private static final ObjectMapper ROUTE_MAPPER = JsonMapper.builder()
            .disable(
                    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    private volatile ImmutablePathMatcher<List<RouteEntry>> routeMatcher;

    /**
     * A node of {@code file-routes.json}. Unlike {@link com.vaadin.flow.server.menu.AvailableViewInfo}
     * it keeps {@code skipLayouts}, which decides whether the layouts around a view protect it.
     */
    record RouteEntry(
            String route,
            boolean loginRequired,
            String[] rolesAllowed,
            boolean skipLayouts,
            List<RouteEntry> children) {

        boolean hasChildren() {
            return children != null && !children.isEmpty();
        }

        boolean hasPath() {
            return route != null && !route.isEmpty();
        }

        boolean isIndex() {
            return !hasPath() && !hasChildren();
        }
    }

    private final VaadinService vaadinService;

    public RouteUtil(VaadinService vaadinService) {
        this.vaadinService = vaadinService;
    }

    public boolean isRouteAllowed(RoutingContext context, SecurityIdentity identity) {
        List<RouteEntry> requirements = getRouteData(context);
        return requirements != null
                && requirements.stream()
                        .allMatch(view -> validateViewAccessible(view, !identity.isAnonymous(), identity::hasRole));
    }

    boolean hasRoute(RoutingContext context) {
        return getRouteData(context) != null;
    }

    private List<RouteEntry> getRouteData(RoutingContext context) {
        // Ensure that discovery can access the current service and restore any previous instances.
        final var oldInstances = CurrentInstance.getInstances();
        VaadinService.setCurrent(vaadinService);
        try {
            if (routeMatcher == null) {
                collectClientRoutes();
            }
            // Match before checking access, so a denied route cannot fall back to a public wildcard.
            return routeMatcher.match(context.normalizedPath()).getValue();
        } finally {
            CurrentInstance.clearAll();
            CurrentInstance.restoreInstances(oldInstances);
        }
    }

    // Not synchronized: it runs on event loop threads, and concurrent first calls
    // only build the same immutable matcher twice; the volatile field publishes it.
    private void collectClientRoutes() {
        ApplicationConfiguration config = ApplicationConfiguration.get(vaadinService.getContext());
        Map<String, List<RouteEntry>> routes = new HashMap<>();
        var resource = MenuRegistry.getViewsJsonAsResource(config);
        if (resource != null) {
            try (var input = resource.openStream()) {
                List<RouteEntry> tree = ROUTE_MAPPER.readValue(input, new TypeReference<List<RouteEntry>>() {});
                if (tree != null) {
                    for (RouteEntry view : tree) {
                        collectRouteRequirements("", List.of(), view, routes);
                    }
                }
            } catch (IOException | JacksonException exception) {
                LoggerFactory.getLogger(RouteUtil.class).warn("Cannot load Hilla client route metadata", exception);
            }
        }
        final var matcherBuilder = ImmutablePathMatcher.<List<RouteEntry>>builder();
        routes.forEach((route, requirements) -> matcherBuilder.addPath(route, List.copyOf(requirements)));
        routeMatcher = matcherBuilder.build();
    }

    // Registers the views the client router renders for a URL, each with the
    // requirements of the layouts around it, following React Router and Hilla:
    // - a layout without a path is not a route of its own;
    // - a layout with a path is rendered for its URL only if it has no index view;
    // - a view with skipLayouts is rendered without the layouts around it, so
    //   their requirements do not apply (Hilla's mergeSkipLayouts).
    private static void collectRouteRequirements(
            String parentPath, List<RouteEntry> ancestors, RouteEntry view, Map<String, List<RouteEntry>> routes) {
        String path = view.hasPath() ? (parentPath + "/" + view.route()).replace("//", "/") : parentPath;
        path = PathUtil.ensureSlashBegin(path);
        List<RouteEntry> requirements = new ArrayList<>(view.skipLayouts() ? List.of() : ancestors);
        requirements.add(view);
        boolean rendered = view.isIndex()
                || !view.hasChildren()
                || (view.hasPath() && view.children().stream().noneMatch(RouteEntry::isIndex));
        if (rendered) {
            // Several views can share a URL, e.g. in sibling pathless layouts: require all of them.
            routes.computeIfAbsent(path, ignored -> new ArrayList<>()).addAll(requirements);
        }
        if (view.hasChildren()) {
            for (RouteEntry child : view.children()) {
                collectRouteRequirements(path, requirements, child, routes);
            }
        }
    }

    private static boolean validateViewAccessible(
            RouteEntry viewInfo, boolean isUserAuthenticated, Predicate<? super String> roleAuthentication) {
        if (viewInfo.loginRequired() && !isUserAuthenticated) {
            return false;
        }
        String[] roles = viewInfo.rolesAllowed();
        return roles == null || roles.length == 0 || Arrays.stream(roles).anyMatch(roleAuthentication);
    }
}
