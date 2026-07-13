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

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.inject.Instance;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.router.RouteData;
import com.vaadin.flow.router.Router;
import com.vaadin.flow.server.RouteRegistry;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinService;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.vertx.http.runtime.PolicyConfig;
import io.quarkus.vertx.http.runtime.PolicyMappingConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnnotationConfigMismatchDiagnosticsTest {

    @Test
    void validate_off_skipsAnalysis() {
        AnnotationConfigMismatchDiagnostics diagnostics =
                diagnostics(Map.of(), VaadinSecurityRuntimeConfig.AnnotationConfigMismatch.OFF);

        assertDoesNotThrow(() -> diagnostics.validate(null));
    }

    @Test
    void validate_warn_doesNotChangeEnforcement() {
        AnnotationConfigMismatchDiagnostics diagnostics =
                diagnostics(VaadinSecurityRuntimeConfig.AnnotationConfigMismatch.WARN, "custom-policy");

        diagnostics.captureRoutes(event());
        assertDoesNotThrow(() -> diagnostics.validate(null));
    }

    @Test
    void validate_fail_rejectsOnlyProvenMismatch() {
        AnnotationConfigMismatchDiagnostics mismatch =
                diagnostics(VaadinSecurityRuntimeConfig.AnnotationConfigMismatch.FAIL, "permit");
        AnnotationConfigMismatchDiagnostics opaque =
                diagnostics(VaadinSecurityRuntimeConfig.AnnotationConfigMismatch.FAIL, "custom-policy");

        mismatch.captureRoutes(event());
        opaque.captureRoutes(event());
        ConfigurationException exception = assertThrows(ConfigurationException.class, () -> mismatch.validate(null));
        assertTrue(exception.getMessage().contains("/user"));
        assertTrue(exception.getMessage().contains("conjunction"));
        assertDoesNotThrow(() -> opaque.validate(null));
    }

    @Test
    void captureRoutes_afterRuntimeStartup_validatesLazyService() {
        AnnotationConfigMismatchDiagnostics diagnostics =
                diagnostics(VaadinSecurityRuntimeConfig.AnnotationConfigMismatch.FAIL, "permit");
        diagnostics.validate(null);

        assertThrows(ConfigurationException.class, () -> diagnostics.captureRoutes(event()));
    }

    @Test
    void validate_warn_registryAnalysisFailureDoesNotAbortStartup() {
        RouteRegistry registry = mock(RouteRegistry.class);
        when(registry.getRegisteredRoutes()).thenThrow(new IllegalStateException("broken registry"));
        AnnotationConfigMismatchDiagnostics diagnostics =
                diagnostics(Map.of(), VaadinSecurityRuntimeConfig.AnnotationConfigMismatch.WARN);

        diagnostics.captureRoutes(event(registry));

        assertDoesNotThrow(() -> diagnostics.validate(null));
    }

    private static AnnotationConfigMismatchDiagnostics diagnostics(
            VaadinSecurityRuntimeConfig.AnnotationConfigMismatch mode, String policyName) {
        PolicyMappingConfig permission = mock(PolicyMappingConfig.class);
        when(permission.enabled()).thenReturn(Optional.empty());
        when(permission.policy()).thenReturn(policyName);
        when(permission.paths()).thenReturn(Optional.of(List.of("/user")));
        when(permission.methods()).thenReturn(Optional.empty());
        when(permission.appliesTo()).thenReturn(PolicyMappingConfig.AppliesTo.ALL);

        return diagnostics(Map.of("route", permission), mode);
    }

    private static AnnotationConfigMismatchDiagnostics diagnostics(
            Map<String, PolicyMappingConfig> permissions, VaadinSecurityRuntimeConfig.AnnotationConfigMismatch mode) {
        @SuppressWarnings("unchecked")
        Instance<VaadinSecurityRuntimeConfiguration> configuration = mock(Instance.class);
        when(configuration.get())
                .thenReturn(
                        new VaadinSecurityRuntimeConfiguration(permissions, Map.<String, PolicyConfig>of(), "/", mode));
        return new AnnotationConfigMismatchDiagnostics(configuration);
    }

    private static ServiceInitEvent event() {
        RouteRegistry registry = mock(RouteRegistry.class);
        RouteData route =
                new RouteData(new ArrayList<>(), "user", new LinkedHashMap<>(), UserView.class, new ArrayList<>());
        when(registry.getRegisteredRoutes()).thenReturn(List.of(route));
        return event(registry);
    }

    private static ServiceInitEvent event(RouteRegistry registry) {
        Router router = mock(Router.class);
        when(router.getRegistry()).thenReturn(registry);
        VaadinService service = mock(VaadinService.class);
        when(service.getRouter()).thenReturn(router);
        return new ServiceInitEvent(service);
    }

    @Tag("user-view")
    @RolesAllowed("USER")
    static class UserView extends Component {}
}
