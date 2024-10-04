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
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.visitor.NodeFinderVisitor;
import com.vaadin.hilla.BrowserCallable;
import com.vaadin.hilla.Endpoint;
import com.vaadin.hilla.EndpointExposed;
import io.quarkus.dev.spi.HotReplacementContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Endpoint live reload watcher that detect changes in source code.
 * <p></p>
 * This watcher acts every time a source file is saved.
 */
class EndpointSourcesWatcher extends AbstractEndpointsWatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(EndpointSourcesWatcher.class);

    EndpointSourcesWatcher(HotReplacementContext context, Set<Path> watchedPaths) throws IOException {
        super(context, context.getSourcesDir(), watchedPaths);
    }

    @Override
    protected boolean isPotentialEndpointRelatedFile(Path file) {
        return file.toFile().getName().endsWith(".java");
    }

    @Override
    protected Optional<String> deriveClassName(Path relativePath) {
        String className =
                relativePath.toString().replace(relativePath.getFileSystem().getSeparator(), ".");
        className = className.substring(0, className.length() - ".java".length());
        return Optional.of(className);
    }

    protected boolean fileContainsEndpointUsedClasses(Path classFile, Set<String> classesUsedInEndpoints) {
        ParseResult<CompilationUnit> parseResult;
        try {
            parseResult = new JavaParser(
                            new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE))
                    .parse(classFile.toAbsolutePath());
        } catch (IOException e) {
            LOGGER.debug("Skipping unparsable Java file {}.", classFile, e);
            return false;
        }
        TypeDeclaration<?> usedType = parseResult
                .getResult()
                .map(unit -> {
                    NodeFinderVisitor visitor =
                            new NodeFinderVisitor(((node, range) -> node instanceof TypeDeclaration<?> typeDeclaration
                                    && (getFullyQualifiedName(typeDeclaration)
                                                    .map(classesUsedInEndpoints::contains)
                                                    .orElse(false)
                                            || typeDeclaration.isAnnotationPresent(BrowserCallable.class)
                                            || typeDeclaration.isAnnotationPresent(Endpoint.class)
                                            || typeDeclaration.isAnnotationPresent(EndpointExposed.class))));
                    unit.accept(visitor, null);
                    return (TypeDeclaration<?>) visitor.getSelectedNode();
                })
                .orElse(null);
        if (usedType != null) {
            LOGGER.debug(
                    "At least one class [{}] in the changed source file {} is used in an endpoint",
                    usedType.getNameAsString(),
                    classFile);
        }
        return usedType != null;
    }

    @SuppressWarnings("unchecked")
    private static Optional<String> getFullyQualifiedName(TypeDeclaration<?> type) {
        if (type.isTopLevelType()) {
            return type.getFullyQualifiedName();
        }
        return type.findAncestor(TypeDeclaration.class)
                .map(td -> (TypeDeclaration<?>) td)
                .flatMap(td -> td.getFullyQualifiedName().map(fqn -> fqn + "$" + type.getNameAsString()));
    }
}
