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
import java.io.UncheckedIOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.vaadin.hilla.EndpointCodeGenerator;
import com.vaadin.hilla.Hotswapper;
import io.quarkus.dev.spi.HotReplacementContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

/**
 * Base class to trigger Quarkus live reload upon changes on interesting files.
 * <p></p>
 * The class registers filesystem watchers on all given root paths and subfolder. When a file changes it tries to detect potential related class names,
 * and if they are used in any Hilla endpoint, it triggers Quarkus live reload.
 * Change detection can be restricted to a smaller set of root subfolders.
 *
 * @see WatchService
 */
abstract class AbstractEndpointsWatcher implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractEndpointsWatcher.class);

    private final WatchService watchService;
    private final HotReplacementContext context;
    private final List<Path> rootPaths;
    private final Set<Path> watchedPaths;
    private final Map<Path, WatchKey> watchKeys = new HashMap<>();
    private volatile boolean running;

    /**
     * Base constructor for endpoints watcher.
     *
     * @param context Quarkus {@link HotReplacementContext} instance.
     * @param rootPaths root paths to watch for file changes.
     * @param endpointRelatedPaths paths relative to root paths that contains endpoint related code.
     * @throws IOException if a filesystem watcher cannot be registered.
     */
    AbstractEndpointsWatcher(HotReplacementContext context, List<Path> rootPaths, Set<Path> endpointRelatedPaths)
            throws IOException {
        this.context = context;
        this.rootPaths = rootPaths;
        this.watchedPaths = endpointRelatedPaths != null ? endpointRelatedPaths : Set.of();
        this.watchService = FileSystems.getDefault().newWatchService();
        rootPaths.forEach(root -> {
            if (this.watchedPaths.isEmpty()) {
                LOGGER.debug("Watching for changes in folder {}", root);
            } else {
                LOGGER.debug("Watching for changes in folder {} sub-trees {}", root, endpointRelatedPaths);
            }
            this.registerRecursive(root);
        });
    }

    private void registerRecursive(final Path root) {
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (!watchKeys.containsKey(dir)) {
                        watchKeys.put(dir, dir.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY));
                        LOGGER.trace("Registering path {} for endpoint code changes", dir);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private void unregisterRecursive(final Path root) {
        Set<Path> removedPaths = watchKeys.keySet().stream()
                .filter(path -> path.equals(root) || path.startsWith(root))
                .collect(Collectors.toSet());
        for (Path path : removedPaths) {
            watchKeys.remove(path).cancel();
        }
        removedPaths.stream().sorted().forEach(path -> LOGGER.trace("Unregistered path {}", path));
    }

    @Override
    public void run() {
        running = true;
        LOGGER.debug("Starting endpoints changes watcher");
        WatchKey key;
        try {
            while (!Thread.currentThread().isInterrupted() && running && (key = watchService.take()) != null) {
                Map<Path, String> changedFiles = computeChangedSources(key);
                Set<String> changedClasses = changedFiles.values().stream()
                        .filter(className -> !className.isEmpty())
                        .collect(Collectors.toSet());
                boolean requiresHotswap = false;
                if (!changedFiles.isEmpty()) {
                    LOGGER.trace("Searching for endpoints related class in changed files {}", changedFiles.keySet());
                    Set<String> usedClasses = collectClassesUsedInEndpoints();
                    try {
                        if (usedClasses == null) {
                            // Force a scan if we cannot get the list of changed classes
                            // This might regenerate and fix a potential invalid Open API file
                            requiresHotswap = !context.doScan(false);
                        } else if (changedClasses.stream().anyMatch(usedClasses::contains)) {
                            LOGGER.debug(
                                    "At least one of the changed classes [{}] is used in an endpoint", changedClasses);
                            requiresHotswap = !context.doScan(false);
                        } else {
                            for (var pair : changedFiles.entrySet()) {
                                Path classFile = pair.getKey();
                                if (Files.exists(classFile)
                                        && fileContainsEndpointUsedClasses(classFile, usedClasses)) {
                                    requiresHotswap = !context.doScan(false);
                                    break;
                                }
                            }
                        }
                        if (requiresHotswap) {
                            LOGGER.debug(
                                    "Server not restarted because classes replaced via instrumentation. Forcing Hilla hotswap. {}",
                                    Thread.currentThread().getContextClassLoader());
                            forceHillaHotswap(changedFiles.values());
                        }
                    } catch (Exception ex) {
                        LOGGER.debug("Endpoint live reload failed", ex);
                    }
                }
                key.reset();
            }
        } catch (InterruptedException ex) {
            stop();
            Thread.currentThread().interrupt();
        } catch (ClosedWatchServiceException ex) {
            LOGGER.trace("WatchService closed, most likely because of stop being invoked", ex);
        } catch (Exception ex) {
            LOGGER.error("Unrecoverable error. Endpoint changes watcher will be stopped", ex);
        }
        LOGGER.debug("Stopped endpoints changes watcher");
    }

    // Visible for test
    Set<String> collectClassesUsedInEndpoints() {
        try {
            return EndpointCodeGenerator.getInstance().getClassesUsedInOpenApi().orElse(Set.of());
        } catch (Exception ex) {
            LOGGER.debug("Cannot get used classes from Open API. Force scan for changes", ex);
        }
        return null;
    }

    // Visible for test
    void forceHillaHotswap(Collection<String> changedFiles) {
        Hotswapper.onHotswap(true, changedFiles.toArray(new String[0]));
    }

    private Map<Path, String> computeChangedSources(WatchKey key) {
        Set<Path> processedPaths = new HashSet<>();
        Map<Path, String> changedClasses = new HashMap<>();
        List<WatchEvent<?>> events;
        List<WatchEvent<?>> allEvents = new ArrayList<>();
        // Try to collect all close events, to prevent multiple reloads
        do {
            events = key.pollEvents();
            allEvents.addAll(events);
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // ignore
            }
        } while (!events.isEmpty());

        for (WatchEvent<?> event : allEvents) {
            Path affectedRelativePath = (Path) event.context();
            WatchEvent.Kind<?> eventKind = event.kind();
            Path parentPath = (Path) key.watchable();
            LOGGER.trace("Event {} on file {} (happened {} time(s)).", eventKind, event.context(), event.count());
            Path affectedPath = parentPath.resolve(affectedRelativePath);
            boolean isDirectory = Files.isDirectory(affectedPath) || watchKeys.containsKey(affectedPath);
            if (isDirectory) {
                if (eventKind == ENTRY_CREATE) {
                    LOGGER.debug("New directory: {}", affectedRelativePath);
                    registerRecursive(affectedPath);
                } else if (eventKind == ENTRY_DELETE) {
                    LOGGER.debug("Directory removed: {}", affectedRelativePath);
                    unregisterRecursive(affectedPath);
                }
            } else if (!processedPaths.contains(affectedRelativePath) && isPotentialEndpointRelatedFile(affectedPath)) {
                processedPaths.add(affectedRelativePath);
                LOGGER.trace("Java source file {} changed ({})", affectedRelativePath, eventKind.name());
                rootPaths.stream()
                        .filter(dir ->
                                affectedPath.startsWith(dir.toAbsolutePath().toString()))
                        .findFirst()
                        .filter(dir -> isWatchedPath(dir, affectedPath))
                        .ifPresent(
                                classPath -> changedClasses.computeIfAbsent(classPath.resolve(affectedPath), file -> {
                                    String className = deriveClassName(classPath.relativize(file))
                                            .orElse("");
                                    if (!className.isEmpty()) {
                                        LOGGER.trace(
                                                "Computed Java class name {} for file {}",
                                                className,
                                                affectedRelativePath);
                                    }
                                    return className;
                                }));
            }
        }
        return changedClasses;
    }

    /**
     * Gets if the given file should be inspected for potential Hilla endpoint related components.
     *
     * @param file the file to inspect
     * @return {@literal true} if the file should be inspected, otherwise {@literal false}.
     */
    protected abstract boolean isPotentialEndpointRelatedFile(Path file);

    /**
     * Tries to derive a top level class name from the given file.
     * @param relativePath the file to inspect.
     * @return the top level classname for the give file, or an empty optional, never {@literal null}.
     */
    protected abstract Optional<String> deriveClassName(Path relativePath);

    /**
     * Inspect the content of the give file to determine if it contains any type (class, record, interface, ...) used in any Hilla endpoint.
     * @param classFile the file to inspect
     * @param classesUsedInEndpoints a set of class names currently used by Hilla endpoints.
     * @return {@literal true} if the file relates to a type used in Hilla endpoints, otherwise {@literal false}.
     */
    protected abstract boolean fileContainsEndpointUsedClasses(Path classFile, Set<String> classesUsedInEndpoints);

    private boolean isWatchedPath(Path rootPath, Path relativePath) {
        if (watchedPaths.isEmpty()) {
            return true;
        }
        Path relativeToSourceRoot = rootPath.relativize(relativePath);
        if (watchedPaths.stream().anyMatch(relativeToSourceRoot::startsWith)) {
            LOGGER.trace("{} is in a watched path", relativeToSourceRoot);
            return true;
        }
        LOGGER.trace("Ignoring changes to {} because it is not in a watched path", relativeToSourceRoot);
        return false;
    }

    void stop() {
        try {
            running = false;
            watchService.close();
        } catch (IOException e) {
            LOGGER.debug("Failure happen stopping endpoints source code watcher", e);
        }
    }
}
