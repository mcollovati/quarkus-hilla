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

import java.util.List;
import java.util.Map;

import com.vaadin.flow.server.menu.AvailableViewInfo;
import com.vaadin.flow.server.menu.RouteParamType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteSnapshotCompilerTest {

    @Test
    void compileRoutes_createsFlatSecurityChain() {
        AvailableViewInfo admin = view("admin", new String[] {"ADMIN"}, Map.of());

        RouteSnapshotCompiler.RouteSnapshot snapshot = RouteSnapshotCompiler.compileRoutes(Map.of("admin", admin));

        List<List<AvailableViewInfo>> chains = compiledRoute(snapshot, "admin").target();
        assertTrue(snapshot.hierarchyComplete());
        assertEquals(1, chains.size());
        assertEquals(List.of(admin), chains.getFirst());
    }

    @Test
    void compileTree_layoutSecurityAppliesToIndexAtSamePath() {
        AvailableViewInfo index = view("", new String[0], Map.of());
        AvailableViewInfo layout = view("", new String[] {"ADMIN"}, Map.of(), List.of(index));

        RoutePatternMatcher.CompiledRoute<List<List<AvailableViewInfo>>> route =
                compiledRoute(RouteSnapshotCompiler.compileTree(List.of(layout)), "");

        assertTrue(route.index());
        assertEquals(2, route.target().size());
        assertArrayEquals(
                new String[] {"ADMIN"}, route.target().getFirst().getFirst().rolesAllowed());
        assertArrayEquals(
                new String[] {"ADMIN"}, route.target().getLast().getFirst().rolesAllowed());
        assertEquals(index, route.target().getLast().getLast());
    }

    @Test
    void compileTree_layoutSecurityAppliesToParameterizedChild() {
        AvailableViewInfo child = view(":id", new String[0], Map.of(":id", RouteParamType.REQUIRED));
        AvailableViewInfo layout = view("admin", new String[] {"ADMIN"}, Map.of(), List.of(child));

        List<AvailableViewInfo> chain = compiledRoute(RouteSnapshotCompiler.compileTree(List.of(layout)), "/admin/:id")
                .target()
                .getFirst();

        assertEquals(List.of(layout, child), chain);
    }

    @Test
    void compileTree_layoutSecurityAppliesToNestedAbsoluteChild() {
        AvailableViewInfo child = view("/admin/users", new String[0], Map.of());
        AvailableViewInfo layout = view("admin", new String[] {"ADMIN"}, Map.of(), List.of(child));

        List<AvailableViewInfo> chain = compiledRoute(
                        RouteSnapshotCompiler.compileTree(List.of(layout)), "/admin/users")
                .target()
                .getFirst();

        assertEquals(List.of(layout, child), chain);
    }

    @Test
    void compileTree_nestedAbsoluteChildUsesReactPrefixSlicing() {
        AvailableViewInfo child = view("/administrator/users", new String[0], Map.of());
        AvailableViewInfo layout = view("admin", new String[] {"ADMIN"}, Map.of(), List.of(child));

        RouteSnapshotCompiler.RouteSnapshot snapshot = RouteSnapshotCompiler.compileTree(List.of(layout));

        assertEquals(
                List.of(layout, child),
                compiledRoute(snapshot, "/admin/istrator/users").target().getFirst());
    }

    @Test
    void compileTree_invalidAbsoluteChildIsRejected() {
        AvailableViewInfo invalidChild = view("/users", new String[0], Map.of());
        AvailableViewInfo layout = view("admin", new String[] {"ADMIN"}, Map.of(), List.of(invalidChild));

        assertThrows(IllegalArgumentException.class, () -> RouteSnapshotCompiler.compileTree(List.of(layout)));
    }

    private static RoutePatternMatcher.CompiledRoute<List<List<AvailableViewInfo>>> compiledRoute(
            RouteSnapshotCompiler.RouteSnapshot snapshot, String path) {
        return snapshot.routes().stream()
                .filter(route -> path.equals(route.path()))
                .findFirst()
                .orElseThrow();
    }

    private static AvailableViewInfo view(
            String route, String[] rolesAllowed, Map<String, RouteParamType> routeParameters) {
        return view(route, rolesAllowed, routeParameters, List.of());
    }

    private static AvailableViewInfo view(
            String route,
            String[] rolesAllowed,
            Map<String, RouteParamType> routeParameters,
            List<AvailableViewInfo> children) {
        return new AvailableViewInfo(
                route, rolesAllowed, true, route, false, true, null, children, routeParameters, false, null);
    }
}
