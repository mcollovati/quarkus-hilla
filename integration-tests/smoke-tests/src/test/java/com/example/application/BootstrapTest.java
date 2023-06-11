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
package com.example.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.mcollovati.quarkus.testing.HillaCleaner;
import com.github.mcollovati.quarkus.testing.HillaFrontendGenerated;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(HillaCleaner.class)
@Tag("development-only")
class BootstrapTest {

    @HillaFrontendGenerated
    Path frontendGeneratedFolder;

    @Test
    void devMode_applicationStart_endpointsGenerated() {
        assertThat(frontendGeneratedFolder).isDirectory().exists();
        Awaitility.await().atMost(120, TimeUnit.SECONDS).untilAsserted(() -> assertThat(frontendGeneratedFolder)
                .isDirectoryContaining(fileWithName("endpoints.ts"))
                .isDirectoryContaining(fileWithName("vaadin.ts"))
                .isDirectoryContaining(fileWithName("theme.js"))
                .isDirectoryContaining(fileWithName("UserInfoEndpoint.ts"))
                .isDirectoryContaining(fileWithName("HelloWorldEndpoint.ts"))
                .isDirectoryRecursivelyContaining("glob:**/entities/User*.ts"));
    }

    private static Predicate<Path> fileWithName(String expectedFileName) {
        return path -> path.getFileName().toString().equals(expectedFileName);
    }
}
