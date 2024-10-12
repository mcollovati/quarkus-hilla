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
import java.lang.instrument.ClassDefinition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import com.vaadin.hilla.BrowserCallable;
import com.vaadin.hilla.Endpoint;
import com.vaadin.hilla.EndpointExposed;
import io.quarkus.dev.spi.HotReplacementContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Endpoint live reload watcher that detect changes in compiled class files.
 * <p></p>
 * This watcher acts after a compilation completes.
 */
class EndpointClassesWatcher extends AbstractEndpointsWatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(EndpointClassesWatcher.class);

    EndpointClassesWatcher(HotReplacementContext context, Set<Path> watchedPaths) throws IOException {
        super(context, context.getClassesDir(), watchedPaths);
    }

    @Override
    protected boolean isPotentialEndpointRelatedFile(Path file) {
        return file.toFile().getName().endsWith(".class");
    }

    @Override
    protected Optional<String> deriveClassName(Path relativePath) {
        String className =
                relativePath.toString().replace(relativePath.getFileSystem().getSeparator(), ".");
        className = className.substring(0, className.length() - ".class".length());
        return Optional.of(className);
    }

    @Override
    protected boolean fileContainsEndpointUsedClasses(Path classFile, Set<String> classesUsedInEndpoints) {
        String className = deriveClassName(classFile).orElse(null);
        if (className != null) {
            try {
                ClassDefinition definition = new ClassDefinition(
                        Thread.currentThread().getContextClassLoader().loadClass(className),
                        Files.readAllBytes(classFile));
                Class<?> definitionClass = definition.getDefinitionClass();
                if (definitionClass.isAnnotationPresent(BrowserCallable.class)
                        || definitionClass.isAnnotationPresent(Endpoint.class)
                        || definitionClass.isAnnotationPresent(EndpointExposed.class)) {
                    LOGGER.debug("The changed class {} has an endpoint annotation", className);
                    return true;
                }
            } catch (Exception ex) {
                LOGGER.debug("Cannot load changed class {} from file {}", className, classFile);
            }
        }
        return false;
    }
}
