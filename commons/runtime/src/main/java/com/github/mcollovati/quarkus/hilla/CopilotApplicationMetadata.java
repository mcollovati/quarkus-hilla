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
package com.github.mcollovati.quarkus.hilla;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Build-time application metadata used by the Copilot runtime bridge.
 */
public final class CopilotApplicationMetadata {

    public static final String RESOURCE = "META-INF/quarkus-hilla/copilot-application-metadata.properties";

    private static final String APPLICATION_CLASS = "applicationClass";
    private static final String APPLICATION_CLASSES = "applicationClasses";

    private final String applicationClassName;
    private final Set<String> applicationClassNames;

    private CopilotApplicationMetadata(String applicationClassName, Set<String> applicationClassNames) {
        this.applicationClassName = applicationClassName;
        this.applicationClassNames = Set.copyOf(applicationClassNames);
    }

    public static CopilotApplicationMetadata empty() {
        return new CopilotApplicationMetadata("", Set.of());
    }

    public static CopilotApplicationMetadata of(String applicationClassName, Set<String> applicationClassNames) {
        return new CopilotApplicationMetadata(
                Objects.requireNonNullElse(applicationClassName, ""), new LinkedHashSet<>(applicationClassNames));
    }

    public static CopilotApplicationMetadata load() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = CopilotApplicationMetadata.class.getClassLoader();
        }
        try (InputStream input = classLoader.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                return empty();
            }
            Properties properties = new Properties();
            properties.load(input);
            Set<String> classes = split(properties.getProperty(APPLICATION_CLASSES));
            return of(properties.getProperty(APPLICATION_CLASS, ""), classes);
        } catch (IOException e) {
            return empty();
        }
    }

    public byte[] toResourceBytes() {
        String content = APPLICATION_CLASS + "=" + applicationClassName + "\n" + APPLICATION_CLASSES + "="
                + applicationClassNames.stream().sorted().collect(Collectors.joining(",")) + "\n";
        return content.getBytes(StandardCharsets.UTF_8);
    }

    public Optional<String> applicationClassName() {
        return Optional.ofNullable(applicationClassName).filter(value -> !value.isBlank());
    }

    public Set<String> applicationClassNames() {
        return applicationClassNames;
    }

    public boolean isApplicationClass(Class<?> type) {
        return type != null && applicationClassNames.contains(type.getName());
    }

    private static Set<String> split(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
