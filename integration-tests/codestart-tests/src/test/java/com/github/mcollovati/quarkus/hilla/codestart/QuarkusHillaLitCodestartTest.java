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
package com.github.mcollovati.quarkus.hilla.codestart;

import io.quarkus.devtools.testing.codestarts.QuarkusCodestartTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static io.quarkus.devtools.codestarts.quarkus.QuarkusCodestartCatalog.Language.JAVA;

public class QuarkusHillaLitCodestartTest {

    @RegisterExtension
    public static QuarkusCodestartTest codestartTest = QuarkusCodestartTest.builder()
            .languages(JAVA)
            .setupStandaloneExtensionTest("com.github.mcollovati:quarkus-hilla")
            .build();

    @Test
    void testContent() throws Throwable {
        codestartTest.checkGeneratedSource("org.acme.Application");
        codestartTest.checkGeneratedSource("org.acme.services.HelloWorldService");
        codestartTest
                .assertThatGeneratedFile(JAVA, "src/main/frontend/views/helloworld/hello-world-view.ts")
                .exists()
                .content()
                .contains("await HelloWorldService.sayHello(");
        codestartTest
                .assertThatGeneratedFile(JAVA, "src/main/frontend/views/main-layout.ts")
                .exists();
        codestartTest
                .assertThatGeneratedFile(JAVA, "src/main/frontend/routes.ts")
                .exists();
        codestartTest
                .assertThatGeneratedFile(JAVA, "pom.xml")
                .exists()
                .content()
                .contains("<artifactId>vaadin-bom</artifactId>");
        codestartTest
                .assertThatGeneratedFile(JAVA, "package.json")
                .exists()
                .content()
                .contains("@vaadin/router")
                .doesNotContain("@vaadin/hilla-file-router", "@vaadin/react-components", "@vaadin/hilla-react-signals");
        codestartTest.assertThatGeneratedFile(JAVA, "package-lock.json").exists();
        codestartTest.assertThatGeneratedFile(JAVA, "vite.config.ts").exists();
        codestartTest.assertThatGeneratedFile(JAVA, "types.d.ts").exists();
        codestartTest.assertThatGeneratedFile(JAVA, "tsconfig.json").exists();
    }

    @Test
    void buildAllProjects() throws Throwable {
        codestartTest.buildAllProjects();
    }
}
