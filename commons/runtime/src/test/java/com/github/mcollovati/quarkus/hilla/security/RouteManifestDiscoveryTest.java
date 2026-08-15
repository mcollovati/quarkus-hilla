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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.menu.AvailableViewInfo;
import com.vaadin.flow.server.menu.RouteParamType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class RouteManifestDiscoveryTest {

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
                RouteManifestDiscovery.readRouteTree(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        AvailableViewInfo layout = routes.getFirst();
        AvailableViewInfo child = layout.children().getFirst();
        assertEquals("admin", layout.route());
        assertArrayEquals(new String[] {"ADMIN"}, layout.rolesAllowed());
        assertEquals(":id", child.route());
        assertEquals(RouteParamType.REQUIRED, child.routeParameters().get(":id"));
    }

    @Test
    void readRouteResource_unchangedReliableFingerprintSkipsParsing() throws Exception {
        Path manifest = temporaryDirectory.resolve("file-routes.json");
        Files.writeString(manifest, "[]");
        Files.setLastModifiedTime(manifest, FileTime.fromMillis(System.currentTimeMillis() - 5_000));

        RouteManifestDiscovery.DiscoveryResult first =
                RouteManifestDiscovery.readRouteResource(manifest.toUri().toURL(), null);
        RouteManifestDiscovery.DiscoveryResult unchanged =
                RouteManifestDiscovery.readRouteResource(manifest.toUri().toURL(), first.resourceFingerprint());
        Files.writeString(manifest, "[\n]");
        Files.setLastModifiedTime(manifest, FileTime.fromMillis(System.currentTimeMillis()));
        RouteManifestDiscovery.DiscoveryResult changed =
                RouteManifestDiscovery.readRouteResource(manifest.toUri().toURL(), first.resourceFingerprint());

        assertFalse(first.unchanged());
        assertTrue(unchanged.unchanged());
        assertFalse(changed.unchanged());
    }

    @Test
    void discoverFromResource_productionMissingUnexpectedManifestReturnsEmptyCompleteTree() throws Exception {
        RouteManifestDiscovery discovery = discovery(false, false, RouteManifestDiscovery.DiscoveryResult::failure);

        RouteManifestDiscovery.DiscoveryResult result = discovery.discoverFromResource(null);

        assertEquals(List.of(), result.routeTree());
        assertFalse(result.retryImmediately());
    }

    @Test
    void discoverFromResource_productionMissingExpectedManifestReturnsFailure() throws Exception {
        RouteManifestDiscovery discovery = discovery(false, true, RouteManifestDiscovery.DiscoveryResult::failure);

        RouteManifestDiscovery.DiscoveryResult result = discovery.discoverFromResource(null);

        assertNull(result.routeTree());
        assertFalse(result.retryImmediately());
    }

    @Test
    void initializeProductionSnapshot_completeTreeSucceeds() {
        RouteManifestDiscovery discovery = discovery(
                false,
                true,
                () -> RouteManifestDiscovery.DiscoveryResult.complete(List.of(view("public", new String[0]))));

        discovery.initializeProductionSnapshot();

        assertCompleteWithRole(discovery.currentSnapshot(), "public");
    }

    @Test
    void initializeProductionSnapshot_missingExpectedManifestFailsStartup() {
        RouteManifestDiscovery discovery =
                discovery(false, true, () -> RouteManifestDiscovery.missingManifest(false, true));

        assertThrows(IllegalStateException.class, discovery::initializeProductionSnapshot);
    }

    @Test
    void initializeProductionSnapshot_missingUnexpectedManifestSucceeds() {
        RouteManifestDiscovery discovery =
                discovery(false, false, () -> RouteManifestDiscovery.missingManifest(false, false));

        discovery.initializeProductionSnapshot();

        assertCompleteEmpty(discovery.currentSnapshot());
    }

    @Test
    void initializeProductionSnapshot_invalidRouteTreeFailsStartup() {
        AvailableViewInfo invalidChild = view("/users", new String[0]);
        AvailableViewInfo layout = view("admin", new String[] {"ADMIN"}, List.of(invalidChild));
        RouteManifestDiscovery discovery =
                discovery(false, true, () -> RouteManifestDiscovery.DiscoveryResult.complete(List.of(layout)));

        assertThrows(IllegalStateException.class, discovery::initializeProductionSnapshot);
    }

    @Test
    void developmentFailureResultUsesBackoffThenPublishesCompleteTree() {
        AtomicInteger discoveries = new AtomicInteger();
        AtomicLong nanoTime = new AtomicLong();
        RouteManifestDiscovery discovery = discovery(
                true,
                true,
                () -> discoveries.incrementAndGet() == 1
                        ? RouteManifestDiscovery.DiscoveryResult.failure()
                        : RouteManifestDiscovery.DiscoveryResult.complete(
                                List.of(view("admin", new String[] {"ADMIN"}))),
                nanoTime::get);

        assertIncomplete(discovery.currentSnapshot());
        assertIncomplete(discovery.currentSnapshot());
        assertEquals(1, discoveries.get());

        nanoTime.set(Duration.ofSeconds(1).toNanos());
        assertCompleteWithRole(discovery.currentSnapshot(), "admin", "ADMIN");
        assertEquals(2, discoveries.get());
    }

    @Test
    void developmentMissingExpectedManifestIsRetriedImmediately() {
        AtomicInteger discoveries = new AtomicInteger();
        RouteManifestDiscovery discovery = discovery(
                true,
                true,
                () -> discoveries.incrementAndGet() == 1
                        ? RouteManifestDiscovery.missingManifest(true, true)
                        : RouteManifestDiscovery.DiscoveryResult.complete(
                                List.of(view("admin", new String[] {"ADMIN"}))));

        assertIncomplete(discovery.currentSnapshot());
        assertCompleteWithRole(discovery.currentSnapshot(), "admin", "ADMIN");
        assertEquals(2, discoveries.get());
    }

    @Test
    void productionMissingExpectedManifestIsTerminalFailure() {
        AtomicInteger discoveries = new AtomicInteger();
        RouteManifestDiscovery discovery = discovery(false, true, () -> {
            discoveries.incrementAndGet();
            return RouteManifestDiscovery.missingManifest(false, true);
        });

        assertIncomplete(discovery.currentSnapshot());
        assertIncomplete(discovery.currentSnapshot());
        assertEquals(1, discoveries.get());
    }

    @Test
    void developmentMissingUnexpectedManifestPublishesCompleteEmptyTree() {
        RouteManifestDiscovery discovery =
                discovery(true, false, () -> RouteManifestDiscovery.missingManifest(true, false));

        assertCompleteEmpty(discovery.currentSnapshot());
    }

    @Test
    void productionFailureResultIsTerminal() {
        AtomicInteger discoveries = new AtomicInteger();
        RouteManifestDiscovery discovery = discovery(false, true, () -> {
            discoveries.incrementAndGet();
            return RouteManifestDiscovery.DiscoveryResult.failure();
        });

        assertIncomplete(discovery.currentSnapshot());
        assertIncomplete(discovery.currentSnapshot());
        assertEquals(1, discoveries.get());
    }

    @Test
    void developmentExceptionIsRetriedAfterBackoff() {
        AtomicInteger discoveries = new AtomicInteger();
        AtomicLong nanoTime = new AtomicLong();
        RouteManifestDiscovery discovery = discovery(
                true,
                true,
                () -> {
                    if (discoveries.incrementAndGet() == 1) {
                        throw new IllegalStateException("routes unavailable");
                    }
                    return RouteManifestDiscovery.DiscoveryResult.complete(
                            List.of(view("admin", new String[] {"ADMIN"})));
                },
                nanoTime::get);

        assertIncomplete(discovery.currentSnapshot());
        assertIncomplete(discovery.currentSnapshot());
        assertEquals(1, discoveries.get());

        nanoTime.set(Duration.ofSeconds(1).toNanos());
        assertCompleteWithRole(discovery.currentSnapshot(), "admin", "ADMIN");
        assertEquals(2, discoveries.get());
    }

    @Test
    void productionExceptionIsTerminal() {
        AtomicInteger discoveries = new AtomicInteger();
        RouteManifestDiscovery discovery = discovery(false, true, () -> {
            discoveries.incrementAndGet();
            throw new IllegalStateException("routes unavailable");
        });

        assertIncomplete(discovery.currentSnapshot());
        assertIncomplete(discovery.currentSnapshot());
        assertEquals(1, discoveries.get());
    }

    @Test
    void developmentCompleteTreeIsRefreshedAfterBackoff() {
        AtomicInteger discoveries = new AtomicInteger();
        AtomicLong nanoTime = new AtomicLong();
        RouteManifestDiscovery discovery = discovery(
                true,
                true,
                () -> RouteManifestDiscovery.DiscoveryResult.complete(List.of(view(
                        "admin", discoveries.incrementAndGet() == 1 ? new String[] {"ADMIN"} : new String[] {"USER"}))),
                nanoTime::get);

        assertCompleteWithRole(discovery.currentSnapshot(), "admin", "ADMIN");
        assertCompleteWithRole(discovery.currentSnapshot(), "admin", "ADMIN");
        assertEquals(1, discoveries.get());

        nanoTime.set(Duration.ofSeconds(1).toNanos());
        assertCompleteWithRole(discovery.currentSnapshot(), "admin", "USER");
        assertEquals(2, discoveries.get());
    }

    @Test
    void developmentUnchangedResourceKeepsPublishedTree() {
        AtomicInteger discoveries = new AtomicInteger();
        AtomicLong nanoTime = new AtomicLong();
        RouteManifestDiscovery discovery = discovery(
                true,
                true,
                () -> discoveries.incrementAndGet() == 1
                        ? RouteManifestDiscovery.DiscoveryResult.complete(
                                List.of(view("admin", new String[] {"ADMIN"})))
                        : RouteManifestDiscovery.DiscoveryResult.unchangedResult(),
                nanoTime::get);

        assertCompleteWithRole(discovery.currentSnapshot(), "admin", "ADMIN");
        nanoTime.set(Duration.ofSeconds(1).toNanos());
        assertCompleteWithRole(discovery.currentSnapshot(), "admin", "ADMIN");
        assertEquals(2, discoveries.get());
    }

    @Test
    void productionCompleteTreeIsNotRefreshed() {
        AtomicInteger discoveries = new AtomicInteger();
        AtomicLong nanoTime = new AtomicLong();
        RouteManifestDiscovery discovery = discovery(
                false,
                true,
                () -> {
                    discoveries.incrementAndGet();
                    return RouteManifestDiscovery.DiscoveryResult.complete(
                            List.of(view("admin", new String[] {"ADMIN"})));
                },
                nanoTime::get);

        assertCompleteWithRole(discovery.currentSnapshot(), "admin", "ADMIN");
        nanoTime.set(Duration.ofDays(1).toNanos());
        assertCompleteWithRole(discovery.currentSnapshot(), "admin", "ADMIN");
        assertEquals(1, discoveries.get());
    }

    @Test
    void productionEmptyTreeIsNotRefreshed() {
        AtomicInteger discoveries = new AtomicInteger();
        RouteManifestDiscovery discovery = discovery(false, false, () -> {
            discoveries.incrementAndGet();
            return RouteManifestDiscovery.DiscoveryResult.complete(List.of());
        });

        assertCompleteEmpty(discovery.currentSnapshot());
        assertCompleteEmpty(discovery.currentSnapshot());
        assertEquals(1, discoveries.get());
    }

    @Test
    void developmentRefreshFailurePublishesIncompleteSnapshotUntilSuccessfulRefresh() {
        AtomicInteger discoveries = new AtomicInteger();
        AtomicLong nanoTime = new AtomicLong();
        RouteManifestDiscovery discovery = discovery(
                true,
                true,
                () -> switch (discoveries.incrementAndGet()) {
                    case 1 ->
                        RouteManifestDiscovery.DiscoveryResult.complete(List.of(view("admin", new String[] {"ADMIN"})));
                    case 2 -> RouteManifestDiscovery.DiscoveryResult.failure();
                    default ->
                        RouteManifestDiscovery.DiscoveryResult.complete(List.of(view("admin", new String[] {"USER"})));
                },
                nanoTime::get);

        assertCompleteWithRole(discovery.currentSnapshot(), "admin", "ADMIN");
        nanoTime.set(Duration.ofSeconds(1).toNanos());
        assertIncomplete(discovery.currentSnapshot());
        assertEquals(2, discoveries.get());

        nanoTime.set(Duration.ofSeconds(2).toNanos());
        assertCompleteWithRole(discovery.currentSnapshot(), "admin", "USER");
        assertEquals(3, discoveries.get());
    }

    @Test
    void productionRouteTreeCompilationFailurePublishesIncompleteSnapshot() {
        AvailableViewInfo brokenParent = view("admin", new String[] {"ADMIN"}, Arrays.asList((AvailableViewInfo) null));
        RouteManifestDiscovery discovery =
                discovery(false, true, () -> RouteManifestDiscovery.DiscoveryResult.complete(List.of(brokenParent)));

        assertIncomplete(discovery.currentSnapshot());
    }

    @Test
    void concurrentRequestsPublishSingleProductionSnapshot() throws Exception {
        AtomicInteger discoveries = new AtomicInteger();
        CountDownLatch discoveryStarted = new CountDownLatch(1);
        CountDownLatch secondRequestStarted = new CountDownLatch(1);
        CountDownLatch continueDiscovery = new CountDownLatch(1);
        RouteManifestDiscovery discovery = discovery(false, true, () -> {
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
            return RouteManifestDiscovery.DiscoveryResult.complete(List.of(view("admin", new String[] {"ADMIN"})));
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<RouteSnapshotCompiler.RouteSnapshot> first = executor.submit(discovery::currentSnapshot);
            assertTrue(discoveryStarted.await(5, TimeUnit.SECONDS));
            Future<RouteSnapshotCompiler.RouteSnapshot> second = executor.submit(() -> {
                secondRequestStarted.countDown();
                return discovery.currentSnapshot();
            });
            assertTrue(secondRequestStarted.await(5, TimeUnit.SECONDS));
            continueDiscovery.countDown();

            assertCompleteWithRole(first.get(5, TimeUnit.SECONDS), "admin", "ADMIN");
            assertCompleteWithRole(second.get(5, TimeUnit.SECONDS), "admin", "ADMIN");
            assertEquals(1, discoveries.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private static RouteManifestDiscovery discovery(
            boolean developmentMode,
            boolean fileRoutesManifestExpected,
            Supplier<RouteManifestDiscovery.DiscoveryResult> routeDiscovery) {
        return discovery(developmentMode, fileRoutesManifestExpected, routeDiscovery, System::nanoTime);
    }

    private static RouteManifestDiscovery discovery(
            boolean developmentMode,
            boolean fileRoutesManifestExpected,
            Supplier<RouteManifestDiscovery.DiscoveryResult> routeDiscovery,
            LongSupplier nanoTime) {
        return new RouteManifestDiscovery(
                mock(VaadinService.class), developmentMode, fileRoutesManifestExpected, routeDiscovery, nanoTime);
    }

    private static void assertCompleteEmpty(RouteSnapshotCompiler.RouteSnapshot snapshot) {
        assertTrue(snapshot.hierarchyComplete());
        assertTrue(snapshot.routes().isEmpty());
    }

    private static void assertIncomplete(RouteSnapshotCompiler.RouteSnapshot snapshot) {
        assertFalse(snapshot.hierarchyComplete());
        assertTrue(snapshot.routes().isEmpty());
    }

    private static void assertCompleteWithRole(
            RouteSnapshotCompiler.RouteSnapshot snapshot, String path, String... expectedRoles) {
        assertTrue(snapshot.hierarchyComplete());
        String compiledPath = path.startsWith("/") ? path : "/" + path;
        RoutePatternMatcher.CompiledRoute<List<List<AvailableViewInfo>>> route = snapshot.routes().stream()
                .filter(candidate -> compiledPath.equals(candidate.path()))
                .findFirst()
                .orElseThrow();
        assertArrayEquals(expectedRoles, route.target().getFirst().getLast().rolesAllowed());
    }

    private static AvailableViewInfo view(String route, String[] rolesAllowed) {
        return view(route, rolesAllowed, List.of());
    }

    private static AvailableViewInfo view(String route, String[] rolesAllowed, List<AvailableViewInfo> children) {
        return new AvailableViewInfo(
                route, rolesAllowed, true, route, false, true, null, children, Map.of(), false, null);
    }
}
