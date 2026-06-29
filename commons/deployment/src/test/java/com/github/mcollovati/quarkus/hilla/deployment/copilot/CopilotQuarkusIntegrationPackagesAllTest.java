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

import dev.codex.quarkushilla.copilot.dependency.DependencyTestBeans;
import io.quarkus.test.QuarkusExtensionTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

class CopilotQuarkusIntegrationPackagesAllTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = CopilotQuarkusIntegrationTestSupport.extensionTest()
            .overrideConfigKey("vaadin.copilot.flow-services.packages", "all")
            .setArchiveProducer(CopilotQuarkusIntegrationTestSupport::rootArchive)
            .addAdditionalDependency(CopilotQuarkusIntegrationTestSupport.dependencyArchive());

    @Test
    void flowServices_allPackages_includesBeansFromAdditionalApplicationArchives() {
        assertThat(CopilotQuarkusIntegrationTestSupport.flowServiceMethods())
                .contains(method(DependencyTestBeans.DependencyApplicationScopedService.class, "dependencyMethod"));
    }

    private static String method(Class<?> serviceClass, String methodName) {
        return CopilotQuarkusIntegrationTestSupport.methodId(serviceClass, methodName);
    }
}
