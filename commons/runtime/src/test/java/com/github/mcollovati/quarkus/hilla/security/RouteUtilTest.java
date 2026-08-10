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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.menu.AvailableViewInfo;
import com.vaadin.flow.server.menu.RouteParamType;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RouteUtilTest {

    @Test
    void readRouteTree_deserializesGeneratedFileRouteFormat() throws Exception {
        String json = """
                [{
                  "title": "Admin layout",
                  "rolesAllowed": ["ADMIN"],
                  "loginRequired": true,
                  "route": "admin",
                  "children": [{
                    "title": "User",
                    "rolesAllowed": [],
                    "loginRequired": true,
                    "route": ":id",
                    "params": {":id": "req"}
                  }]
                }]
                """;

        List<AvailableViewInfo> routes =
                RouteUtil.readRouteTree(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        RouteUtil routeUtil = routeUtil(routes);

        assertEquals(RouteUtil.RouteAccess.DENY, routeUtil.checkRouteAccess(context("/admin/42"), identity("USER")));
        assertEquals(RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/admin/42"), identity("ADMIN")));
        assertEquals(
                RouteParamType.REQUIRED,
                routes.getFirst().children().getFirst().routeParameters().get(":id"));
    }

    @Test
    void checkRouteAccess_requiredParameterEnforcesRouteRoles() {
        RouteUtil routeUtil = routeUtil(
                Map.of("users/:id", view("users/:id", new String[] {"ADMIN"}, Map.of(":id", RouteParamType.REQUIRED))));

        assertEquals(RouteUtil.RouteAccess.DENY, routeUtil.checkRouteAccess(context("/users/42"), identity("USER")));
        assertEquals(RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/users/42"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_optionalParameterMatchesWithAndWithoutValue() {
        RouteUtil routeUtil = routeUtil(Map.of(
                "reports/:filter?",
                view("reports/:filter?", new String[] {"ADMIN"}, Map.of(":filter?", RouteParamType.OPTIONAL))));

        assertEquals(RouteUtil.RouteAccess.DENY, routeUtil.checkRouteAccess(context("/reports"), identity("USER")));
        assertEquals(
                RouteUtil.RouteAccess.DENY, routeUtil.checkRouteAccess(context("/reports/open"), identity("USER")));
        assertEquals(
                RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/reports/open"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_optionalStaticSegmentMatchesWithAndWithoutSegment() {
        RouteUtil routeUtil = routeUtil(Map.of("projects?", view("projects?", new String[] {"ADMIN"}, Map.of())));

        assertEquals(RouteUtil.RouteAccess.DENY, routeUtil.checkRouteAccess(context("/"), identity("USER")));
        assertEquals(RouteUtil.RouteAccess.DENY, routeUtil.checkRouteAccess(context("/projects"), identity("USER")));
        assertEquals(RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/"), identity("ADMIN")));
        assertEquals(RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/projects"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_preservesEncodedSlashInsideParameter() {
        RouteUtil routeUtil = routeUtil(
                Map.of("items/:id", view("items/:id", new String[] {"ADMIN"}, Map.of(":id", RouteParamType.REQUIRED))));

        assertEquals(RouteUtil.RouteAccess.DENY, routeUtil.checkRouteAccess(context("/items/a%2Fb"), identity("USER")));
        assertEquals(
                RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/items/a%2Fb"), identity("ADMIN")));
        assertEquals(
                RouteUtil.RouteAccess.NO_MATCH, routeUtil.checkRouteAccess(context("/items/a/b"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_staticRouteWinsOverParameterRoute() {
        Map<String, AvailableViewInfo> routes = new LinkedHashMap<>();
        routes.put("users/:id", view("users/:id", new String[] {"ADMIN"}, Map.of(":id", RouteParamType.REQUIRED)));
        routes.put("users/new", view("users/new", new String[] {"USER"}, Map.of()));
        RouteUtil routeUtil = routeUtil(routes);

        assertEquals(RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/users/new"), identity("USER")));
    }

    @Test
    void checkRouteAccess_deniedSpecificRouteDoesNotFallBackToPublicParameterRoute() {
        Map<String, AvailableViewInfo> routes = new LinkedHashMap<>();
        routes.put("users/:id", view("users/:id", new String[0], Map.of(":id", RouteParamType.REQUIRED)));
        routes.put("users/new", view("users/new", new String[] {"ADMIN"}, Map.of()));
        RouteUtil routeUtil = routeUtil(routes);

        assertEquals(RouteUtil.RouteAccess.DENY, routeUtil.checkRouteAccess(context("/users/new"), identity("USER")));
        assertEquals(RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/users/new"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_equalScoreMatchesMustAllPermit() {
        Map<String, AvailableViewInfo> routes = new LinkedHashMap<>();
        routes.put("items/:id", view("items/:id", new String[0], Map.of(":id", RouteParamType.REQUIRED)));
        routes.put(
                "items/:slug", view("items/:slug", new String[] {"ADMIN"}, Map.of(":slug", RouteParamType.REQUIRED)));
        RouteUtil routeUtil = routeUtil(routes);

        assertEquals(RouteUtil.RouteAccess.DENY, routeUtil.checkRouteAccess(context("/items/42"), identity("USER")));
        assertEquals(RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/items/42"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_parameterSuffixMustMatchBeforeOutrankingWildcard() {
        Map<String, AvailableViewInfo> routes = new LinkedHashMap<>();
        routes.put("files/:id.json", view("files/:id.json", new String[0], Map.of()));
        routes.put("files/*", view("files/*", new String[] {"ADMIN"}, Map.of("*", RouteParamType.WILDCARD)));
        RouteUtil routeUtil = routeUtil(routes);

        assertEquals(RouteUtil.RouteAccess.DENY, routeUtil.checkRouteAccess(context("/files/foo"), identity("USER")));
        assertEquals(RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/files/foo"), identity("ADMIN")));
        assertEquals(
                RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/files/foo.json"), identity("USER")));
    }

    @Test
    void checkRouteAccess_parameterSuffixKeepsEqualStaticMatchRestrictive() {
        Map<String, AvailableViewInfo> routes = new LinkedHashMap<>();
        routes.put("files/:id.json", view("files/:id.json", new String[] {"ADMIN"}, Map.of()));
        routes.put("files/public.json", view("files/public.json", new String[0], Map.of()));
        RouteUtil routeUtil = routeUtil(routes);

        assertEquals(
                RouteUtil.RouteAccess.DENY,
                routeUtil.checkRouteAccess(context("/files/public.json"), identity("USER")));
        assertEquals(
                RouteUtil.RouteAccess.ALLOW,
                routeUtil.checkRouteAccess(context("/files/public.json"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_optionalParameterWithSuffixKeepsSegmentRequired() {
        RouteUtil routeUtil =
                routeUtil(Map.of("files/:id?.json", view("files/:id?.json", new String[] {"ADMIN"}, Map.of())));

        assertEquals(RouteUtil.RouteAccess.NO_MATCH, routeUtil.checkRouteAccess(context("/files"), identity("ADMIN")));
        assertEquals(RouteUtil.RouteAccess.DENY, routeUtil.checkRouteAccess(context("/files/.json"), identity("USER")));
        assertEquals(
                RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/files/foo.json"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_layoutSecurityAppliesToIndexAtSamePath() {
        AvailableViewInfo index = view("", new String[0], Map.of());
        AvailableViewInfo layout = view("", new String[] {"ADMIN"}, Map.of(), List.of(index));
        RouteUtil routeUtil = routeUtil(List.of(layout));

        assertEquals(RouteUtil.RouteAccess.DENY, routeUtil.checkRouteAccess(context("/"), identity("USER")));
        assertEquals(RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_layoutSecurityAppliesToParameterizedChild() {
        AvailableViewInfo child = view(":id", new String[0], Map.of(":id", RouteParamType.REQUIRED));
        AvailableViewInfo layout = view("admin", new String[] {"ADMIN"}, Map.of(), List.of(child));
        RouteUtil routeUtil = routeUtil(List.of(layout));

        assertEquals(RouteUtil.RouteAccess.DENY, routeUtil.checkRouteAccess(context("/admin/42"), identity("USER")));
        assertEquals(RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/admin/42"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_layoutSecurityAppliesToNestedAbsoluteChild() {
        AvailableViewInfo child = view("/admin/users", new String[0], Map.of());
        AvailableViewInfo layout = view("admin", new String[] {"ADMIN"}, Map.of(), List.of(child));
        RouteUtil routeUtil = routeUtil(List.of(layout));

        assertEquals(RouteUtil.RouteAccess.DENY, routeUtil.checkRouteAccess(context("/admin/users"), identity("USER")));
        assertEquals(
                RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/admin/users"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_nestedAbsoluteChildUsesReactPrefixSlicing() {
        AvailableViewInfo child = view("/administrator/users", new String[0], Map.of());
        AvailableViewInfo layout = view("admin", new String[] {"ADMIN"}, Map.of(), List.of(child));
        RouteUtil routeUtil = routeUtil(List.of(layout));

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
    void checkRouteAccess_invalidAbsoluteChildDoesNotDiscardOtherRoutes() {
        AvailableViewInfo invalidChild = view("/users", new String[0], Map.of());
        AvailableViewInfo layout = view("admin", new String[] {"ADMIN"}, Map.of(), List.of(invalidChild));
        AvailableViewInfo publicView = view("public", new String[0], Map.of());
        RouteUtil routeUtil = routeUtil(List.of(layout, publicView));

        assertEquals(RouteUtil.RouteAccess.NO_MATCH, routeUtil.checkRouteAccess(context("/users"), identity("ADMIN")));
        assertEquals(RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/public"), identity("USER")));
    }

    @Test
    void checkRouteAccess_wildcardEnforcesRouteRoles() {
        RouteUtil routeUtil = routeUtil(
                Map.of("files/*", view("files/*", new String[] {"ADMIN"}, Map.of("*", RouteParamType.WILDCARD))));

        assertEquals(RouteUtil.RouteAccess.DENY, routeUtil.checkRouteAccess(context("/files/a/b"), identity("USER")));
        assertEquals(RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/files/a/b"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_wildcardBeforeOptionalParameterMatchesOptionalExpansion() {
        RouteUtil routeUtil = routeUtil(Map.of(
                "files/*/:id?",
                view(
                        "files/*/:id?",
                        new String[] {"ADMIN"},
                        Map.of("*", RouteParamType.WILDCARD, ":id?", RouteParamType.OPTIONAL))));

        assertEquals(RouteUtil.RouteAccess.DENY, routeUtil.checkRouteAccess(context("/files/a/b"), identity("USER")));
        assertEquals(RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/files/a/b"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_nonTerminalWildcardUsesLiteralBranch() {
        RouteUtil routeUtil = routeUtil(Map.of(
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

    @Test
    void discovery_developmentFallbackUsesBackoffThenPublishesCompleteTree() {
        AtomicInteger discoveries = new AtomicInteger();
        AtomicLong nanoTime = new AtomicLong();
        AvailableViewInfo admin = view("admin", new String[] {"ADMIN"}, Map.of());
        RouteUtil routeUtil = new RouteUtil(
                mock(VaadinService.class),
                () -> discoveries.incrementAndGet() == 1
                        ? RouteUtil.DiscoveryResult.fallback(Map.of("admin", admin))
                        : RouteUtil.DiscoveryResult.complete(List.of(admin)),
                nanoTime::get);

        assertEquals(RouteUtil.RouteAccess.DENY, routeUtil.checkRouteAccess(context("/admin"), identity("ADMIN")));
        assertEquals(RouteUtil.RouteAccess.DENY, routeUtil.checkRouteAccess(context("/admin"), identity("ADMIN")));
        assertEquals(1, discoveries.get());

        nanoTime.set(Duration.ofSeconds(1).toNanos());
        assertEquals(RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/admin"), identity("ADMIN")));
        assertEquals(2, discoveries.get());
    }

    @Test
    void discovery_productionFallbackIsNotRepeatedAndDeniesKnownRoutes() {
        AtomicInteger discoveries = new AtomicInteger();
        AvailableViewInfo publicView = view("public", new String[0], Map.of());
        RouteUtil routeUtil = new RouteUtil(
                mock(VaadinService.class),
                false,
                () -> {
                    discoveries.incrementAndGet();
                    return RouteUtil.DiscoveryResult.fallback(Map.of("public", publicView));
                },
                System::nanoTime);

        assertEquals(RouteUtil.RouteAccess.DENY, routeUtil.checkRouteAccess(context("/public"), identity("USER")));
        assertEquals(RouteUtil.RouteAccess.NO_MATCH, routeUtil.checkRouteAccess(context("/unknown"), identity("USER")));
        assertEquals(RouteUtil.RouteAccess.DENY, routeUtil.checkRouteAccess(context("/public"), identity("USER")));
        assertEquals(1, discoveries.get());
    }

    @Test
    void discovery_failureIsRetriedAfterBackoff() {
        AtomicInteger discoveries = new AtomicInteger();
        AtomicLong nanoTime = new AtomicLong();
        AvailableViewInfo admin = view("admin", new String[] {"ADMIN"}, Map.of());
        RouteUtil routeUtil = new RouteUtil(
                mock(VaadinService.class),
                () -> {
                    if (discoveries.incrementAndGet() == 1) {
                        throw new IllegalStateException("routes unavailable");
                    }
                    return RouteUtil.DiscoveryResult.complete(List.of(admin));
                },
                nanoTime::get);

        assertEquals(RouteUtil.RouteAccess.NO_MATCH, routeUtil.checkRouteAccess(context("/admin"), identity("ADMIN")));
        assertEquals(RouteUtil.RouteAccess.NO_MATCH, routeUtil.checkRouteAccess(context("/admin"), identity("ADMIN")));
        assertEquals(1, discoveries.get());

        nanoTime.set(Duration.ofSeconds(1).toNanos());
        assertEquals(RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/admin"), identity("ADMIN")));
        assertEquals(2, discoveries.get());
    }

    @Test
    void discovery_developmentCompleteTreeIsRefreshedAfterBackoff() {
        AtomicInteger discoveries = new AtomicInteger();
        AtomicLong nanoTime = new AtomicLong();
        AvailableViewInfo admin = view("admin", new String[] {"ADMIN"}, Map.of());
        AvailableViewInfo user = view("admin", new String[] {"USER"}, Map.of());
        RouteUtil routeUtil = new RouteUtil(
                mock(VaadinService.class),
                () -> RouteUtil.DiscoveryResult.complete(List.of(discoveries.incrementAndGet() == 1 ? admin : user)),
                nanoTime::get);

        assertEquals(RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/admin"), identity("ADMIN")));
        assertEquals(RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/admin"), identity("ADMIN")));
        assertEquals(1, discoveries.get());

        nanoTime.set(Duration.ofSeconds(1).toNanos());
        assertEquals(RouteUtil.RouteAccess.DENY, routeUtil.checkRouteAccess(context("/admin"), identity("ADMIN")));
        assertEquals(RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/admin"), identity("USER")));
        assertEquals(2, discoveries.get());
    }

    @Test
    void discovery_productionCompleteTreeIsNotRefreshed() {
        AtomicInteger discoveries = new AtomicInteger();
        AtomicLong nanoTime = new AtomicLong();
        AvailableViewInfo admin = view("admin", new String[] {"ADMIN"}, Map.of());
        RouteUtil routeUtil = new RouteUtil(
                mock(VaadinService.class),
                false,
                () -> {
                    discoveries.incrementAndGet();
                    return RouteUtil.DiscoveryResult.complete(List.of(admin));
                },
                nanoTime::get);

        assertEquals(RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/admin"), identity("ADMIN")));
        nanoTime.set(Duration.ofDays(1).toNanos());
        assertEquals(RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/admin"), identity("ADMIN")));
        assertEquals(1, discoveries.get());
    }

    @Test
    void discovery_developmentFailureKeepsLastCompleteTreeUntilSuccessfulRefresh() {
        AtomicInteger discoveries = new AtomicInteger();
        AtomicLong nanoTime = new AtomicLong();
        AvailableViewInfo admin = view("admin", new String[] {"ADMIN"}, Map.of());
        AvailableViewInfo user = view("admin", new String[] {"USER"}, Map.of());
        RouteUtil routeUtil = new RouteUtil(
                mock(VaadinService.class),
                () -> switch (discoveries.incrementAndGet()) {
                    case 1 -> RouteUtil.DiscoveryResult.complete(List.of(admin));
                    case 2 -> RouteUtil.DiscoveryResult.fallback(Map.of());
                    default -> RouteUtil.DiscoveryResult.complete(List.of(user));
                },
                nanoTime::get);

        assertEquals(RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/admin"), identity("ADMIN")));
        nanoTime.set(Duration.ofSeconds(1).toNanos());
        assertEquals(RouteUtil.RouteAccess.ALLOW, routeUtil.checkRouteAccess(context("/admin"), identity("ADMIN")));
        assertEquals(2, discoveries.get());

        nanoTime.set(Duration.ofSeconds(2).toNanos());
        assertEquals(RouteUtil.RouteAccess.DENY, routeUtil.checkRouteAccess(context("/admin"), identity("ADMIN")));
        assertEquals(3, discoveries.get());
    }

    @Test
    void checkRouteAccess_anonymousIdentityDeniedForLoginRequiredRoute() {
        RouteUtil routeUtil = routeUtil(Map.of("profile", view("profile", new String[0], Map.of())));

        assertEquals(RouteUtil.RouteAccess.DENY, routeUtil.checkRouteAccess(context("/profile"), anonymousIdentity()));
    }

    private static RouteUtil routeUtil(Map<String, AvailableViewInfo> routes) {
        RouteUtil routeUtil = new RouteUtil(mock(VaadinService.class), false, () -> null, System::nanoTime);
        routeUtil.setRoutes(new LinkedHashMap<>(routes));
        return routeUtil;
    }

    private static RouteUtil routeUtil(List<AvailableViewInfo> routeTree) {
        RouteUtil routeUtil = new RouteUtil(mock(VaadinService.class), false, () -> null, System::nanoTime);
        routeUtil.setRouteTree(routeTree);
        return routeUtil;
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
