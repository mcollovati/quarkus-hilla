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

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.function.DeploymentConfiguration;
import com.vaadin.flow.internal.CurrentInstance;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

class HillaSecurityPolicyTest {

    @TempDir
    Path frontend;

    private HillaSecurityPolicy policy;
    private RouteRegistry registry;
    private WebIconsRequestMatcher webIcons;

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(frontend.resolve("generated"));
        Files.writeString(frontend.resolve("generated/file-routes.json"), """
                [{"route":"protected", "loginRequired":true},
                 {"route":"public"},
                 {"route":"admin", "rolesAllowed":["ADMIN"]},
                 {"route":"protected-wildcard/*", "loginRequired":true},
                 {"route":"fallback/*"},
                 {"route":"fallback/protected", "loginRequired":true}]
                """);
        ApplicationConfiguration configuration = mock(ApplicationConfiguration.class);
        when(configuration.getFrontendFolder()).thenReturn(frontend.toFile());
        VaadinContext context = mock(VaadinContext.class);
        when(context.getAttribute(eq(ApplicationConfiguration.class), any())).thenReturn(configuration);
        VaadinService service = mock(VaadinService.class);
        when(service.getContext()).thenReturn(context);
        when(service.getDeploymentConfiguration()).thenReturn(mock(DeploymentConfiguration.class));
        Router router = mock(Router.class);
        registry = mock(RouteRegistry.class);
        when(router.getRegistry()).thenReturn(registry);
        when(service.getRouter()).thenReturn(router);
        QuarkusEndpointConfiguration endpoints = mock(QuarkusEndpointConfiguration.class);
        when(endpoints.getNormalizedEndpointPrefix()).thenReturn("/connect");
        policy = new HillaSecurityPolicy(mock(NavigationAccessControl.class), endpoints, mock(EndpointUtil.class));
        try (var ignored = mockConstruction(WebIconsRequestMatcher.class)) {
            policy.onVaadinServiceInit(new ServiceInitEvent(service));
            webIcons = ignored.constructed().getFirst();
        }
    }

    @Test
    void anonymousGet_loginRequired_denied() {
        assertFalse(permitted("/protected", HttpMethod.GET, "text/html", true));
    }

    @Test
    void anonymousHead_loginRequired_denied() {
        assertFalse(permitted("/protected", HttpMethod.HEAD, "text/html", true));
    }

    @Test
    void anonymousGet_rolesAllowed_denied() {
        assertFalse(permitted("/admin", HttpMethod.GET, "text/html", true));
    }

    @Test
    void authenticatedGet_missingRole_permitted() {
        assertTrue(permitted("/admin", HttpMethod.GET, "text/html", false));
    }

    @Test
    void anonymousGet_publicAndUnknownPaths_permitted() {
        assertTrue(permitted("/public", HttpMethod.GET, "text/html", true));
        assertTrue(permitted("/unknown", HttpMethod.GET, "text/html", true));
    }

    @Test
    void anonymousGet_nonHtmlProtectedRoutes_permitted() {
        for (String accept : new String[] {null, "application/json", "*/*"}) {
            assertTrue(permitted("/protected", HttpMethod.GET, accept, true));
            assertTrue(permitted("/protected-wildcard/api", HttpMethod.GET, accept, true));
        }
    }

    @Test
    void anonymousPost_htmlProtectedWildcard_permitted() {
        assertTrue(permitted("/protected-wildcard/api", HttpMethod.POST, "text/html", true));
    }

    @Test
    void anonymousGet_htmlProtectedWildcard_denied() {
        assertFalse(permitted("/protected-wildcard/page", HttpMethod.GET, "text/html", true));
    }

    @Test
    void anonymousGet_protectedExactRouteWithPublicWildcard_denied() {
        assertFalse(permitted("/fallback/protected", HttpMethod.GET, "text/html", true));
        assertTrue(permitted("/fallback/public", HttpMethod.GET, "text/html", true));
    }

    @Test
    void anonymousGet_mixedCaseHtmlAccept_denied() {
        assertFalse(permitted("/protected", HttpMethod.GET, "application/json, TEXT/HTML;q=0.9", true));
    }

    @Test
    void anonymousGet_customIconMatchingProtectedRoute_permitted() {
        when(webIcons.isWebIconRequest("/protected")).thenReturn(true);
        assertTrue(permitted("/protected", HttpMethod.GET, "text/html", true));
    }

    @Test
    void beforeServiceInit_unknownPath_permitted() {
        QuarkusEndpointConfiguration endpoints = mock(QuarkusEndpointConfiguration.class);
        when(endpoints.getNormalizedEndpointPrefix()).thenReturn("/connect");
        policy = new HillaSecurityPolicy(mock(NavigationAccessControl.class), endpoints, mock(EndpointUtil.class));
        assertTrue(permitted("/unknown", HttpMethod.GET, "text/html", true));
    }

    @Test
    void disabledNavigationAccessControl_matchingFlowRoute_permitted() {
        NavigationRouteTarget target = mock(NavigationRouteTarget.class);
        RouteTarget routeTarget = mock(RouteTarget.class);
        when(target.getRouteTarget()).thenReturn(routeTarget);
        when(target.getRouteParameters()).thenReturn(RouteParameters.empty());
        when(routeTarget.getTarget()).thenAnswer(ignored -> Div.class);
        when(registry.getNavigationRouteTarget("protected")).thenReturn(target);

        assertTrue(permitted("/protected", HttpMethod.GET, "text/html", true));
    }

    @Test
    void routeDiscovery_restoresCurrentService() {
        var oldInstances = CurrentInstance.getInstances();
        VaadinService previousService = mock(VaadinService.class);
        VaadinService.setCurrent(previousService);
        try {
            permitted("/protected", HttpMethod.GET, "text/html", true);
            assertSame(previousService, VaadinService.getCurrent());
        } finally {
            CurrentInstance.clearAll();
            CurrentInstance.restoreInstances(oldInstances);
        }
    }

    private boolean permitted(String path, HttpMethod method, String accept, boolean anonymous) {
        RoutingContext context = mock(RoutingContext.class);
        HttpServerRequest request = mock(HttpServerRequest.class);
        when(context.request()).thenReturn(request);
        when(context.normalizedPath()).thenReturn(path);
        when(request.path()).thenReturn(path);
        when(request.method()).thenReturn(method);
        when(request.getHeader(HttpHeaders.ACCEPT)).thenReturn(accept);
        when(request.params()).thenReturn(MultiMap.caseInsensitiveMultiMap());
        SecurityIdentity identity = mock(SecurityIdentity.class);
        when(identity.isAnonymous()).thenReturn(anonymous);
        return policy.checkPermission(context, Uni.createFrom().item(identity), null)
                .await()
                .indefinitely()
                .isPermitted();
    }
}
