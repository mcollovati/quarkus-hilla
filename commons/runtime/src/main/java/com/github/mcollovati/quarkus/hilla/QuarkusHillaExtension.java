/*
 * Copyright 2023 Marco Collovati, Dario Götze
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

import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;

import com.vaadin.flow.internal.UsageStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class QuarkusHillaExtension {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuarkusHillaExtension.class);

    /**
     * Memoized Quarkus-Hilla extension version. null if not yet calculated,
     * empty string if Vaadin is not present on the classpath.
     */
    private static String version;

    private QuarkusHillaExtension() {}

    /**
     * Returns the Quarkus-Hilla extension version string, e.g., {@code "2.0.0"}.
     *
     * @return the extension version or {@link Optional#empty()} if unavailable.
     */
    public static Optional<String> getVersion() {
        // thread-safe: in the worst case version may be computed multiple
        // times by concurrent threads. Unsafe-publish is OK since String is
        // immutable and thread-safe.
        if (version == null) {
            try (final InputStream vaadinPomProperties = Thread.currentThread()
                    .getContextClassLoader()
                    .getResourceAsStream("META-INF/maven/com.github.mcollovati/quarkus-hilla-commons/pom.properties")) {
                if (vaadinPomProperties != null) {
                    final Properties properties = new Properties();
                    properties.load(vaadinPomProperties);
                    version = properties.getProperty("version", "");
                } else {
                    LOGGER.info("Unable to determine Quarkus-Hilla version. "
                            + "No META-INF/maven/com.github.mcollovati/quarkus-hilla-commons/pom.properties found");
                    version = "";
                }
            } catch (Exception e) {
                LOGGER.error("Unable to determine Quarkus-Hilla version", e);
                version = "";
            }
        }

        return version.isEmpty() ? Optional.empty() : Optional.of(version);
    }

    /**
     * Marks the extension as used in Vaadin usage statistics.
     */
    static void markUsed() {
        UsageStatistics.markAsUsed(
                "mcollovati/quarkus-hilla", QuarkusHillaExtension.getVersion().orElse("-"));
    }
}
