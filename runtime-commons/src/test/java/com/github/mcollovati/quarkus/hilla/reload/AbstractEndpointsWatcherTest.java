/*
 * Copyright 2024 Marco Collovati, Dario Götze
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
package com.github.mcollovati.quarkus.hilla.reload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.quarkus.dev.spi.HotReplacementContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbstractEndpointsWatcherTest {

    @TempDir
    Path projectRoot;

    TestEndpointsWatcher watcher;
    private Path root1;
    private Path root2;

    @BeforeEach
    void setUp() throws IOException {
        root1 = projectRoot.resolve(Path.of("src", "main", "java"));
        Files.createDirectories(root1);
        root2 = projectRoot.resolve(Path.of("src", "main", "kotlin"));
        Files.createDirectories(root2);
    }

    @AfterEach
    void tearDown() {
        if (watcher != null) {
            watcher.stop();
        }
    }

    @Test
    void fileChanged_notWatchedFolder_changeIgnored() throws Exception {
        createWatcher().matchEverything();
        watcher.start();

        Files.writeString(projectRoot.resolve("file.txt"), "Some text");
        Files.writeString(projectRoot.resolve(Path.of("src", "file.txt")), "Some text");
        Files.writeString(projectRoot.resolve(Path.of("src", "main", "file.txt")), "Some text");

        watcher.waitForChanges(200, TimeUnit.MILLISECONDS);
        verify(watcher.hotReplacementContext, never()).doScan(anyBoolean());
        Assertions.assertEquals(0, watcher.hillaHotswapInvocations.get(), "Hilla hotswap not expected but invoked");
    }

    @Test
    void endpointWatchedFolders_fileChanged_notInWatchedFolder_changeIgnored() throws Exception {
        Path watchedDir1 = Path.of("com", "example");
        Files.createDirectories(root1.resolve(watchedDir1));
        Path watchedDir2 = Path.of("org", "application");
        Files.createDirectories(root1.resolve(watchedDir2));

        createWatcher(Set.of(watchedDir1, watchedDir2)).matchEverything();
        watcher.start();

        Files.writeString(root1.resolve("file.txt"), "Some text");
        Files.writeString(root1.resolve(Path.of("com", "file.txt")), "Some text");
        Files.writeString(root1.resolve(Path.of("org", "file.txt")), "Some text");
        watcher.waitForChanges(200, TimeUnit.MILLISECONDS);
        verify(watcher.hotReplacementContext, never()).doScan(anyBoolean());
        Assertions.assertEquals(0, watcher.hillaHotswapInvocations.get(), "Hilla hotswap not expected but invoked");
    }

    @Test
    void endpointWatchedFolders_fileChanged_inWatchedFolder_liveReloadTriggered() throws Exception {
        Path watchedDir1 = Path.of("com", "example");
        Files.createDirectories(root1.resolve(watchedDir1));
        Path watchedDir2 = Path.of("org", "application");
        Files.createDirectories(root1.resolve(watchedDir2));

        createWatcher(Set.of(watchedDir1, watchedDir2)).matchEverything();
        watcher.start();

        Files.writeString(root1.resolve(Path.of("com", "example", "file.txt")), "Some text");
        Files.writeString(root1.resolve(Path.of("org", "application", "file2.txt")), "Some text");
        watcher.waitUntilFileChanged(files -> files.size() == 2);
        verify(watcher.hotReplacementContext, times(2)).doScan(anyBoolean());
        Assertions.assertEquals(2, watcher.hillaHotswapInvocations.get(), "Hilla hotswap expected but not invoked");
    }

    @Test
    void fileAdded_watchedFolder_notEndpointRelated_changeIgnored() throws Exception {
        createWatcher();
        watcher.start();

        Path file = root1.resolve(Path.of("com", "example", "file.txt"));
        Files.createDirectories(file.getParent());
        writeFile(file, "Some text");

        watcher.waitForChanges(200, TimeUnit.MILLISECONDS);
        verify(watcher.hotReplacementContext, never()).doScan(anyBoolean());
        Assertions.assertEquals(0, watcher.hillaHotswapInvocations.get(), "Hilla hotswap not expected but invoked");
    }

    @Test
    void fileChanged_watchedFolder_notEndpointRelated_changeIgnored() throws Exception {
        Path file = root1.resolve(Path.of("com", "example", "file.txt"));
        Files.createDirectories(file.getParent());
        Files.writeString(file, "Some text");

        createWatcher();
        watcher.start();

        writeFile(file, "Some different text");

        watcher.waitForChanges(200, TimeUnit.MILLISECONDS);
        verify(watcher.hotReplacementContext, never()).doScan(anyBoolean());
        Assertions.assertEquals(0, watcher.hillaHotswapInvocations.get(), "Hilla hotswap not expected but invoked");
    }

    @Test
    void fileDeleted_watchedFolder_notEndpointRelated_changeIgnored() throws Exception {
        Path file = root1.resolve(Path.of("com", "example", "file.txt"));
        Files.createDirectories(file.getParent());
        Files.writeString(file, "Some text");

        createWatcher().matchEverything();
        watcher.start();

        Files.delete(file);

        watcher.waitForChanges(200, TimeUnit.MILLISECONDS);
        verify(watcher.hotReplacementContext, never()).doScan(anyBoolean());
        Assertions.assertEquals(0, watcher.hillaHotswapInvocations.get(), "Hilla hotswap not expected but invoked");
    }

    @Test
    void fileAdded_derivedClassIsUsedInEndpoint_liveReloadTriggered() throws Exception {
        createWatcher().matchEverything();
        watcher.classesUsedInEndpoints = () -> Set.of("com.example.Endpoint", "com.example.Data");
        watcher.deriveClassName = path -> Optional.of("com.example.Endpoint");
        watcher.start();

        Path file = root1.resolve(Path.of("com", "example", "Endpoint.java"));
        Files.createDirectories(file.getParent());
        writeFile(file, "Some text");

        watcher.waitUntilFileChanged(files -> files.size() == 1);
        verify(watcher.hotReplacementContext, times(1)).doScan(anyBoolean());
        Assertions.assertEquals(1, watcher.hillaHotswapInvocations.get(), "Hilla hotswap expected but not invoked");
    }

    @Test
    void fileChanged_derivedClassIsUsedInEndpoint_liveReloadTriggered() throws Exception {
        Path file = root1.resolve(Path.of("com", "example", "Data.java"));
        Files.createDirectories(file.getParent());
        Files.writeString(file, "Some text");

        createWatcher().matchEverything();
        watcher.classesUsedInEndpoints = () -> Set.of("com.example.Endpoint", "com.example.Data");
        watcher.deriveClassName = path -> Optional.of("com.example.Data");
        watcher.start();

        writeFile(file, "Some different text");

        watcher.waitUntilFileChanged(files -> files.size() == 1);
        verify(watcher.hotReplacementContext, times(1)).doScan(anyBoolean());
        Assertions.assertEquals(1, watcher.hillaHotswapInvocations.get(), "Hilla hotswap expected but not invoked");
    }

    @Test
    void nestedFileChanged_endpointRelated_derivedClassIsUsedInEndpoint_liveReloadTriggered() throws Exception {
        Path file = root1.resolve(Path.of("com", "example", "Data.java"));
        Files.createDirectories(file.getParent());
        Files.writeString(file, "Some text");

        createWatcher().matchEverything();
        watcher.classesUsedInEndpoints = () -> Set.of("com.example.Endpoint", "com.example.Data");
        watcher.deriveClassName = path -> Optional.of("com.example.Data");
        watcher.start();

        file = file.getParent().resolve(Path.of("nested", "pkg", "File.java"));
        Files.createDirectories(file.getParent());
        writeFile(file, "Some different text");

        watcher.waitUntilFileChanged(files -> files.size() == 1);
        verify(watcher.hotReplacementContext, times(1)).doScan(anyBoolean());
        Assertions.assertEquals(1, watcher.hillaHotswapInvocations.get(), "Hilla hotswap expected but not invoked");
    }

    @Test
    void fileDeleted_derivedClassIsUsedInEndpoint_liveReloadTriggered() throws Exception {
        Path file = root1.resolve(Path.of("com", "example", "Data.java"));
        Files.createDirectories(file.getParent());
        Files.writeString(file, "Some text");

        createWatcher().matchEverything();
        watcher.classesUsedInEndpoints = () -> Set.of("com.example.Endpoint", "com.example.Data");
        watcher.deriveClassName = path -> Optional.of("com.example.Data");
        watcher.start();

        Files.delete(file);

        watcher.waitUntilFileChanged(files -> files.size() == 1);
        verify(watcher.hotReplacementContext, times(1)).doScan(anyBoolean());
        Assertions.assertEquals(1, watcher.hillaHotswapInvocations.get(), "Hilla hotswap expected but not invoked");
    }

    @Test
    void fileDeleted_derivedClassNameNotUSedInEndpoint_changeIgnored() throws Exception {
        Path file = root1.resolve(Path.of("com", "example", "Data.java"));
        Files.createDirectories(file.getParent());
        Files.writeString(file, "Some text");

        createWatcher().matchEverything();
        watcher.classesUsedInEndpoints = () -> Set.of("com.example.Endpoint", "com.example.Data");
        watcher.deriveClassName = path -> Optional.of("com.example.NotDirectlyUsedInEndpoint");
        watcher.start();

        Files.delete(file);

        watcher.waitForChanges(200, TimeUnit.MILLISECONDS);
        verify(watcher.hotReplacementContext, never()).doScan(anyBoolean());
        Assertions.assertEquals(0, watcher.hillaHotswapInvocations.get(), "Hilla hotswap not expected but invoked");
    }

    @Test
    void fileAdded_containsTypeIsUsedInEndpoint_liveReloadTriggered() throws Exception {
        createWatcher().matchEverything();
        watcher.classesUsedInEndpoints = () -> Set.of("com.example.Endpoint", "com.example.Data");
        watcher.deriveClassName = path -> Optional.of("com.example.NotDirectlyUsedInEndpoint");
        watcher.fileContainsEndpointUsedClasses = (path, usedClasses) -> true;
        watcher.start();

        Path file = root1.resolve(Path.of("com", "example", "NotDirectlyUsedInEndpoint.java"));
        Files.createDirectories(file.getParent());
        writeFile(file, "I contain references to classes used in com.example.Endpoint");

        watcher.waitUntilFileChanged(files -> files.size() == 1);
        verify(watcher.hotReplacementContext, times(1)).doScan(anyBoolean());
        Assertions.assertEquals(1, watcher.hillaHotswapInvocations.get(), "Hilla hotswap expected but not invoked");
    }

    @Test
    void fileChanged_containsTypeIsUsedInEndpoint_liveReloadTriggered() throws Exception {
        Path file = root1.resolve(Path.of("com", "example", "Data.java"));
        Files.createDirectories(file.getParent());
        Files.writeString(file, "Some text");

        createWatcher().matchEverything();
        watcher.classesUsedInEndpoints = () -> Set.of("com.example.Endpoint", "com.example.Data");
        watcher.deriveClassName = path -> Optional.of("com.example.NotDirectlyUsedInEndpoint");
        watcher.fileContainsEndpointUsedClasses = (path, usedClasses) -> true;
        watcher.start();

        writeFile(file, "I contain references to classes used in com.example.Endpoint");

        watcher.waitUntilFileChanged(files -> files.size() == 1);
        verify(watcher.hotReplacementContext, times(1)).doScan(anyBoolean());
        Assertions.assertEquals(1, watcher.hillaHotswapInvocations.get(), "Hilla hotswap expected but not invoked");
    }

    @Test
    void fileChanged_classesUsedInEndpointNotDetected_liveReloadTriggered() throws Exception {
        Path file = root1.resolve(Path.of("com", "example", "Data.java"));
        Files.createDirectories(file.getParent());
        Files.writeString(file, "Some text");

        AtomicBoolean deriveClassNameInvoked = new AtomicBoolean(false);
        AtomicBoolean fileContainsEndpointUsedClassesInvoked = new AtomicBoolean(false);

        createWatcher().matchEverything();
        watcher.classesUsedInEndpoints = () -> null;
        watcher.deriveClassName = path -> {
            deriveClassNameInvoked.set(true);
            return Optional.of("com.example.Data");
        };
        watcher.fileContainsEndpointUsedClasses = (path, usedClasses) -> {
            fileContainsEndpointUsedClassesInvoked.set(true);
            return false;
        };
        watcher.start();

        writeFile(file, "Some text");

        watcher.waitUntilFileChanged(files -> files.size() == 1);
        verify(watcher.hotReplacementContext, times(1)).doScan(anyBoolean());

        Assertions.assertTrue(deriveClassNameInvoked.get(), "deriveClassName expected but not invoked");
        Assertions.assertFalse(
                fileContainsEndpointUsedClassesInvoked.get(),
                "fileContainsEndpointUsedClasses not expected but invoked");
        Assertions.assertEquals(1, watcher.hillaHotswapInvocations.get(), "Hilla hotswap expected but not invoked");
    }

    @Test
    void fileChanged_liveReloadRequired_serverRestarted_hillaHotswapNotInvoked() throws Exception {
        Path file = root1.resolve(Path.of("com", "example", "Data.java"));
        Files.createDirectories(file.getParent());
        Files.writeString(file, "Some text");

        createWatcher().matchEverything().withServerRestart();
        // Forces live reload
        watcher.classesUsedInEndpoints = () -> null;
        watcher.start();

        writeFile(file, "Some text");

        watcher.waitUntilFileChanged(files -> files.size() == 1);
        verify(watcher.hotReplacementContext, times(1)).doScan(anyBoolean());

        Assertions.assertEquals(0, watcher.hillaHotswapInvocations.get(), "Hilla hotswap not expected but invoked");
    }

    @Test
    void fileChanged_liveReloadRequired_serverNotRestarted_hillaHotswapInvoked() throws Exception {
        Path file = root1.resolve(Path.of("com", "example", "Data.java"));
        Files.createDirectories(file.getParent());
        Files.writeString(file, "Some text");

        createWatcher().matchEverything();
        // Forces live reload
        watcher.classesUsedInEndpoints = () -> null;
        watcher.start();

        writeFile(file, "Some text");

        watcher.waitUntilFileChanged(files -> files.size() == 1);
        verify(watcher.hotReplacementContext, times(1)).doScan(anyBoolean());

        Assertions.assertEquals(1, watcher.hillaHotswapInvocations.get(), "Hilla hotswap expected but not invoked");
    }

    private void writeFile(Path file, String content) throws IOException {
        watcher.waitForChanges(5, TimeUnit.MILLISECONDS);
        Files.writeString(file, content);
    }

    private static class TestEndpointsWatcher extends AbstractEndpointsWatcher {

        final HotReplacementContext hotReplacementContext;
        final List<Path> changedFiles = new ArrayList<>();
        final AtomicInteger hillaHotswapInvocations = new AtomicInteger(0);
        Predicate<Path> potentialEndpoint;
        Function<Path, Optional<String>> deriveClassName;
        BiPredicate<Path, Set<String>> fileContainsEndpointUsedClasses;
        Supplier<Set<String>> classesUsedInEndpoints;

        TestEndpointsWatcher(HotReplacementContext context, List<Path> rootPaths, Set<Path> endpointRelatedPaths)
                throws IOException {
            super(context, rootPaths, endpointRelatedPaths);
            reset();
            hotReplacementContext = context;
        }

        @Override
        protected boolean isPotentialEndpointRelatedFile(Path file) {
            changedFiles.add(file);
            return potentialEndpoint.test(file);
        }

        @Override
        protected Optional<String> deriveClassName(Path relativePath) {
            return deriveClassName.apply(relativePath);
        }

        @Override
        protected boolean fileContainsEndpointUsedClasses(Path classFile, Set<String> classesUsedInEndpoints) {
            return fileContainsEndpointUsedClasses.test(classFile, classesUsedInEndpoints);
        }

        @Override
        Set<String> collectClassesUsedInEndpoints() {
            return classesUsedInEndpoints.get();
        }

        @Override
        void forceHillaHotswap(Collection<String> changedFiles) {
            hillaHotswapInvocations.incrementAndGet();
        }

        void start() {
            CompletableFuture.runAsync(this);
        }

        TestEndpointsWatcher matchEverything() {
            this.potentialEndpoint = path -> true;
            this.deriveClassName = path -> Optional.of("com.example.Endpoint");
            this.fileContainsEndpointUsedClasses = (path, strings) -> true;
            return this;
        }

        TestEndpointsWatcher withServerRestart() throws Exception {
            Mockito.reset(hotReplacementContext);
            when(hotReplacementContext.doScan(anyBoolean())).thenReturn(true);
            return this;
        }

        void reset() {
            potentialEndpoint = path -> false;
            deriveClassName = path -> Optional.empty();
            fileContainsEndpointUsedClasses = (path, strings) -> false;
            classesUsedInEndpoints = Set::of;
        }

        void waitUntilFileChanged(Predicate<List<Path>> test) {
            await().atMost(10000, TimeUnit.SECONDS).until(() -> test.test(changedFiles));
        }

        public void waitForChanges(long timeout, TimeUnit unit) {
            LocalTime untilTime = LocalTime.now().plus(timeout, unit.toChronoUnit());
            await().until(() -> LocalTime.now().isAfter(untilTime));
        }
    }

    TestEndpointsWatcher createWatcher() throws Exception {
        createWatcher(Set.of());
        return watcher;
    }

    TestEndpointsWatcher createWatcher(Set<Path> endpointRelatedPaths) throws Exception {
        HotReplacementContext context = Mockito.mock(HotReplacementContext.class);
        // By default, simulate changes without server restart
        when(context.doScan(anyBoolean())).thenReturn(false);
        watcher = new TestEndpointsWatcher(context, List.of(root1, root2), endpointRelatedPaths);
        return watcher;
    }
}
