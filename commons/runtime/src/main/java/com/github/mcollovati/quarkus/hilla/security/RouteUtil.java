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

import com.vaadin.flow.internal.CurrentInstance;
import com.vaadin.flow.internal.menu.MenuRegistry;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.menu.AvailableViewInfo;
import com.vaadin.flow.server.startup.ApplicationConfiguration;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.security.ImmutablePathMatcher;
import io.vertx.ext.web.RoutingContext;

public class RouteUtil {

    private Map<String, AvailableViewInfo> registeredRoutes = null;
    private final VaadinService vaadinService;

    public RouteUtil(VaadinService vaadinService) {
        this.vaadinService = vaadinService;
    }

    public boolean isRouteAllowed(RoutingContext context, SecurityIdentity identity) {
        // Ensure that the VaadinService is set for the current thread, so that collectClientRoutes can access it.
        // The current instances should always be null, but for safety, we restore them after the operation.
        final var oldInstances = CurrentInstance.getInstances();
        VaadinService.setCurrent(vaadinService);
        try {
            return isRouteAllowedSafe(context, identity);
        } finally {
            CurrentInstance.clearAll();
            CurrentInstance.restoreInstances(oldInstances);
        }
    }

    private boolean isRouteAllowedSafe(RoutingContext context, SecurityIdentity identity) {
        if (registeredRoutes == null) {
            collectClientRoutes();
        }
        var viewConfig = getRouteData(context.normalizedPath(), identity);
        return viewConfig.isPresent();
    }

    private void collectClientRoutes() {
        ApplicationConfiguration config = ApplicationConfiguration.get(vaadinService.getContext());
        setRoutes(MenuRegistry.collectClientMenuItems(false, config, null));
    }

    private void setRoutes(final Map<String, AvailableViewInfo> registeredRoutes) {
        if (registeredRoutes == null) {
            this.registeredRoutes = null;
        } else {
            this.registeredRoutes = new HashMap<>(registeredRoutes);
        }
    }

    private Optional<AvailableViewInfo> getRouteData(String path, SecurityIdentity identity) {
        Map<String, AvailableViewInfo> availableRoutes = new HashMap<>(registeredRoutes);
        filterClientViews(availableRoutes, identity);
        return Optional.ofNullable(getRouteByPath(availableRoutes, path));
    }

    private static void filterClientViews(Map<String, AvailableViewInfo> configurations, SecurityIdentity identity) {
        final boolean isUserAuthenticated = !identity.isAnonymous();
        Set<String> clientEntries = new HashSet<>(configurations.keySet());
        // configurations::containsKey is used to avoid ConcurrentModificationException
        clientEntries.stream().filter(configurations::containsKey).forEach(path -> {
            final AvailableViewInfo viewInfo = configurations.get(path);
            final boolean routeValid = validateViewAccessible(viewInfo, isUserAuthenticated, identity::hasRole);
            if (!routeValid) {
                removePathRecursive(configurations, viewInfo, path);
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

    private static void removePathRecursive(
            Map<String, AvailableViewInfo> configurations, AvailableViewInfo viewInfo, String parentPath) {
        configurations.remove(parentPath);
        if (viewInfo.children() == null) return;
        for (AvailableViewInfo child : viewInfo.children()) {
            String childRoute = (parentPath + "/" + child.route()).replace("//", "/");
            removePathRecursive(configurations, child, childRoute);
        }
    }

    private AvailableViewInfo getRouteByPath(Map<String, AvailableViewInfo> availableRoutes, String path) {
        final var matcherBuilder = ImmutablePathMatcher.<AvailableViewInfo>builder();
        availableRoutes.forEach((route, info) -> {
            matcherBuilder.addPath(PathUtil.ensureSlashBegin(route), info);
        });
        return matcherBuilder.build().match(path).getValue();
    }
}
