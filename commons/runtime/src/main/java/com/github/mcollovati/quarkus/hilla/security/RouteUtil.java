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
import java.util.Optional;
import java.util.function.Predicate;

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

public class RouteUtil {

    // Match MenuRegistry's metadata parsing without its lossy menu flattening.
    private static final ObjectMapper ROUTE_MAPPER = JsonMapper.builder()
            .disable(
                    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    private volatile ClientRouteMatcher routeMatcher;
    private final VaadinService vaadinService;

    public RouteUtil(VaadinService vaadinService) {
        this.vaadinService = vaadinService;
    }

    public boolean isRouteAllowed(RoutingContext context, SecurityIdentity identity) {
        return getRouteData(context)
                .map(requirements -> requirements.stream()
                        .allMatch(view -> validateViewAccessible(view, !identity.isAnonymous(), identity::hasRole)))
                .orElse(false);
    }

    boolean hasRoute(RoutingContext context) {
        return getRouteData(context).isPresent();
    }

    private Optional<List<AvailableViewInfo>> getRouteData(RoutingContext context) {
        // Ensure that discovery can access the current service and restore any previous instances.
        final var oldInstances = CurrentInstance.getInstances();
        VaadinService.setCurrent(vaadinService);
        try {
            if (routeMatcher == null) {
                collectClientRoutes();
            }
            // Match before checking access, so a denied route cannot fall back to a public wildcard: the route is
            // the one React Router renders, with the requirements of all its ancestors.
            return routeMatcher.requirements(context.normalizedPath());
        } finally {
            CurrentInstance.clearAll();
            CurrentInstance.restoreInstances(oldInstances);
        }
    }

    // Not synchronized: it runs on event loop threads, and concurrent first calls
    // only build the same immutable matcher twice; the volatile field publishes it.
    private void collectClientRoutes() {
        ApplicationConfiguration config = ApplicationConfiguration.get(vaadinService.getContext());
        List<AvailableViewInfo> tree = List.of();
        var resource = MenuRegistry.getViewsJsonAsResource(config);
        if (resource != null) {
            try (var input = resource.openStream()) {
                List<AvailableViewInfo> routes =
                        ROUTE_MAPPER.readValue(input, new TypeReference<List<AvailableViewInfo>>() {});
                if (routes != null) {
                    tree = routes;
                }
            } catch (IOException | JacksonException exception) {
                LoggerFactory.getLogger(RouteUtil.class).warn("Cannot load Hilla client route metadata", exception);
            }
        }
        routeMatcher = ClientRouteMatcher.forFileRoutes(tree, basename(vaadinService));
    }

    /**
     * Returns the basename of the client router, the path of the document base URI, which Vaadin sets to the
     * context root of the Vaadin servlet. The request path that the HTTP layer checks includes it.
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
