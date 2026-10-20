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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.function.DeploymentConfiguration;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.Router;
import com.vaadin.flow.router.internal.NavigationRouteTarget;
import com.vaadin.flow.router.internal.RouteTarget;
import com.vaadin.flow.server.RouteRegistry;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinContext;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.auth.NavigationAccessControl;
import com.vaadin.flow.server.startup.ApplicationConfiguration;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.mcollovati.quarkus.hilla.QuarkusEndpointConfiguration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

class HillaRouteHierarchyTest {

    @TempDir
    Path frontend;

    private VaadinService service;
    private RouteRegistry registry;

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(frontend.resolve("generated"));
        ApplicationConfiguration configuration = mock(ApplicationConfiguration.class);
        when(configuration.getFrontendFolder()).thenReturn(frontend.toFile());
        VaadinContext context = mock(VaadinContext.class);
        when(context.getAttribute(eq(ApplicationConfiguration.class), any())).thenReturn(configuration);
        service = mock(VaadinService.class);
        when(service.getContext()).thenReturn(context);
        when(service.getDeploymentConfiguration()).thenReturn(mock(DeploymentConfiguration.class));
        Router router = mock(Router.class);
        registry = mock(RouteRegistry.class);
        when(router.getRegistry()).thenReturn(registry);
        when(service.getRouter()).thenReturn(router);
    }

    @Test
    void routeUtil_rootLayoutLoginRequiredSurvivesPublicIndex() throws Exception {
        manifest("""
                [{"route":"", "loginRequired":true, "children":[
                  {"route":""}, {"route":"child"}
                ]}]
                """);
        RouteUtil routes = new RouteUtil(service);

        assertFalse(routes.isRouteAllowed(request("/child"), identity(true)));
        assertFalse(routes.isRouteAllowed(request("/"), identity(true)));
        assertTrue(routes.isRouteAllowed(request("/child"), identity(false)));
    }

    @Test
    void routeUtil_namedLayoutRolesSurvivePublicIndex() throws Exception {
        hierarchy();
        RouteUtil routes = new RouteUtil(service);

        assertFalse(routes.isRouteAllowed(request("/hierarchy/child"), identity(false, "USER")));
        assertFalse(routes.isRouteAllowed(request("/hierarchy"), identity(false, "USER")));
        assertTrue(routes.isRouteAllowed(request("/hierarchy/child"), identity(false, "ADMIN")));
        assertTrue(routes.isRouteAllowed(request("/hierarchy"), identity(false, "ADMIN")));
    }

    @Test
    void routeUtil_pathlessLayoutsRequireEveryAncestorsRoles() throws Exception {
        manifest("""
                [{"route":"hierarchy", "rolesAllowed":["ADMIN", "MANAGER"], "children":[
                  {"rolesAllowed":["EDITOR"], "children":[{"route":"child"}]}
                ]}]
                """);
        RouteUtil routes = new RouteUtil(service);

        assertFalse(routes.isRouteAllowed(request("/hierarchy/child"), identity(false, "EDITOR")));
        assertFalse(routes.isRouteAllowed(request("/hierarchy/child"), identity(false, "ADMIN")));
        assertTrue(routes.isRouteAllowed(request("/hierarchy/child"), identity(false, "MANAGER", "EDITOR")));
    }

    @Test
    void routeUtil_childRestrictionDoesNotRestrictSibling() throws Exception {
        manifest("""
                [{"route":"hierarchy", "children":[
                  {"route":"private", "rolesAllowed":["ADMIN"]}, {"route":"public"}
                ]}]
                """);
        RouteUtil routes = new RouteUtil(service);

        assertFalse(routes.isRouteAllowed(request("/hierarchy/private"), identity(false, "USER")));
        assertTrue(routes.isRouteAllowed(request("/hierarchy/public"), identity(true)));
    }

    @Test
    void policy_layoutLoginRequiredDeniesAnonymousChildWithoutFlowRoute() throws Exception {
        manifest("""
                [{"route":"hierarchy", "loginRequired":true, "children":[{"route":"child"}]}]
                """);
        HillaSecurityPolicy policy = policy();

        assertFalse(permitted(policy, "/hierarchy/child", identity(true)));
        assertTrue(permitted(policy, "/hierarchy/child", identity(false)));
    }

    @Test
    void policy_layoutRolesLeftToClientForAuthenticatedUser() throws Exception {
        hierarchy();
        HillaSecurityPolicy policy = policy();

        // Roles are checked by the client router; the HTTP layer only requires a login.
        assertTrue(permitted(policy, "/hierarchy/child", identity(false, "USER")));
        assertTrue(permitted(policy, "/hierarchy/child", identity(false, "ADMIN")));
        assertFalse(permitted(policy, "/hierarchy/child", identity(true)));
    }

    @Test
    void policy_publicChildAndUnknownPathRemainPublic() throws Exception {
        manifest("""
                [{"route":"hierarchy", "children":[{"route":"child"}]}]
                """);
        HillaSecurityPolicy policy = policy();

        assertTrue(permitted(policy, "/hierarchy/child", identity(true)));
        assertTrue(permitted(policy, "/unrelated", identity(true)));
    }

    @Test
    void policy_disabledNavigationAccessControlKeepsFlowRoutePublic() throws Exception {
        hierarchy();
        NavigationRouteTarget target = mock(NavigationRouteTarget.class);
        RouteTarget routeTarget = mock(RouteTarget.class);
        when(target.getRouteTarget()).thenReturn(routeTarget);
        when(target.getRouteParameters()).thenReturn(RouteParameters.empty());
        when(routeTarget.getTarget()).thenAnswer(ignored -> Div.class);
        when(registry.getNavigationRouteTarget("hierarchy/child")).thenReturn(target);

        assertTrue(permitted(policy(), "/hierarchy/child", identity(true)));
    }

    @Test
    void policy_deniedChildDoesNotFallBackToPublicWildcard() throws Exception {
        manifest("""
                [{"route":"hierarchy", "loginRequired":true, "children":[{"route":"child"}]},
                 {"route":"*"}]
                """);
        HillaSecurityPolicy policy = policy();

        assertFalse(permitted(policy, "/hierarchy/child", identity(true)));
        assertTrue(permitted(policy, "/another", identity(true)));
    }

    @Test
    void policy_wildcardRouteDoesNotCaptureNonPageRequests() throws Exception {
        manifest("""
                [{"route":"", "loginRequired":true, "children":[{"route":"*"}]}]
                """);
        HillaSecurityPolicy policy = policy();

        // A browser page load is checked against the protected wildcard route.
        assertFalse(permitted(policy, "/anything", identity(true)));
        // REST or health requests on the same path are not Hilla pages.
        assertTrue(permitted(policy, request("/api/hello", "application/json"), identity(true)));
        assertTrue(permitted(policy, request("/q/health", "*/*"), identity(true)));
    }

    private void hierarchy() throws Exception {
        manifest("""
                [{"route":"hierarchy", "loginRequired":true, "rolesAllowed":["ADMIN"], "children":[
                  {"route":""}, {"route":"child"}
                ]}]
                """);
    }

    private void manifest(String json) throws Exception {
        Files.writeString(frontend.resolve("generated/file-routes.json"), json);
    }

    private HillaSecurityPolicy policy() {
        QuarkusEndpointConfiguration configuration = mock(QuarkusEndpointConfiguration.class);
        when(configuration.getNormalizedEndpointPrefix()).thenReturn("/connect");
        HillaSecurityPolicy policy =
                new HillaSecurityPolicy(mock(NavigationAccessControl.class), configuration, mock(EndpointUtil.class));
        try (var ignored = mockConstruction(WebIconsRequestMatcher.class)) {
            policy.onVaadinServiceInit(new ServiceInitEvent(service));
        }
        return policy;
    }

    private static boolean permitted(HillaSecurityPolicy policy, String path, SecurityIdentity identity) {
        return permitted(policy, request(path), identity);
    }

    private static boolean permitted(HillaSecurityPolicy policy, RoutingContext request, SecurityIdentity identity) {
        return policy.checkPermission(request, Uni.createFrom().item(identity), null)
                .await()
                .indefinitely()
                .isPermitted();
    }

    private static SecurityIdentity identity(boolean anonymous, String... roles) {
        SecurityIdentity identity = mock(SecurityIdentity.class);
        when(identity.isAnonymous()).thenReturn(anonymous);
        when(identity.hasRole(anyString()))
                .thenAnswer(call -> Arrays.asList(roles).contains(call.getArgument(0)));
        return identity;
    }

    private static RoutingContext request(String path) {
        return request(path, "text/html,application/xhtml+xml,*/*;q=0.8");
    }

    private static RoutingContext request(String path, String accept) {
        RoutingContext context = mock(RoutingContext.class);
        HttpServerRequest request = mock(HttpServerRequest.class);
        when(context.request()).thenReturn(request);
        when(context.normalizedPath()).thenReturn(path);
        when(request.path()).thenReturn(path);
        when(request.method()).thenReturn(HttpMethod.GET);
        when(request.getHeader(HttpHeaders.ACCEPT)).thenReturn(accept);
        when(request.params()).thenReturn(MultiMap.caseInsensitiveMultiMap());
        return context;
    }
}
