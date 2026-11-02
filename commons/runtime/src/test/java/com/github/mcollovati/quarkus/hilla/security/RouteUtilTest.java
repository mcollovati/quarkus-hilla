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
import java.util.HashMap;
import java.util.Map;

import com.vaadin.flow.internal.CurrentInstance;
import com.vaadin.flow.internal.menu.MenuRegistry;
import com.vaadin.flow.server.VaadinContext;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.menu.AvailableViewInfo;
import com.vaadin.flow.server.startup.ApplicationConfiguration;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class RouteUtilTest {

    @TempDir
    Path frontend;

    private RouteUtil routeUtil;
    private ApplicationConfiguration configuration;

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(frontend.resolve("generated"));
        Files.writeString(frontend.resolve("generated/file-routes.json"), """
                [{"route":"products/:id", "loginRequired":true},
                 {"route":"optional/:id?", "loginRequired":true},
                 {"route":"protected", "loginRequired":true},
                 {"route":"café", "loginRequired":true},
                 {"route":"admin/:id", "rolesAllowed":["ADMIN"]},
                 {"route":"products/*"},
                 {"route":"public/:id"},
                 {"route":"public/*", "loginRequired":true},
                 {"route":"layout", "loginRequired":true, "rolesAllowed":["ADMIN"], "children":[
                   {"route":"public"},
                   {"route":"private", "loginRequired":true}]},
                 {"route":"public-layout", "children":[{"route":"admin", "rolesAllowed":["ADMIN"]}]}]
                """);
        configuration = mock(ApplicationConfiguration.class);
        when(configuration.getFrontendFolder()).thenReturn(frontend.toFile());
        VaadinContext context = mock(VaadinContext.class);
        when(context.getAttribute(eq(ApplicationConfiguration.class), any())).thenReturn(configuration);
        VaadinService service = mock(VaadinService.class);
        when(service.getContext()).thenReturn(context);
        routeUtil = new RouteUtil(service);
    }

    @Test
    void securedRoute_matchesClientPaths() {
        for (String path :
                new String[] {"/products/123", "/optional", "/optional/123", "/PROTECTED", "/caf%C3%A9", "/protected/"
                }) {
            assertTrue(routeUtil.isSecuredRoute(context(path)), path);
            assertFalse(routeUtil.isRouteAllowed(context(path), identity(true)), path);
            assertTrue(routeUtil.isRouteAllowed(context(path), identity(false)), path);
        }
    }

    @Test
    void routeAllowed_securedParameterDoesNotFallBackToPublicWildcard() {
        assertFalse(routeUtil.isRouteAllowed(context("/products/123"), identity(true)));
        assertTrue(routeUtil.isRouteAllowed(context("/products/123/edit"), identity(true)));
        assertFalse(routeUtil.isSecuredRoute(context("/products/123/edit")));
    }

    @Test
    void routeAllowed_usesOnlyMatchedViewsRoles() {
        assertTrue(routeUtil.isSecuredRoute(context("/admin/123")));
        assertFalse(routeUtil.isRouteAllowed(context("/admin/123"), identity(true)));
        assertFalse(routeUtil.isRouteAllowed(context("/admin/123"), identity(false)));
        assertTrue(routeUtil.isRouteAllowed(context("/admin/123"), identity(false, "ADMIN")));
        assertTrue(routeUtil.isRouteAllowed(context("/layout/public"), identity(true)));
        assertFalse(routeUtil.isSecuredRoute(context("/layout/public")));
        assertFalse(routeUtil.isRouteAllowed(context("/layout/private"), identity(true)));
        assertTrue(routeUtil.isRouteAllowed(context("/layout/private"), identity(false)));
        assertTrue(routeUtil.isSecuredRoute(context("/public-layout/admin")));
        assertFalse(routeUtil.isRouteAllowed(context("/public-layout/admin"), identity(false)));
    }

    @Test
    void routeAllowed_publicParameterizedAndUnknownPaths() {
        assertTrue(routeUtil.isRouteAllowed(context("/public/123"), identity(true)));
        assertFalse(routeUtil.isSecuredRoute(context("/public/123")));
        assertFalse(routeUtil.isRouteAllowed(context("/unknown"), identity(true)));
        assertFalse(routeUtil.isSecuredRoute(context("/unknown")));
    }

    @Test
    void routeLookup_equalScoresUseDocumentOrder() throws Exception {
        Files.writeString(frontend.resolve("generated/file-routes.json"), """
                [{"route":"users/:id", "loginRequired":true},
                 {"route":"users/:name"}]
                """);

        assertTrue(routeUtil.isSecuredRoute(context("/users/42")));
        assertFalse(routeUtil.isRouteAllowed(context("/users/42"), identity(true)));
    }

    @Test
    void routeLookup_publicFirstTieDoesNotInheritSecuredSiblingRequirements() throws Exception {
        Files.writeString(frontend.resolve("generated/file-routes.json"), """
                [{"route":"users/:name"},
                 {"route":"users/:id", "loginRequired":true}]
                """);

        assertFalse(routeUtil.isSecuredRoute(context("/users/42")));
        assertTrue(routeUtil.isRouteAllowed(context("/users/42"), identity(true)));
    }

    @Test
    void routeLookup_nestedTiesAndIndexUseOwnRequirements() throws Exception {
        Files.writeString(frontend.resolve("generated/file-routes.json"), """
                [{"route":"users", "loginRequired":true, "children":[
                  {"route":""}, {"route":":name"}, {"route":":id", "loginRequired":true}]}]
                """);

        assertFalse(routeUtil.isSecuredRoute(context("/users")));
        assertTrue(routeUtil.isRouteAllowed(context("/users"), identity(true)));
        assertFalse(routeUtil.isSecuredRoute(context("/users/42")));
        assertTrue(routeUtil.isRouteAllowed(context("/users/42"), identity(true)));
    }

    @Test
    void routeLookup_unknownOrderTiesFailClosed() {
        Map<String, AvailableViewInfo> routes = new HashMap<>();
        routes.put(
                "/users/:id",
                new AvailableViewInfo(null, null, true, "users/:id", false, false, null, null, null, false, null));
        routes.put(
                "/users/:name",
                new AvailableViewInfo(null, null, false, "users/:name", false, false, null, null, null, false, null));
        routes.put(
                "/admin/:id",
                new AvailableViewInfo(
                        null, new String[] {"ADMIN"}, false, "admin/:id", false, false, null, null, null, false, null));
        routes.put(
                "/admin/:name",
                new AvailableViewInfo(
                        null,
                        new String[] {"MANAGER"},
                        false,
                        "admin/:name",
                        false,
                        false,
                        null,
                        null,
                        null,
                        false,
                        null));
        try (var menuRegistry = mockStatic(MenuRegistry.class)) {
            menuRegistry
                    .when(() -> MenuRegistry.getViewsJsonAsResource(configuration))
                    .thenReturn(null);
            menuRegistry
                    .when(() -> MenuRegistry.collectClientMenuItems(false, configuration, null))
                    .thenReturn(routes);

            assertTrue(routeUtil.isSecuredRoute(context("/users/42")));
            assertFalse(routeUtil.isRouteAllowed(context("/users/42"), identity(true)));
            assertTrue(routeUtil.isRouteAllowed(context("/users/42"), identity(false)));
            assertTrue(routeUtil.isSecuredRoute(context("/admin/42")));
            assertFalse(routeUtil.isRouteAllowed(context("/admin/42"), identity(false, "ADMIN")));
            assertFalse(routeUtil.isRouteAllowed(context("/admin/42"), identity(false, "MANAGER")));
            assertTrue(routeUtil.isRouteAllowed(context("/admin/42"), identity(false, "ADMIN", "MANAGER")));
        }
    }

    @Test
    void routeLookup_preservesRepeatedSlashes() {
        RoutingContext context = context("/public//123");
        when(context.normalizedPath()).thenReturn("/public/123");

        assertTrue(routeUtil.isSecuredRoute(context));
        assertFalse(routeUtil.isRouteAllowed(context, identity(true)));
        assertTrue(routeUtil.isRouteAllowed(context("/public/123"), identity(true)));
    }

    @Test
    void routeLookup_dotSegmentsCannotBypassNormalizedSecuredRoute() throws Exception {
        Files.writeString(frontend.resolve("generated/file-routes.json"), """
                [{"route":"public/*"}, {"route":"protected", "loginRequired":true}]
                """);
        RoutingContext context = context("/public/../protected", "/protected");

        assertTrue(routeUtil.isSecuredRoute(context));
        assertFalse(routeUtil.isRouteAllowed(context, identity(true)));
        assertTrue(routeUtil.isRouteAllowed(context, identity(false)));
    }

    @Test
    void routeLookup_encodedDotSegmentsCannotBypassNormalizedSecuredRoute() throws Exception {
        Files.writeString(frontend.resolve("generated/file-routes.json"), """
                [{"route":"public/*"}, {"route":"protected", "loginRequired":true}]
                """);
        for (String path :
                new String[] {"/public/%2e%2e/protected", "/public/.%2E/protected", "/public/%2E./protected"}) {
            RoutingContext context = context(path, "/protected");

            assertTrue(routeUtil.isSecuredRoute(context), path);
            assertFalse(routeUtil.isRouteAllowed(context, identity(true)), path);
            assertTrue(routeUtil.isRouteAllowed(context, identity(false)), path);
        }
    }

    @Test
    void routeLookup_repeatedSlashesCannotBypassNormalizedSecuredRoute() throws Exception {
        Files.writeString(frontend.resolve("generated/file-routes.json"), """
                [{"route":"public/:id", "loginRequired":true}, {"route":"public/*"}]
                """);
        RoutingContext context = context("/public//123", "/public/123");

        assertTrue(routeUtil.isSecuredRoute(context));
        assertFalse(routeUtil.isRouteAllowed(context, identity(true)));
        assertTrue(routeUtil.isRouteAllowed(context, identity(false)));
    }

    @Test
    void routeLookup_normalizedRoleRequirementsCannotBeBypassed() {
        RoutingContext context = context("/products/../admin/42", "/admin/42");

        assertTrue(routeUtil.isSecuredRoute(context));
        assertFalse(routeUtil.isRouteAllowed(context, identity(false)));
        assertTrue(routeUtil.isRouteAllowed(context, identity(false, "ADMIN")));
    }

    @Test
    void routeLookup_unmatchedRepresentationDoesNotDenyMatchedPublicView() {
        RoutingContext normalizedOnly = context("/missing/../products/42/edit", "/products/42/edit");
        RoutingContext rawOnly = context("/products/../missing", "/missing");

        assertFalse(routeUtil.isSecuredRoute(normalizedOnly));
        assertTrue(routeUtil.isRouteAllowed(normalizedOnly, identity(true)));
        assertFalse(routeUtil.isSecuredRoute(rawOnly));
        assertTrue(routeUtil.isRouteAllowed(rawOnly, identity(true)));
    }

    @Test
    void routeDiscovery_restoresCurrentServiceAndCachesMatcher() throws Exception {
        var oldInstances = CurrentInstance.getInstances();
        VaadinService previousService = mock(VaadinService.class);
        VaadinService.setCurrent(previousService);
        try {
            assertTrue(routeUtil.isSecuredRoute(context("/products/123")));
            assertSame(previousService, VaadinService.getCurrent());
            Files.writeString(frontend.resolve("generated/file-routes.json"), "[]");
            assertTrue(routeUtil.isSecuredRoute(context("/products/456")));
            assertFalse(routeUtil.isRouteAllowed(context("/products/456"), identity(true)));
            assertSame(previousService, VaadinService.getCurrent());
        } finally {
            CurrentInstance.clearAll();
            CurrentInstance.restoreInstances(oldInstances);
        }
    }

    private static RoutingContext context(String path) {
        return context(path, path);
    }

    private static RoutingContext context(String path, String normalizedPath) {
        RoutingContext context = mock(RoutingContext.class);
        HttpServerRequest request = mock(HttpServerRequest.class);
        when(context.request()).thenReturn(request);
        when(request.path()).thenReturn(path);
        when(context.normalizedPath()).thenReturn(normalizedPath);
        return context;
    }

    private static SecurityIdentity identity(boolean anonymous, String... roles) {
        SecurityIdentity identity = mock(SecurityIdentity.class);
        when(identity.isAnonymous()).thenReturn(anonymous);
        for (String role : roles) {
            when(identity.hasRole(role)).thenReturn(true);
        }
        return identity;
    }
}
