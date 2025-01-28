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

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import com.vaadin.flow.internal.menu.MenuRegistry;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.menu.AvailableViewInfo;
import com.vaadin.flow.server.startup.ApplicationConfiguration;
import io.quarkus.vertx.http.runtime.security.ImmutablePathMatcher;
import io.vertx.ext.web.RoutingContext;

public class RouteUtil {

    private Map<String, AvailableViewInfo> registeredRoutes = null;
    private final VaadinService vaadinService;

    public RouteUtil(VaadinService vaadinService) {
        this.vaadinService = vaadinService;
    }

    public void setRoutes(final Map<String, AvailableViewInfo> registeredRoutes) {
        if (registeredRoutes == null) {
            this.registeredRoutes = null;
        } else {
            this.registeredRoutes = new HashMap<>(registeredRoutes);
        }
    }

    public boolean isRouteAllowed(RoutingContext context, Predicate<? super String> roleAuthentication) {
        if (registeredRoutes == null) {
            collectClientRoutes();
        }
        var viewConfig = getRouteData(context, roleAuthentication);
        return viewConfig.isPresent();
    }

    private static void filterClientViews(
            Map<String, AvailableViewInfo> configurations,
            RoutingContext context,
            Predicate<? super String> roleAuthentication) {
        final boolean isUserAuthenticated = context.user() != null;

        Set<String> clientEntries = new HashSet<>(configurations.keySet());
        clientEntries.stream().filter(configurations::containsKey).forEach(key -> {
            final AvailableViewInfo viewInfo = configurations.get(key);
            final boolean routeValid = validateViewAccessible(viewInfo, isUserAuthenticated, roleAuthentication);
            if (!routeValid) {
                removePathRecursive(configurations, viewInfo, key);
            }
        });
    }

    private static boolean validateViewAccessible(
            AvailableViewInfo viewInfo, boolean isUserAuthenticated, Predicate<? super String> roleAuthentication) {
        if (viewInfo.loginRequired() && !isUserAuthenticated) {
            return false;
        }
        String[] roles = viewInfo.rolesAllowed();
        return roles == null || roles.length == 0 || Arrays.stream(roles).anyMatch(roleAuthentication);
    }

    public static void removePathRecursive(
            Map<String, AvailableViewInfo> configurations, AvailableViewInfo viewInfo, String parentPath) {
        configurations.remove(parentPath);
        if (viewInfo.children() == null) return;
        for (AvailableViewInfo child : viewInfo.children()) {
            String childRoute = (parentPath + "/" + child.route()).replace("//", "/");
            removePathRecursive(configurations, child, childRoute);
        }
    }

    private Optional<AvailableViewInfo> getRouteData(
            RoutingContext context, Predicate<? super String> roleAuthentication) {
        String path = context.normalizedPath();
        Map<String, AvailableViewInfo> availableRoutes = new HashMap<>(registeredRoutes);
        filterClientViews(availableRoutes, context, roleAuthentication);
        return Optional.ofNullable(getRouteByPath(availableRoutes, path));
    }

    private void collectClientRoutes() {
        ApplicationConfiguration config = ApplicationConfiguration.get(vaadinService.getContext());
        setRoutes(MenuRegistry.collectClientMenuItems(false, config, null));
    }

    protected synchronized AvailableViewInfo getRouteByPath(
            Map<String, AvailableViewInfo> availableRoutes, String path) {
        final var matcherBuilder = ImmutablePathMatcher.<AvailableViewInfo>builder();
        availableRoutes.forEach(matcherBuilder::addPath);
        return matcherBuilder.build().match(path).getValue();
    }
}
