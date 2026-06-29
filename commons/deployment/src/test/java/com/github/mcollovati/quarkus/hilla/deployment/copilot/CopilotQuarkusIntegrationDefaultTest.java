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
package com.github.mcollovati.quarkus.hilla.deployment.copilot;

import java.util.Set;

import dev.codex.quarkushilla.copilot.app.CopilotTestBeans;
import dev.codex.quarkushilla.copilot.dependency.DependencyTestBeans;
import io.quarkus.test.QuarkusExtensionTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.mcollovati.quarkus.hilla.CopilotQuarkusIntegration;

import static org.assertj.core.api.Assertions.assertThat;

class CopilotQuarkusIntegrationDefaultTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = CopilotQuarkusIntegrationTestSupport.extensionTest()
            .setArchiveProducer(CopilotQuarkusIntegrationTestSupport::rootArchive)
            .addAdditionalDependency(CopilotQuarkusIntegrationTestSupport.dependencyArchive());

    @Test
    void flowServices_defaultServicesMode_findsScopedApplicationBeansOnly() {
        Set<String> methods = CopilotQuarkusIntegrationTestSupport.flowServiceMethods();

        assertThat(methods)
                .contains(
                        method(CopilotTestBeans.ApplicationScopedFlowService.class, "applicationMethod"),
                        method(CopilotTestBeans.SingletonFlowService.class, "singletonMethod"),
                        method(CopilotTestBeans.DependentFlowService.class, "dependentMethod"),
                        method(CopilotTestBeans.VaadinServiceScopedFlowService.class, "vaadinServiceMethod"),
                        method(CopilotTestBeans.VaadinSessionScopedFlowService.class, "vaadinSessionMethod"),
                        method(CopilotTestBeans.VaadinUiScopedFlowService.class, "vaadinUiMethod"),
                        method(CopilotTestBeans.VaadinRouteScopedFlowService.class, "vaadinRouteMethod"))
                .doesNotContain(
                        method(CopilotTestBeans.RequestScopedFlowService.class, "requestMethod"),
                        method(CopilotTestBeans.BrowserCallableEndpoint.class, "browserCallableMethod"),
                        method(CopilotTestBeans.LegacyEndpoint.class, "endpointMethod"),
                        method(DependencyTestBeans.DependencyApplicationScopedService.class, "dependencyMethod"));
    }

    @Test
    void endpoints_returnsHillaEndpointsAndFlowServicesDoNotDuplicateThem() {
        Set<String> endpoints = CopilotQuarkusIntegrationTestSupport.endpointMethods();
        Set<String> flowServices = CopilotQuarkusIntegrationTestSupport.flowServiceMethods();

        assertThat(endpoints)
                .contains(
                        method(CopilotTestBeans.BrowserCallableEndpoint.class, "browserCallableMethod"),
                        method(CopilotTestBeans.LegacyEndpoint.class, "endpointMethod"));
        assertThat(flowServices)
                .doesNotContain(
                        method(CopilotTestBeans.BrowserCallableEndpoint.class, "browserCallableMethod"),
                        method(CopilotTestBeans.LegacyEndpoint.class, "endpointMethod"));
    }

    @Test
    void springBridgePatch_routesCopilotCallsToQuarkusIntegration() {
        assertThat(CopilotQuarkusIntegrationTestSupport.springBridgeAvailable()).isTrue();
        assertThat(CopilotQuarkusIntegrationTestSupport.springBridgeFlowServiceMethods())
                .contains(method(CopilotTestBeans.ApplicationScopedFlowService.class, "applicationMethod"));
    }

    @Test
    void applicationClass_prefersAppShellConfiguratorOverAlphabeticallyEarlierClasses() {
        assertThat(CopilotQuarkusIntegration.getApplicationClass(null)).isEqualTo(CopilotTestBeans.ZzzAppShell.class);
    }

    private static String method(Class<?> serviceClass, String methodName) {
        return CopilotQuarkusIntegrationTestSupport.methodId(serviceClass, methodName);
    }
}
