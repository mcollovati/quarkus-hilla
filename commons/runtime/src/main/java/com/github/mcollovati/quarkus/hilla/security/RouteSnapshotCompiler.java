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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.vaadin.flow.server.menu.AvailableViewInfo;

/**
 * Compiles Hilla route metadata into immutable, hierarchy-aware security snapshots.
 */
final class RouteSnapshotCompiler {

    private RouteSnapshotCompiler() {}

    static RouteSnapshot compileTree(List<AvailableViewInfo> routeTree) {
        Map<String, AvailableViewInfo> routes = new LinkedHashMap<>();
        Map<String, List<List<AvailableViewInfo>>> chains = new LinkedHashMap<>();
        for (AvailableViewInfo route : routeTree) {
            collectRouteTree("", List.of(), route, routes, chains);
        }
        return createSnapshot(routes, chains, true);
    }

    static RouteSnapshot compileRoutes(Map<String, AvailableViewInfo> registeredRoutes) {
        Map<String, AvailableViewInfo> routes =
                registeredRoutes == null ? Map.of() : new LinkedHashMap<>(registeredRoutes);
        Map<String, List<List<AvailableViewInfo>>> chains = new LinkedHashMap<>();
        routes.forEach((route, view) -> chains.put(route, List.of(List.of(view))));
        return createSnapshot(routes, chains, true);
    }

    static RouteSnapshot incompleteSnapshot() {
        return new RouteSnapshot(List.of(), false);
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

    record RouteSnapshot(
            List<RoutePatternMatcher.CompiledRoute<List<List<AvailableViewInfo>>>> routes, boolean hierarchyComplete) {}
}
