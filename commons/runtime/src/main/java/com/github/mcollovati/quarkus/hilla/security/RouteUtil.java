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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.vaadin.flow.internal.CurrentInstance;
import com.vaadin.flow.internal.menu.MenuRegistry;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinServletContext;
import com.vaadin.flow.server.menu.AvailableViewInfo;
import com.vaadin.flow.server.startup.ApplicationConfiguration;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Looks up client routes and checks the matching views' access requirements without inheriting layout requirements.
 * For each raw and normalized path, all highest-scoring matching candidates are combined strictly (fail closed),
 * because the client may reorder equally ranked routes. A route is secured if any candidate requires login or roles;
 * access is allowed only if every candidate allows the identity.
 */
public class RouteUtil {

    // Match MenuRegistry's metadata parsing while retaining the tree for paths and score ranking.
    private static final ObjectMapper ROUTE_MAPPER = JsonMapper.builder()
            .disable(
                    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    private volatile ClientRouteMatcher clientRouteMatcher;
    private final VaadinService vaadinService;

    /**
     * Creates a route lookup for the given service.
     *
     * @param vaadinService the service providing client routes
     */
    public RouteUtil(VaadinService vaadinService) {
        this.vaadinService = vaadinService;
    }

    /**
     * Checks access to the client views matched by the raw and normalized paths without inheriting layout requirements.
     * All equally ranked candidates must allow access, because the client may reorder them.
     *
     * @param context the request context
     * @param identity the requesting user's identity
     * @return whether at least one path matches and every matched candidate allows the identity
     */
    public boolean isRouteAllowed(RoutingContext context, SecurityIdentity identity) {
        return getRouteData(context)
                .map(views -> views.stream()
                        .allMatch(view -> validateViewAccessible(view, !identity.isAnonymous(), identity::hasRole)))
                .orElse(false);
    }

    /**
     * Checks whether any highest-scoring candidate for either the raw or normalized path requires login or roles.
     * Equally ranked candidates are combined strictly, because the client may reorder them.
     *
     * @param context the request context
     * @return whether any matched candidate requires authentication
     */
    boolean isSecuredRoute(RoutingContext context) {
        // Match before filtering, so a secured route cannot fall back to a public wildcard.
        return getRouteData(context)
                .map(views -> views.stream().anyMatch(view -> !validateViewAccessible(view, false, role -> false)))
                .orElse(false);
    }

    private Optional<List<AvailableViewInfo>> getRouteData(RoutingContext context) {
        ensureClientRoutes();
        // Raw and normalized paths can resolve to different views; neither may weaken the other's requirements.
        List<AvailableViewInfo> views = Stream.of(context.request().path(), context.normalizedPath())
                .filter(Objects::nonNull)
                .distinct()
                .flatMap(path -> clientRouteMatcher.match(path).stream())
                .flatMap(List::stream)
                .distinct()
                .toList();
        return views.isEmpty() ? Optional.empty() : Optional.of(views);
    }

    private void ensureClientRoutes() {
        if (clientRouteMatcher != null) {
            return;
        }
        initializeClientRoutes();
    }

    private synchronized void initializeClientRoutes() {
        if (clientRouteMatcher != null) {
            return;
        }
        // Route discovery needs the current service; restore any previous instances afterward.
        final var oldInstances = CurrentInstance.getInstances();
        VaadinService.setCurrent(vaadinService);
        try {
            ApplicationConfiguration config = ApplicationConfiguration.get(vaadinService.getContext());
            var resource = MenuRegistry.getViewsJsonAsResource(config);
            if (resource != null) {
                try (var input = resource.openStream()) {
                    List<AvailableViewInfo> routes =
                            ROUTE_MAPPER.readValue(input, new TypeReference<List<AvailableViewInfo>>() {});
                    if (routes != null) {
                        clientRouteMatcher = ClientRouteMatcher.forFileRoutes(routes, basename(vaadinService));
                        return;
                    }
                } catch (IOException | JacksonException exception) {
                    LoggerFactory.getLogger(RouteUtil.class).warn("Cannot load Hilla client route metadata", exception);
                }
            }
            // Fall back to flattened metadata when the route tree is unavailable.
            clientRouteMatcher = new ClientRouteMatcher(
                    MenuRegistry.collectClientMenuItems(false, config, null), basename(vaadinService));
        } finally {
            CurrentInstance.clearAll();
            CurrentInstance.restoreInstances(oldInstances);
        }
    }

    /**
     * Returns the context root used as the client router's basename.
     *
     * @param vaadinService the Vaadin service
     * @return the basename, ending with a slash
     */
    static String basename(VaadinService vaadinService) {
        String contextPath = "";
        if (vaadinService.getContext() instanceof VaadinServletContext servletContext
                && servletContext.getContext() != null
                && servletContext.getContext().getContextPath() != null) {
            contextPath = servletContext.getContext().getContextPath().replaceFirst("/+$", "");
        }
        return contextPath + "/";
    }

    private static boolean validateViewAccessible(
            AvailableViewInfo viewInfo, boolean isUserAuthenticated, Predicate<? super String> roleAuthentication) {
        if (viewInfo.loginRequired() && !isUserAuthenticated) {
            return false;
        }
        String[] roles = viewInfo.rolesAllowed();
        return roles == null || roles.length == 0 || Arrays.stream(roles).anyMatch(roleAuthentication);
    }
}
