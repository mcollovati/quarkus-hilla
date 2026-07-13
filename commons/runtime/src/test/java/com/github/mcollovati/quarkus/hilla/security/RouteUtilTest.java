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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.menu.AvailableViewInfo;
import com.vaadin.flow.server.menu.RouteParamType;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RouteUtilTest {

    @Test
    void legacyConstructor_usesRootApplicationPath() throws Exception {
        RouteUtil routeUtil = new RouteUtil(mock(VaadinService.class));
        routeUtil.setRoutes(Map.of("admin", view("admin", new String[] {"ADMIN"}, Map.of())));

        assertEquals(RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/admin"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_requiredParameterEnforcesRouteRoles() throws Exception {
        RouteUtil routeUtil = routeUtil(
                "/",
                Map.of("users/:id", view("users/:id", new String[] {"ADMIN"}, Map.of(":id", RouteParamType.REQUIRED))));

        assertEquals(RouteUtil.RouteAccess.DENY, routeUtil.checkRouteAccess(context("/users/42"), identity("USER")));
        assertEquals(RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/users/42"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_optionalParameterEnforcesRouteRolesWithAndWithoutValue() throws Exception {
        RouteUtil routeUtil = routeUtil(
                "/",
                Map.of(
                        "reports/:filter?",
                        view("reports/:filter?", new String[] {"ADMIN"}, Map.of(":filter?", RouteParamType.OPTIONAL))));

        assertEquals(RouteUtil.RouteAccess.DENY, routeUtil.checkRouteAccess(context("/reports"), identity("USER")));
        assertEquals(
                RouteUtil.RouteAccess.DENY, routeUtil.checkRouteAccess(context("/reports/open"), identity("USER")));
        assertEquals(
                RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/reports/open"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_optionalStaticSegmentEnforcesRouteRolesWithAndWithoutSegment() throws Exception {
        RouteUtil routeUtil = routeUtil("/", Map.of("projects?", view("projects?", new String[] {"ADMIN"}, Map.of())));

        assertEquals(RouteUtil.RouteAccess.DENY, routeUtil.checkRouteAccess(context("/"), identity("USER")));
        assertEquals(RouteUtil.RouteAccess.DENY, routeUtil.checkRouteAccess(context("/projects"), identity("USER")));
        assertEquals(RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/"), identity("ADMIN")));
        assertEquals(RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/projects"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_canonicalizesEncodedPathBeforeRootRelativization() throws Exception {
        RouteUtil routeUtil =
                routeUtil("/app/", Map.of("hilla-admin", view("hilla-admin", new String[] {"ADMIN"}, Map.of())));

        assertEquals(
                RouteUtil.RouteAccess.DENY,
                routeUtil.checkRouteAccess(context("/app%2Fhilla%2Dadmin"), identity("USER")));
        assertEquals(
                RouteUtil.RouteAccess.ALLOW,
                routeUtil.checkRouteAccess(context("/app%2Fhilla%2Dadmin"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_preservesEncodedSlashInsideClientRouteParameter() throws Exception {
        RouteUtil routeUtil = routeUtil(
                "/",
                Map.of("items/:id", view("items/:id", new String[] {"ADMIN"}, Map.of(":id", RouteParamType.REQUIRED))));

        assertEquals(RouteUtil.RouteAccess.DENY, routeUtil.checkRouteAccess(context("/items/a%2Fb"), identity("USER")));
        assertEquals(
                RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/items/a%2Fb"), identity("ADMIN")));
        assertEquals(
                RouteUtil.RouteAccess.NO_MATCH, routeUtil.checkRouteAccess(context("/items/a/b"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_staticRouteWinsOverParameterRoute() throws Exception {
        Map<String, AvailableViewInfo> routes = new LinkedHashMap<>();
        routes.put("users/:id", view("users/:id", new String[] {"ADMIN"}, Map.of(":id", RouteParamType.REQUIRED)));
        routes.put("users/new", view("users/new", new String[] {"USER"}, Map.of()));
        RouteUtil routeUtil = routeUtil("/", routes);

        assertEquals(RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/users/new"), identity("USER")));
    }

    @Test
    void checkRouteAccess_deniedSpecificRouteDoesNotFallBackToPublicParameterRoute() throws Exception {
        Map<String, AvailableViewInfo> routes = new LinkedHashMap<>();
        routes.put("users/:id", view("users/:id", new String[0], Map.of(":id", RouteParamType.REQUIRED)));
        routes.put("users/new", view("users/new", new String[] {"ADMIN"}, Map.of()));
        RouteUtil routeUtil = routeUtil("/", routes);

        assertEquals(RouteUtil.RouteAccess.DENY, routeUtil.checkRouteAccess(context("/users/new"), identity("USER")));
        assertEquals(RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/users/new"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_equalScoreMatchesMustAllPermit() throws Exception {
        Map<String, AvailableViewInfo> routes = new LinkedHashMap<>();
        routes.put("items/:id", view("items/:id", new String[0], Map.of(":id", RouteParamType.REQUIRED)));
        routes.put(
                "items/:slug", view("items/:slug", new String[] {"ADMIN"}, Map.of(":slug", RouteParamType.REQUIRED)));
        RouteUtil routeUtil = routeUtil("/", routes);

        assertEquals(RouteUtil.RouteAccess.DENY, routeUtil.checkRouteAccess(context("/items/42"), identity("USER")));
        assertEquals(RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/items/42"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_parameterSuffixMustMatchBeforeOutrankingWildcard() throws Exception {
        Map<String, AvailableViewInfo> routes = new LinkedHashMap<>();
        routes.put("files/:id.json", view("files/:id.json", new String[0], Map.of()));
        routes.put("files/*", view("files/*", new String[] {"ADMIN"}, Map.of("*", RouteParamType.WILDCARD)));
        RouteUtil routeUtil = routeUtil("/", routes);

        assertEquals(RouteUtil.RouteAccess.DENY, routeUtil.checkRouteAccess(context("/files/foo"), identity("USER")));
        assertEquals(RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/files/foo"), identity("ADMIN")));
        assertEquals(
                RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/files/foo.json"), identity("USER")));
    }

    @Test
    void checkRouteAccess_parameterSuffixUsesReactStaticScoreAndKeepsEqualMatchesRestrictive() throws Exception {
        Map<String, AvailableViewInfo> routes = new LinkedHashMap<>();
        routes.put("files/:id.json", view("files/:id.json", new String[] {"ADMIN"}, Map.of()));
        routes.put("files/public.json", view("files/public.json", new String[0], Map.of()));
        RouteUtil routeUtil = routeUtil("/", routes);

        assertEquals(
                RouteUtil.RouteAccess.DENY,
                routeUtil.checkRouteAccess(context("/files/public.json"), identity("USER")));
        assertEquals(
                RouteUtil.RouteAccess.ALLOW,
                routeUtil.checkRouteAccess(context("/files/public.json"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_optionalParameterWithSuffixKeepsSegmentRequired() throws Exception {
        RouteUtil routeUtil =
                routeUtil("/", Map.of("files/:id?.json", view("files/:id?.json", new String[] {"ADMIN"}, Map.of())));

        assertEquals(RouteUtil.RouteAccess.NO_MATCH, routeUtil.checkRouteAccess(context("/files"), identity("ADMIN")));
        assertEquals(RouteUtil.RouteAccess.DENY, routeUtil.checkRouteAccess(context("/files/.json"), identity("USER")));
        assertEquals(
                RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/files/foo.json"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_layoutSecurityAppliesToIndexAtSamePath() {
        AvailableViewInfo index = view("", new String[0], Map.of());
        AvailableViewInfo layout = view("", new String[] {"ADMIN"}, Map.of(), List.of(index));
        RouteUtil routeUtil = routeUtil("/", List.of(layout));

        assertEquals(RouteUtil.RouteAccess.DENY, routeUtil.checkRouteAccess(context("/"), identity("USER")));
        assertEquals(RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_layoutSecurityAppliesToParameterizedChild() {
        AvailableViewInfo child = view(":id", new String[0], Map.of(":id", RouteParamType.REQUIRED));
        AvailableViewInfo layout = view("admin", new String[] {"ADMIN"}, Map.of(), List.of(child));
        RouteUtil routeUtil = routeUtil("/", List.of(layout));

        assertEquals(RouteUtil.RouteAccess.DENY, routeUtil.checkRouteAccess(context("/admin/42"), identity("USER")));
        assertEquals(RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/admin/42"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_layoutSecurityAppliesToNestedAbsoluteChildRoute() {
        AvailableViewInfo child = view("/admin/users", new String[0], Map.of());
        AvailableViewInfo layout = view("admin", new String[] {"ADMIN"}, Map.of(), List.of(child));
        RouteUtil routeUtil = routeUtil("/", List.of(layout));

        assertEquals(RouteUtil.RouteAccess.DENY, routeUtil.checkRouteAccess(context("/admin/users"), identity("USER")));
        assertEquals(
                RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/admin/users"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_nestedAbsoluteChildRouteUsesReactPrefixSlicing() {
        AvailableViewInfo child = view("/administrator/users", new String[0], Map.of());
        AvailableViewInfo layout = view("admin", new String[] {"ADMIN"}, Map.of(), List.of(child));
        RouteUtil routeUtil = routeUtil("/", List.of(layout));

        assertEquals(
                RouteUtil.RouteAccess.DENY,
                routeUtil.checkRouteAccess(context("/admin/istrator/users"), identity("USER")));
        assertEquals(
                RouteUtil.RouteAccess.ALLOW,
                routeUtil.checkRouteAccess(context("/admin/istrator/users"), identity("ADMIN")));
        assertEquals(
                RouteUtil.RouteAccess.NO_MATCH,
                routeUtil.checkRouteAccess(context("/administrator/users"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_wildcardParameterEnforcesRouteRoles() throws Exception {
        RouteUtil routeUtil = routeUtil(
                "/", Map.of("files/*", view("files/*", new String[] {"ADMIN"}, Map.of("*", RouteParamType.WILDCARD))));

        assertEquals(RouteUtil.RouteAccess.DENY, routeUtil.checkRouteAccess(context("/files/a/b"), identity("USER")));
        assertEquals(RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/files/a/b"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_wildcardBeforeOptionalParameterMatchesReactOptionalExpansion() throws Exception {
        RouteUtil routeUtil = routeUtil(
                "/",
                Map.of(
                        "files/*/:id?",
                        view(
                                "files/*/:id?",
                                new String[] {"ADMIN"},
                                Map.of("*", RouteParamType.WILDCARD, ":id?", RouteParamType.OPTIONAL))));

        assertEquals(RouteUtil.RouteAccess.DENY, routeUtil.checkRouteAccess(context("/files/a/b"), identity("USER")));
        assertEquals(RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/files/a/b"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_nonTerminalWildcardMatchesReactLiteralBranch() throws Exception {
        RouteUtil routeUtil = routeUtil(
                "/",
                Map.of(
                        "files/*/:id",
                        view(
                                "files/*/:id",
                                new String[] {"ADMIN"},
                                Map.of("*", RouteParamType.WILDCARD, ":id", RouteParamType.REQUIRED))));

        assertEquals(RouteUtil.RouteAccess.DENY, routeUtil.checkRouteAccess(context("/files/*/42"), identity("USER")));
        assertEquals(
                RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/files/*/42"), identity("ADMIN")));
        assertEquals(
                RouteUtil.RouteAccess.NO_MATCH, routeUtil.checkRouteAccess(context("/files/a/42"), identity("ADMIN")));
    }

    private static RouteUtil routeUtil(String rootPath, Map<String, AvailableViewInfo> routes) throws Exception {
        VaadinSecurityRuntimeConfiguration configuration = new VaadinSecurityRuntimeConfiguration(
                Map.of(), Map.of(), rootPath, VaadinSecurityRuntimeConfig.AnnotationConfigMismatch.OFF);
        RouteUtil routeUtil = new RouteUtil(mock(VaadinService.class), () -> configuration);
        routeUtil.setRoutes(new LinkedHashMap<>(routes));
        return routeUtil;
    }

    private static RouteUtil routeUtil(String rootPath, List<AvailableViewInfo> routeTree) {
        VaadinSecurityRuntimeConfiguration configuration = new VaadinSecurityRuntimeConfiguration(
                Map.of(), Map.of(), rootPath, VaadinSecurityRuntimeConfig.AnnotationConfigMismatch.OFF);
        RouteUtil routeUtil = new RouteUtil(mock(VaadinService.class), () -> configuration);
        routeUtil.setRouteTree(routeTree);
        return routeUtil;
    }

    private static RoutingContext context(String path) {
        RoutingContext context = mock(RoutingContext.class);
        when(context.normalizedPath()).thenReturn(path);
        return context;
    }

    private static SecurityIdentity identity(String... roles) {
        return TestSecurityIdentity.authenticated("user", roles);
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
