/*
 * Copyright 2025 Marco Collovati, Dario Götze
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
package com.vaadin.hilla.engine;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.JarIndexer;
import org.jboss.jandex.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Temporary hack to replace AotBrowserCallableFinder during build-frontend execution.
 */
public class AotBrowserCallableFinder {

    private static final Logger LOGGER = LoggerFactory.getLogger(AotBrowserCallableFinder.class);

    private IndexView index;

    static List<Class<?>> findEndpointClasses(EngineConfiguration engineConfiguration)
            throws IOException, InterruptedException {
        EngineConfiguration configuration = EngineConfiguration.getDefault();
        IndexView compositeIndex = getOrCreateIndex(configuration);
        Set<String> browserCallables = configuration.getEndpointAnnotations().stream()
                .map(DotName::createSimple)
                .flatMap(ann -> compositeIndex.getAnnotations(ann).stream())
                .filter(instance -> instance.target().kind() == AnnotationTarget.Kind.CLASS)
                .map(instance -> instance.target().asClass().name().toString())
                .collect(Collectors.toSet());
        List<Class<?>> browserCallableClasses = new ArrayList<>();
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        for (String browserCallable : browserCallables) {
            try {
                browserCallableClasses.add(Class.forName(browserCallable, false, contextClassLoader));
                LOGGER.debug("Found browser callable {}", browserCallable);
            } catch (ClassNotFoundException e) {
                LOGGER.error(
                        "Cannot load browser callable class {} using {} class loader",
                        browserCallable,
                        contextClassLoader,
                        e);
            }
        }
        return browserCallableClasses;
    }

    private static IndexView getOrCreateIndex(EngineConfiguration configuration) throws IOException {
        Path tempFile = Files.createTempFile("jandex", "idx");
        tempFile.toFile().deleteOnExit();
        List<IndexView> indexes = new ArrayList<>();
        for (Path path : configuration.getClasspath()) {
            LOGGER.trace("Indexing {}", path);
            Indexer indexer = new Indexer();
            File file = path.toFile();
            if (file.isDirectory()) {
                Files.walkFileTree(path, new DirectoryIndexer(indexer));
                indexes.add(indexer.complete());
            } else if (file.exists() && file.getName().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                Result result = JarIndexer.createJarIndex(file, indexer, tempFile.toFile(), false, false, false);
                indexes.add(result.getIndex());
            }
        }
        return CompositeIndex.create(indexes);
    }

    private static class DirectoryIndexer extends SimpleFileVisitor<Path> {

        private final Indexer indexer;

        public DirectoryIndexer(Indexer indexer) {
            this.indexer = indexer;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            if (file.getFileName().toString().endsWith(".class")) {
                try (InputStream stream = Files.newInputStream(file)) {
                    indexer.index(stream);
                }
            }
            return FileVisitResult.CONTINUE;
        }
    }
}
