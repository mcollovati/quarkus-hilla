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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.menu.AvailableViewInfo;
import com.vaadin.flow.server.menu.RouteParamType;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RouteUtilTest {

    @TempDir
    Path temporaryDirectory;

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

        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context("/admin/42"), identity("USER")));
        assertEquals(AuthorizationDecision.ALLOW, routeUtil.checkRouteAccess(context("/admin/42"), identity("ADMIN")));
        assertEquals(
                RouteParamType.REQUIRED,
                routes.getFirst().children().getFirst().routeParameters().get(":id"));
    }

    @Test
    void readRouteResource_unchangedReliableFingerprintSkipsParsing() throws Exception {
        Path manifest = temporaryDirectory.resolve("file-routes.json");
        Files.writeString(manifest, "[]");
        Files.setLastModifiedTime(manifest, FileTime.fromMillis(System.currentTimeMillis() - 5_000));

        RouteUtil.DiscoveryResult first =
                RouteUtil.readRouteResource(manifest.toUri().toURL(), null);
        RouteUtil.DiscoveryResult unchanged =
                RouteUtil.readRouteResource(manifest.toUri().toURL(), first.resourceFingerprint());
        Files.writeString(manifest, "[\n]");
        Files.setLastModifiedTime(manifest, FileTime.fromMillis(System.currentTimeMillis()));
        RouteUtil.DiscoveryResult changed =
                RouteUtil.readRouteResource(manifest.toUri().toURL(), first.resourceFingerprint());

        assertFalse(first.unchanged());
        assertTrue(unchanged.unchanged());
        assertFalse(changed.unchanged());
    }

    @Test
    void checkRouteAccess_requiredParameterEnforcesRouteRoles() {
        RouteUtil routeUtil = routeUtil(
                Map.of("users/:id", view("users/:id", new String[] {"ADMIN"}, Map.of(":id", RouteParamType.REQUIRED))));

        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context("/users/42"), identity("USER")));
        assertEquals(AuthorizationDecision.ALLOW, routeUtil.checkRouteAccess(context("/users/42"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_optionalParameterMatchesWithAndWithoutValue() {
        RouteUtil routeUtil = routeUtil(Map.of(
                "reports/:filter?",
                view("reports/:filter?", new String[] {"ADMIN"}, Map.of(":filter?", RouteParamType.OPTIONAL))));

        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context("/reports"), identity("USER")));
        assertEquals(
                AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context("/reports/open"), identity("USER")));
        assertEquals(
                AuthorizationDecision.ALLOW, routeUtil.checkRouteAccess(context("/reports/open"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_optionalStaticSegmentMatchesWithAndWithoutSegment() {
        RouteUtil routeUtil = routeUtil(Map.of("projects?", view("projects?", new String[] {"ADMIN"}, Map.of())));

        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context("/"), identity("USER")));
        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context("/projects"), identity("USER")));
        assertEquals(AuthorizationDecision.ALLOW, routeUtil.checkRouteAccess(context("/"), identity("ADMIN")));
        assertEquals(AuthorizationDecision.ALLOW, routeUtil.checkRouteAccess(context("/projects"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_indexRouteOutranksRestrictedOmittedOptionalStaticRoute() {
        Map<String, AvailableViewInfo> routes = new LinkedHashMap<>();
        routes.put("", view("", new String[0], Map.of()));
        routes.put("projects?", view("projects?", new String[] {"ADMIN"}, Map.of()));
        RouteUtil routeUtil = routeUtil(routes);

        assertEquals(AuthorizationDecision.ALLOW, routeUtil.checkRouteAccess(context("/"), identity("USER")));
        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context("/projects"), identity("USER")));
    }

    @Test
    void checkRouteAccess_preservesEncodedSlashInsideParameter() {
        RouteUtil routeUtil = routeUtil(
                Map.of("items/:id", view("items/:id", new String[] {"ADMIN"}, Map.of(":id", RouteParamType.REQUIRED))));

        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context("/items/a%2Fb"), identity("USER")));
        assertEquals(
                AuthorizationDecision.ALLOW, routeUtil.checkRouteAccess(context("/items/a%2Fb"), identity("ADMIN")));
        assertEquals(
                AuthorizationDecision.NO_MATCH, routeUtil.checkRouteAccess(context("/items/a/b"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_staticRouteWinsOverParameterRoute() {
        Map<String, AvailableViewInfo> routes = new LinkedHashMap<>();
        routes.put("users/:id", view("users/:id", new String[] {"ADMIN"}, Map.of(":id", RouteParamType.REQUIRED)));
        routes.put("users/new", view("users/new", new String[] {"USER"}, Map.of()));
        RouteUtil routeUtil = routeUtil(routes);

        assertEquals(AuthorizationDecision.ALLOW, routeUtil.checkRouteAccess(context("/users/new"), identity("USER")));
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
    void checkRouteAccess_parameterSuffixMustMatchBeforeOutrankingWildcard() {
        Map<String, AvailableViewInfo> routes = new LinkedHashMap<>();
        routes.put("files/:id.json", view("files/:id.json", new String[0], Map.of()));
        routes.put("files/*", view("files/*", new String[] {"ADMIN"}, Map.of("*", RouteParamType.WILDCARD)));
        RouteUtil routeUtil = routeUtil(routes);

        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context("/files/foo"), identity("USER")));
        assertEquals(AuthorizationDecision.ALLOW, routeUtil.checkRouteAccess(context("/files/foo"), identity("ADMIN")));
        assertEquals(
                AuthorizationDecision.ALLOW, routeUtil.checkRouteAccess(context("/files/foo.json"), identity("USER")));
    }

    @Test
    void checkRouteAccess_staticRouteOutranksPartialParameterRoute() {
        Map<String, AvailableViewInfo> routes = new LinkedHashMap<>();
        routes.put("files/:id.json", view("files/:id.json", new String[] {"ADMIN"}, Map.of()));
        routes.put("files/public.json", view("files/public.json", new String[0], Map.of()));
        RouteUtil routeUtil = routeUtil(routes);

        assertEquals(
                AuthorizationDecision.ALLOW,
                routeUtil.checkRouteAccess(context("/files/public.json"), identity("USER")));
        assertEquals(
                AuthorizationDecision.ALLOW,
                routeUtil.checkRouteAccess(context("/files/public.json"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_optionalParameterWithSuffixKeepsSegmentRequired() {
        RouteUtil routeUtil =
                routeUtil(Map.of("files/:id?.json", view("files/:id?.json", new String[] {"ADMIN"}, Map.of())));

        assertEquals(AuthorizationDecision.NO_MATCH, routeUtil.checkRouteAccess(context("/files"), identity("ADMIN")));
        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context("/files/.json"), identity("USER")));
        assertEquals(
                AuthorizationDecision.ALLOW, routeUtil.checkRouteAccess(context("/files/foo.json"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_layoutSecurityAppliesToIndexAtSamePath() {
        AvailableViewInfo index = view("", new String[0], Map.of());
        AvailableViewInfo layout = view("", new String[] {"ADMIN"}, Map.of(), List.of(index));
        RouteUtil routeUtil = routeUtil(List.of(layout));

        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context("/"), identity("USER")));
        assertEquals(AuthorizationDecision.ALLOW, routeUtil.checkRouteAccess(context("/"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_layoutSecurityAppliesToParameterizedChild() {
        AvailableViewInfo child = view(":id", new String[0], Map.of(":id", RouteParamType.REQUIRED));
        AvailableViewInfo layout = view("admin", new String[] {"ADMIN"}, Map.of(), List.of(child));
        RouteUtil routeUtil = routeUtil(List.of(layout));

        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context("/admin/42"), identity("USER")));
        assertEquals(AuthorizationDecision.ALLOW, routeUtil.checkRouteAccess(context("/admin/42"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_layoutSecurityAppliesToNestedAbsoluteChild() {
        AvailableViewInfo child = view("/admin/users", new String[0], Map.of());
        AvailableViewInfo layout = view("admin", new String[] {"ADMIN"}, Map.of(), List.of(child));
        RouteUtil routeUtil = routeUtil(List.of(layout));

        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context("/admin/users"), identity("USER")));
        assertEquals(
                AuthorizationDecision.ALLOW, routeUtil.checkRouteAccess(context("/admin/users"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_nestedAbsoluteChildUsesReactPrefixSlicing() {
        AvailableViewInfo child = view("/administrator/users", new String[0], Map.of());
        AvailableViewInfo layout = view("admin", new String[] {"ADMIN"}, Map.of(), List.of(child));
        RouteUtil routeUtil = routeUtil(List.of(layout));

        assertEquals(
                AuthorizationDecision.DENY,
                routeUtil.checkRouteAccess(context("/admin/istrator/users"), identity("USER")));
        assertEquals(
                AuthorizationDecision.ALLOW,
                routeUtil.checkRouteAccess(context("/admin/istrator/users"), identity("ADMIN")));
        assertEquals(
                AuthorizationDecision.NO_MATCH,
                routeUtil.checkRouteAccess(context("/administrator/users"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_invalidAbsoluteChildDoesNotDiscardOtherRoutes() {
        AvailableViewInfo invalidChild = view("/users", new String[0], Map.of());
        AvailableViewInfo layout = view("admin", new String[] {"ADMIN"}, Map.of(), List.of(invalidChild));
        AvailableViewInfo publicView = view("public", new String[0], Map.of());
        RouteUtil routeUtil = routeUtil(List.of(layout, publicView));

        assertEquals(AuthorizationDecision.NO_MATCH, routeUtil.checkRouteAccess(context("/users"), identity("ADMIN")));
        assertEquals(AuthorizationDecision.ALLOW, routeUtil.checkRouteAccess(context("/public"), identity("USER")));
    }

    @Test
    void checkRouteAccess_wildcardEnforcesRouteRoles() {
        RouteUtil routeUtil = routeUtil(
                Map.of("files/*", view("files/*", new String[] {"ADMIN"}, Map.of("*", RouteParamType.WILDCARD))));

        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context("/files/a/b"), identity("USER")));
        assertEquals(AuthorizationDecision.ALLOW, routeUtil.checkRouteAccess(context("/files/a/b"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_wildcardBeforeOptionalParameterMatchesOptionalExpansion() {
        RouteUtil routeUtil = routeUtil(Map.of(
                "files/*/:id?",
                view(
                        "files/*/:id?",
                        new String[] {"ADMIN"},
                        Map.of("*", RouteParamType.WILDCARD, ":id?", RouteParamType.OPTIONAL))));

        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context("/files/a/b"), identity("USER")));
        assertEquals(AuthorizationDecision.ALLOW, routeUtil.checkRouteAccess(context("/files/a/b"), identity("ADMIN")));
    }

    @Test
    void checkRouteAccess_nonTerminalWildcardUsesLiteralBranch() {
        RouteUtil routeUtil = routeUtil(Map.of(
                "files/*/:id",
                view(
                        "files/*/:id",
                        new String[] {"ADMIN"},
                        Map.of("*", RouteParamType.WILDCARD, ":id", RouteParamType.REQUIRED))));

        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context("/files/*/42"), identity("USER")));
        assertEquals(
                AuthorizationDecision.ALLOW, routeUtil.checkRouteAccess(context("/files/*/42"), identity("ADMIN")));
        assertEquals(
                AuthorizationDecision.NO_MATCH, routeUtil.checkRouteAccess(context("/files/a/42"), identity("ADMIN")));
    }

    @Test
    void discovery_developmentFailureResultUsesBackoffThenPublishesCompleteTree() {
        AtomicInteger discoveries = new AtomicInteger();
        AtomicLong nanoTime = new AtomicLong();
        AvailableViewInfo admin = view("admin", new String[] {"ADMIN"}, Map.of());
        RouteUtil routeUtil = new RouteUtil(
                mock(VaadinService.class),
                () -> discoveries.incrementAndGet() == 1
                        ? RouteUtil.DiscoveryResult.failure()
                        : RouteUtil.DiscoveryResult.complete(List.of(admin)),
                nanoTime::get);

        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context("/admin"), identity("ADMIN")));
        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context("/admin"), identity("ADMIN")));
        assertEquals(1, discoveries.get());

        nanoTime.set(Duration.ofSeconds(1).toNanos());
        assertEquals(AuthorizationDecision.ALLOW, routeUtil.checkRouteAccess(context("/admin"), identity("ADMIN")));
        assertEquals(2, discoveries.get());
    }

    @Test
    void discovery_developmentMissingReactManifestIsRetriedImmediatelyAndDeniesUntilComplete() {
        AtomicInteger discoveries = new AtomicInteger();
        AtomicLong nanoTime = new AtomicLong();
        AvailableViewInfo admin = view("admin", new String[] {"ADMIN"}, Map.of());
        RouteUtil routeUtil = new RouteUtil(
                mock(VaadinService.class),
                () -> discoveries.incrementAndGet() == 1
                        ? RouteUtil.missingManifest(true, true, false)
                        : RouteUtil.DiscoveryResult.complete(List.of(admin)),
                nanoTime::get);

        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context("/admin"), identity("ADMIN")));
        assertEquals(AuthorizationDecision.ALLOW, routeUtil.checkRouteAccess(context("/admin"), identity("ADMIN")));
        assertEquals(2, discoveries.get());
    }

    @Test
    void discovery_productionMissingReactManifestIsNoMatch() {
        AtomicInteger discoveries = new AtomicInteger();
        RouteUtil routeUtil = new RouteUtil(
                mock(VaadinService.class),
                false,
                () -> {
                    discoveries.incrementAndGet();
                    return RouteUtil.missingManifest(false, true, false);
                },
                System::nanoTime);

        assertEquals(AuthorizationDecision.NO_MATCH, routeUtil.checkRouteAccess(context("/custom"), identity("ADMIN")));
        assertEquals(AuthorizationDecision.NO_MATCH, routeUtil.checkRouteAccess(context("/custom"), identity("ADMIN")));
        assertEquals(1, discoveries.get());
    }

    @Test
    void discovery_missingNonReactManifestIsNoMatch() {
        RouteUtil routeUtil = new RouteUtil(
                mock(VaadinService.class),
                false,
                () -> RouteUtil.missingManifest(true, false, false),
                System::nanoTime);

        assertEquals(AuthorizationDecision.NO_MATCH, routeUtil.checkRouteAccess(context("/custom"), identity("USER")));
    }

    @Test
    void discovery_developmentMissingManifestWithCustomReactRouterIsNoMatch() {
        RouteUtil routeUtil = new RouteUtil(
                mock(VaadinService.class), false, () -> RouteUtil.missingManifest(true, true, true), System::nanoTime);

        assertEquals(AuthorizationDecision.NO_MATCH, routeUtil.checkRouteAccess(context("/custom"), identity("USER")));
    }

    @Test
    void discovery_productionFailureResultIsNotRepeatedAndDeniesEveryRoute() {
        AtomicInteger discoveries = new AtomicInteger();
        RouteUtil routeUtil = new RouteUtil(
                mock(VaadinService.class),
                false,
                () -> {
                    discoveries.incrementAndGet();
                    return RouteUtil.DiscoveryResult.failure();
                },
                System::nanoTime);

        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context("/public"), identity("USER")));
        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context("/unknown"), identity("USER")));
        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context("/public"), identity("USER")));
        assertEquals(1, discoveries.get());
    }

    @Test
    void discovery_developmentFailureIsRetriedAfterBackoffAndDeniesUntilComplete() {
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

        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context("/admin"), identity("ADMIN")));
        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context("/admin"), identity("ADMIN")));
        assertEquals(1, discoveries.get());

        nanoTime.set(Duration.ofSeconds(1).toNanos());
        assertEquals(AuthorizationDecision.ALLOW, routeUtil.checkRouteAccess(context("/admin"), identity("ADMIN")));
        assertEquals(2, discoveries.get());
    }

    @Test
    void discovery_productionFailureIsNotRetriedAndDeniesEveryRoute() {
        AtomicInteger discoveries = new AtomicInteger();
        RouteUtil routeUtil = new RouteUtil(
                mock(VaadinService.class),
                false,
                () -> {
                    discoveries.incrementAndGet();
                    throw new IllegalStateException("routes unavailable");
                },
                System::nanoTime);

        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context("/admin"), identity("ADMIN")));
        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context("/unknown"), identity("ADMIN")));
        assertEquals(1, discoveries.get());
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

        assertEquals(AuthorizationDecision.ALLOW, routeUtil.checkRouteAccess(context("/admin"), identity("ADMIN")));
        assertEquals(AuthorizationDecision.ALLOW, routeUtil.checkRouteAccess(context("/admin"), identity("ADMIN")));
        assertEquals(1, discoveries.get());

        nanoTime.set(Duration.ofSeconds(1).toNanos());
        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context("/admin"), identity("ADMIN")));
        assertEquals(AuthorizationDecision.ALLOW, routeUtil.checkRouteAccess(context("/admin"), identity("USER")));
        assertEquals(2, discoveries.get());
    }

    @Test
    void discovery_developmentUnchangedResourceKeepsPublishedTree() {
        AtomicInteger discoveries = new AtomicInteger();
        AtomicLong nanoTime = new AtomicLong();
        AvailableViewInfo admin = view("admin", new String[] {"ADMIN"}, Map.of());
        RouteUtil routeUtil = new RouteUtil(
                mock(VaadinService.class),
                () -> discoveries.incrementAndGet() == 1
                        ? RouteUtil.DiscoveryResult.complete(List.of(admin))
                        : RouteUtil.DiscoveryResult.unchangedResult(),
                nanoTime::get);

        assertEquals(AuthorizationDecision.ALLOW, routeUtil.checkRouteAccess(context("/admin"), identity("ADMIN")));
        nanoTime.set(Duration.ofSeconds(1).toNanos());
        assertEquals(AuthorizationDecision.ALLOW, routeUtil.checkRouteAccess(context("/admin"), identity("ADMIN")));
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

        assertEquals(AuthorizationDecision.ALLOW, routeUtil.checkRouteAccess(context("/admin"), identity("ADMIN")));
        nanoTime.set(Duration.ofDays(1).toNanos());
        assertEquals(AuthorizationDecision.ALLOW, routeUtil.checkRouteAccess(context("/admin"), identity("ADMIN")));
        assertEquals(1, discoveries.get());
    }

    @Test
    void discovery_productionAbsentManifestIsNoMatchAndNotRepeated() {
        AtomicInteger discoveries = new AtomicInteger();
        RouteUtil routeUtil = new RouteUtil(
                mock(VaadinService.class),
                false,
                () -> {
                    discoveries.incrementAndGet();
                    return RouteUtil.DiscoveryResult.complete(List.of());
                },
                System::nanoTime);

        assertEquals(AuthorizationDecision.NO_MATCH, routeUtil.checkRouteAccess(context("/custom"), identity("USER")));
        assertEquals(AuthorizationDecision.NO_MATCH, routeUtil.checkRouteAccess(context("/custom"), identity("USER")));
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
                    case 2 -> RouteUtil.DiscoveryResult.failure();
                    default -> RouteUtil.DiscoveryResult.complete(List.of(user));
                },
                nanoTime::get);

        assertEquals(AuthorizationDecision.ALLOW, routeUtil.checkRouteAccess(context("/admin"), identity("ADMIN")));
        nanoTime.set(Duration.ofSeconds(1).toNanos());
        assertEquals(AuthorizationDecision.ALLOW, routeUtil.checkRouteAccess(context("/admin"), identity("ADMIN")));
        assertEquals(2, discoveries.get());

        nanoTime.set(Duration.ofSeconds(2).toNanos());
        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context("/admin"), identity("ADMIN")));
        assertEquals(3, discoveries.get());
    }

    @Test
    void discovery_productionRouteTreeCompilationFailureDeniesEveryRoute() {
        AvailableViewInfo brokenParent =
                view("admin", new String[] {"ADMIN"}, Map.of(), java.util.Arrays.asList((AvailableViewInfo) null));
        RouteUtil routeUtil = new RouteUtil(
                mock(VaadinService.class),
                false,
                () -> RouteUtil.DiscoveryResult.complete(List.of(brokenParent)),
                System::nanoTime);

        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context("/admin"), identity("ADMIN")));
        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context("/unknown"), identity("ADMIN")));
    }

    @Test
    void discovery_concurrentRequestsPublishSingleProductionSnapshot() throws Exception {
        AtomicInteger discoveries = new AtomicInteger();
        CountDownLatch discoveryStarted = new CountDownLatch(1);
        CountDownLatch continueDiscovery = new CountDownLatch(1);
        AvailableViewInfo admin = view("admin", new String[] {"ADMIN"}, Map.of());
        RouteUtil routeUtil = new RouteUtil(
                mock(VaadinService.class),
                false,
                () -> {
                    discoveries.incrementAndGet();
                    discoveryStarted.countDown();
                    try {
                        if (!continueDiscovery.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("timed out waiting for concurrent request");
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    }
                    return RouteUtil.DiscoveryResult.complete(List.of(admin));
                },
                System::nanoTime);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<AuthorizationDecision> first =
                    executor.submit(() -> routeUtil.checkRouteAccess(context("/admin"), identity("ADMIN")));
            assertTrue(discoveryStarted.await(5, TimeUnit.SECONDS));
            Future<AuthorizationDecision> second =
                    executor.submit(() -> routeUtil.checkRouteAccess(context("/admin"), identity("ADMIN")));
            continueDiscovery.countDown();

            assertEquals(AuthorizationDecision.ALLOW, first.get(5, TimeUnit.SECONDS));
            assertEquals(AuthorizationDecision.ALLOW, second.get(5, TimeUnit.SECONDS));
            assertEquals(1, discoveries.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void checkRouteAccess_anonymousIdentityDeniedForLoginRequiredRoute() {
        RouteUtil routeUtil = routeUtil(Map.of("profile", view("profile", new String[0], Map.of())));

        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context("/profile"), anonymousIdentity()));
    }

    @Test
    void checkRouteAccess_normalizedPathFailureIsDenied() {
        RouteUtil routeUtil = routeUtil(Map.of("admin", view("admin", new String[] {"ADMIN"}, Map.of())));
        RoutingContext context = mock(RoutingContext.class);
        when(context.normalizedPath()).thenThrow(new IllegalStateException("normalization failed"));

        assertEquals(AuthorizationDecision.DENY, routeUtil.checkRouteAccess(context, identity("ADMIN")));
    }

    private static RouteUtil routeUtil(Map<String, AvailableViewInfo> routes) {
        RouteUtil routeUtil = new RouteUtil(mock(VaadinService.class), false, () -> null, System::nanoTime);
        routeUtil.setCompleteRoutesForTesting(new LinkedHashMap<>(routes));
        return routeUtil;
    }

    private static RouteUtil routeUtil(List<AvailableViewInfo> routeTree) {
        RouteUtil routeUtil = new RouteUtil(mock(VaadinService.class), false, () -> null, System::nanoTime);
        routeUtil.setCompleteRouteTreeForTesting(routeTree);
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
