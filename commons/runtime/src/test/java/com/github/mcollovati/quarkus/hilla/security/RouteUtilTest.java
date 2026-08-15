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
import java.util.Set;

import com.vaadin.flow.server.menu.AvailableViewInfo;
import com.vaadin.flow.server.menu.RouteParamType;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RouteUtilTest {

    @Test
    void checkRouteAccess_requiredParameterEnforcesRouteRoles() {
        RouteUtil routeUtil = routeUtil(
                Map.of("users/:id", view("users/:id", new String[] {"ADMIN"}, Map.of(":id", RouteParamType.REQUIRED))));

        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context("/users/42"), identity("USER")));
        assertEquals(AuthorizationDecision.ALLOW, routeUtil.checkRouteAccess(context("/users/42"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_deniedSpecificRouteDoesNotFallBackToPublicParameterRoute() {
        Map<String, AvailableViewInfo> routes = new LinkedHashMap<>();
        routes.put("users/:id", view("users/:id", new String[0], Map.of(":id", RouteParamType.REQUIRED)));
        routes.put("users/new", view("users/new", new String[] {"ADMIN"}, Map.of()));
        RouteUtil routeUtil = routeUtil(routes);

        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context("/users/new"), identity("USER")));
        assertEquals(AuthorizationDecision.ALLOW, routeUtil.checkRouteAccess(context("/users/new"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_optionalSuffixRouteDoesNotFallThroughToNoMatch() {
        RouteUtil routeUtil =
                routeUtil(Map.of("orders/:id.v2?", view("orders/:id.v2?", new String[] {"ADMIN"}, Map.of())));

        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context("/orders/x.v2"), identity("USER")));
        assertEquals(
                AuthorizationDecision.ALLOW, routeUtil.checkRouteAccess(context("/orders/x.v2"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_canonicalizesMatrixParametersBeforeMatching() {
        RouteUtil routeUtil = routeUtil(Map.of("admin", view("admin", new String[] {"ADMIN"}, Map.of())));

        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context("/admin;a=b"), identity("USER")));
        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context("/admin%3Ba=b"), identity("USER")));
        assertEquals(AuthorizationDecision.ALLOW, routeUtil.checkRouteAccess(context("/admin;a=b"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_preservesEncodedSlashInsideReactRouterParameter() {
        RouteUtil routeUtil = routeUtil(Map.of("items/:id", view("items/:id", new String[] {"ADMIN"}, Map.of())));

        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context("/items/a%2Fb"), identity("USER")));
        assertEquals(
                AuthorizationDecision.ALLOW, routeUtil.checkRouteAccess(context("/items/a%2Fb"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_equalScoreMatchesMustAllPermit() {
        Map<String, AvailableViewInfo> routes = new LinkedHashMap<>();
        routes.put("items/:id", view("items/:id", new String[0], Map.of(":id", RouteParamType.REQUIRED)));
        routes.put(
                "items/:slug", view("items/:slug", new String[] {"ADMIN"}, Map.of(":slug", RouteParamType.REQUIRED)));
        RouteUtil routeUtil = routeUtil(routes);

        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context("/items/42"), identity("USER")));
        assertEquals(AuthorizationDecision.ALLOW, routeUtil.checkRouteAccess(context("/items/42"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_compiledLayoutSecurityAppliesToChild() {
        AvailableViewInfo child = view(":id", new String[0], Map.of(":id", RouteParamType.REQUIRED));
        AvailableViewInfo layout = view("admin", new String[] {"ADMIN"}, Map.of(), List.of(child));
        RouteUtil routeUtil = routeUtil(List.of(layout));

        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context("/admin/42"), identity("USER")));
        assertEquals(AuthorizationDecision.ALLOW, routeUtil.checkRouteAccess(context("/admin/42"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_incompleteSnapshotDeniesEveryPath() {
        RouteUtil routeUtil = new RouteUtil(RouteSnapshotCompiler.incompleteSnapshot());

        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context("/known"), identity("ADMIN")));
        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context("/unknown"), identity("USER")));
    }

    @Test
    void checkRouteAccess_completeSnapshotsReturnNoMatchForUnownedPaths() {
        RouteUtil empty = routeUtil(Map.of());
        RouteUtil nonEmpty = routeUtil(Map.of("admin", view("admin", new String[] {"ADMIN"}, Map.of())));

        assertEquals(AuthorizationDecision.NO_MATCH, empty.checkRouteAccess(context("/custom"), identity("USER")));
        assertEquals(AuthorizationDecision.NO_MATCH, nonEmpty.checkRouteAccess(context("/custom"), identity("USER")));
    }

    @Test
    void checkRouteAccess_anonymousIdentityDeniedForLoginRequiredRoute() {
        RouteUtil routeUtil = routeUtil(Map.of("profile", view("profile", new String[0], Map.of())));

        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context("/profile"), anonymousIdentity()));
    }

    @Test
    void checkRouteAccess_anonymousIdentityAllowedForPublicRoute() {
        RouteUtil routeUtil = routeUtil(Map.of("public", view("public", new String[0], false, Map.of(), List.of())));

        assertEquals(AuthorizationDecision.ALLOW, routeUtil.checkRouteAccess(context("/public"), anonymousIdentity()));
    }

    @Test
    void checkRouteAccess_normalizedPathFailureIsDenied() {
        RouteUtil routeUtil = routeUtil(Map.of("admin", view("admin", new String[] {"ADMIN"}, Map.of())));
        RoutingContext context = mock(RoutingContext.class);
        when(context.normalizedPath()).thenThrow(new IllegalStateException("normalization failed"));

        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context, identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_nullIdentityIsDeniedBeforeRouteMatching() {
        RouteUtil routeUtil = routeUtil(Map.of("admin", view("admin", new String[] {"ADMIN"}, Map.of())));
        RoutingContext context = mock(RoutingContext.class);

        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context, null));
        verifyNoInteractions(context);
    }

    private static RouteUtil routeUtil(Map<String, AvailableViewInfo> routes) {
        return new RouteUtil(RouteSnapshotCompiler.compileRoutes(new LinkedHashMap<>(routes)));
    }

    private static RouteUtil routeUtil(List<AvailableViewInfo> routeTree) {
        return new RouteUtil(RouteSnapshotCompiler.compileTree(routeTree));
    }

    private static RoutingContext context(String path) {
        RoutingContext context = mock(RoutingContext.class);
        when(context.normalizedPath()).thenReturn(path);
        return context;
    }

    private static SecurityIdentity identity(String... roles) {
        SecurityIdentity identity = mock(SecurityIdentity.class);
        Set<String> roleSet = Set.of(roles);
        when(identity.isAnonymous()).thenReturn(false);
        when(identity.hasRole(anyString())).thenAnswer(invocation -> roleSet.contains(invocation.getArgument(0)));
        return identity;
    }

    private static SecurityIdentity anonymousIdentity() {
        SecurityIdentity identity = mock(SecurityIdentity.class);
        when(identity.isAnonymous()).thenReturn(true);
        return identity;
    }

    private static AvailableViewInfo view(
            String route, String[] rolesAllowed, Map<String, RouteParamType> routeParameters) {
        return view(route, rolesAllowed, true, routeParameters, List.of());
    }

    private static AvailableViewInfo view(
            String route,
            String[] rolesAllowed,
            Map<String, RouteParamType> routeParameters,
            List<AvailableViewInfo> children) {
        return view(route, rolesAllowed, true, routeParameters, children);
    }

    private static AvailableViewInfo view(
            String route,
            String[] rolesAllowed,
            boolean loginRequired,
            Map<String, RouteParamType> routeParameters,
            List<AvailableViewInfo> children) {
        return new AvailableViewInfo(
                route, rolesAllowed, loginRequired, route, false, true, null, children, routeParameters, false, null);
    }
}
