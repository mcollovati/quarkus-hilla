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

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import dev.codex.quarkushilla.copilot.app.CopilotTestBeans;
import io.quarkus.test.QuarkusDevModeTest;
import io.quarkus.value.registry.ValueRegistry;
import io.quarkus.value.registry.ValueRegistry.RuntimeKey;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

class CopilotDevModeBuildStepTest {

    @RegisterExtension
    static final QuarkusDevModeTest config = new QuarkusDevModeTest().withApplicationRoot(jar -> jar.addClasses(
                    CopilotDevModeProbeServlet.class,
                    CopilotTestBeans.class,
                    CopilotTestBeans.AAlphabeticallyFirstHelper.class,
                    CopilotTestBeans.ZzzAppShell.class,
                    CopilotTestBeans.ApplicationScopedFlowService.class)
            .addAsResource(new StringAsset("quarkus.http.port=0\n"), "application.properties"));

    private static final RuntimeKey<URI> LOCAL_BASE_URI = RuntimeKey.key("quarkus.http.local-base-uri");

    private URI baseUri;

    @BeforeEach
    void beforeEach(ValueRegistry valueRegistry) {
        assertThat(valueRegistry.containsKey(LOCAL_BASE_URI)).isTrue();
        baseUri = valueRegistry.get(LOCAL_BASE_URI);
    }

    @Test
    void devModeBuildSteps_generateCopilotMetadataAndPreserveFlowServiceBeans() throws Exception {
        assertThat(read("/copilot-dev-mode/application-class")).isEqualTo(CopilotTestBeans.ZzzAppShell.class.getName());
        assertThat(read("/copilot-dev-mode/application-scoped-flow-service-bean"))
                .isEqualTo("true");
    }

    private String read(String path) throws IOException {
        try (Scanner scanner = new Scanner(baseUri.resolve(path).toURL().openStream(), StandardCharsets.UTF_8)) {
            scanner.useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
        }
    }
}
