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

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import com.vaadin.flow.server.RouteRegistry;
import com.vaadin.flow.server.ServiceInitEvent;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.configuration.ConfigurationException;
import org.jboss.logging.Logger;

/**
 * Reports statically provable differences between route annotations and
 * Quarkus HTTP permission configuration when a Vaadin service starts.
 */
@Singleton
public class AnnotationConfigMismatchDiagnostics {

    private static final Logger LOGGER = Logger.getLogger(AnnotationConfigMismatchDiagnostics.class);

    private final Instance<VaadinSecurityRuntimeConfiguration> configuration;
    private final List<RouteRegistry> routeRegistries = new ArrayList<>();
    private VaadinSecurityRuntimeConfiguration runtimeConfiguration;
    private int validatedRegistryCount;

    @Inject
    public AnnotationConfigMismatchDiagnostics(Instance<VaadinSecurityRuntimeConfiguration> configuration) {
        this.configuration = configuration;
    }

    synchronized void captureRoutes(@Observes ServiceInitEvent event) {
        routeRegistries.add(event.getSource().getRouter().getRegistry());
        if (runtimeConfiguration != null) {
            validatePendingRegistries();
        }
    }

    synchronized void validate(@Observes StartupEvent event) {
        runtimeConfiguration = configuration.get();
        validatePendingRegistries();
    }

    private void validatePendingRegistries() {
        List<RouteRegistry> pendingRegistries =
                List.copyOf(routeRegistries.subList(validatedRegistryCount, routeRegistries.size()));
        validatedRegistryCount = routeRegistries.size();
        if (runtimeConfiguration.annotationConfigMismatch() == VaadinSecurityRuntimeConfig.AnnotationConfigMismatch.OFF
                || pendingRegistries.isEmpty()) {
            return;
        }

        List<AnnotationConfigMismatchAnalyzer.Diagnostic> diagnostics = new ArrayList<>();
        int routeCount = 0;
        AnnotationConfigMismatchAnalyzer analyzer = new AnnotationConfigMismatchAnalyzer(
                runtimeConfiguration.permissions(),
                runtimeConfiguration.rolePolicies(),
                runtimeConfiguration.rootPath());
        for (RouteRegistry registry : pendingRegistries) {
            try {
                routeCount += registry.getRegisteredRoutes().size();
                diagnostics.addAll(analyzer.analyze(registry).diagnostics());
            } catch (RuntimeException exception) {
                LOGGER.warnf(
                        exception,
                        "Static Vaadin annotation/configuration comparison failed for a route registry; "
                                + "runtime authorization remains unchanged");
            }
        }
        diagnostics = diagnostics.stream()
                .distinct()
                .sorted(Comparator.comparing(AnnotationConfigMismatchAnalyzer.Diagnostic::path)
                        .thenComparing(diagnostic -> diagnostic.kind().name())
                        .thenComparing(AnnotationConfigMismatchAnalyzer.Diagnostic::message))
                .toList();
        LOGGER.debugf(
                "Compared %d Vaadin routes with Quarkus HTTP permissions and found %d diagnostics",
                routeCount, diagnostics.size());

        for (AnnotationConfigMismatchAnalyzer.Diagnostic diagnostic : diagnostics) {
            LOGGER.warnf(
                    "Vaadin security annotation/configuration %s: %s",
                    diagnostic.kind().name().toLowerCase(), diagnostic.logMessage());
        }

        List<AnnotationConfigMismatchAnalyzer.Diagnostic> mismatches = diagnostics.stream()
                .filter(diagnostic -> diagnostic.kind() == AnnotationConfigMismatchAnalyzer.Kind.MISMATCH)
                .toList();
        if (runtimeConfiguration.annotationConfigMismatch() == VaadinSecurityRuntimeConfig.AnnotationConfigMismatch.FAIL
                && !mismatches.isEmpty()) {
            String details = mismatches.stream()
                    .map(AnnotationConfigMismatchAnalyzer.Diagnostic::logMessage)
                    .collect(Collectors.joining(System.lineSeparator() + " - ", " - ", ""));
            throw new ConfigurationException(
                    "Vaadin route annotations and Quarkus HTTP permissions differ:" + System.lineSeparator() + details);
        }
    }
}
