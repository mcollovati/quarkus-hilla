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

import java.nio.file.Files;

import io.quarkus.devtools.project.BuildTool;
import io.quarkus.devtools.testing.codestarts.QuarkusCodestartTest;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static io.quarkus.devtools.codestarts.quarkus.QuarkusCodestartCatalog.Language.JAVA;
import static org.assertj.core.api.Assertions.assertThat;

public class QuarkusHillaReactCodestartTest {

    @RegisterExtension
    public static QuarkusCodestartTest codestartTest = QuarkusCodestartTest.builder()
            .languages(JAVA)
            .buildTool(BuildTool.MAVEN)
            .setupStandaloneExtensionTest("com.github.mcollovati:quarkus-hilla-react")
            .build();

    @Test
    void testContent() throws Throwable {
        codestartTest.checkGeneratedSource("org.acme.Application");
        codestartTest.checkGeneratedSource("org.acme.services.HelloWorldService");
        codestartTest
                .assertThatGeneratedFile(JAVA, "src/main/frontend/views/@index.tsx")
                .exists()
                .content()
                .contains("await HelloWorldService.sayHello(");
        codestartTest
                .assertThatGeneratedFile(JAVA, "src/main/frontend/views/@layout.tsx")
                .exists();
        codestartTest
                .assertThatGeneratedFile(JAVA, "pom.xml")
                .exists()
                .content()
                .contains("<artifactId>vaadin-bom</artifactId>");
        codestartTest
                .assertThatGeneratedFile(JAVA, "package.json")
                .exists()
                .satisfies(path -> assertThat(new JsonObject(Files.readString(path))
                                .getJsonObject("dependencies")
                                .fieldNames())
                        .contains(
                                "@vaadin/hilla-file-router", "@vaadin/react-components", "@vaadin/hilla-react-signals")
                        .doesNotContain("@vaadin/router"));
        codestartTest.assertThatGeneratedFile(JAVA, "package-lock.json").exists();
        codestartTest.assertThatGeneratedFile(JAVA, "vite.config.ts").exists();
        codestartTest.assertThatGeneratedFile(JAVA, "hilla-vite-compat.ts").doesNotExist();
        codestartTest.assertThatGeneratedFile(JAVA, "types.d.ts").exists();
        codestartTest.assertThatGeneratedFile(JAVA, "tsconfig.json").exists();
    }

    @Test
    void buildAllProjects() throws Throwable {
        codestartTest.buildAllProjects();
    }
}
