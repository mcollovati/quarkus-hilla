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
package com.github.mcollovati.quarkus.hilla.deployment.security;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class FileRoutesManifestClassifierTest {

    @TempDir
    Path frontendDirectory;

    @Test
    void defaultReactRouterWithHillaViewExpectsManifest() throws Exception {
        Path views = Files.createDirectories(frontendDirectory.resolve("views"));
        Files.writeString(views.resolve("DashboardView.tsx"), "export default function DashboardView() {}");

        assertThat(classify(Optional.of(true))).isTrue();
    }

    @Test
    void hybridCustomRouterExpectsManifest() throws Exception {
        Files.writeString(
                frontendDirectory.resolve("routes.tsx"),
                "export const routes = [customRoute, ...withFileRoutes(fileRoutes)];");

        assertThat(classify(Optional.of(true))).isTrue();
    }

    @Test
    void hybridCustomRouterWithWhitespaceBeforeCallExpectsManifest() throws Exception {
        Files.writeString(
                frontendDirectory.resolve("routes.tsx"),
                "export const routes = new RouterConfigurationBuilder().withFileRoutes\n  (fileRoutes).build();");

        assertThat(classify(Optional.of(true))).isTrue();
    }

    @Test
    void pureCustomReactRouterCompositionDoesNotExpectManifest() throws Exception {
        Files.writeString(
                frontendDirectory.resolve("routes.tsx"), "export const routes = withReactRoutes(customRoutes);");

        assertThat(classify(Optional.of(true))).isFalse();
    }

    @Test
    void pureCustomReactRouterDoesNotExpectManifest() throws Exception {
        Path views = Files.createDirectories(frontendDirectory.resolve("views"));
        Files.writeString(views.resolve("DashboardView.tsx"), "export default function DashboardView() {}");
        Files.writeString(
                frontendDirectory.resolve("routes.tsx"),
                "export const router = createBrowserRouter([{ path: '/', element: <Home /> }]);");

        assertThat(classify(Optional.of(true))).isFalse();
    }

    @Test
    void commentedFileRoutesCompositionDoesNotMakeCustomRouterHybrid() throws Exception {
        Files.writeString(
                frontendDirectory.resolve("routes.tsx"),
                "// withFileRoutes(fileRoutes)\nexport const router = createBrowserRouter(customRoutes);");

        assertThat(classify(Optional.of(true))).isFalse();
    }

    @Test
    void litRouterDoesNotExpectManifest() throws Exception {
        Files.writeString(frontendDirectory.resolve("index.ts"), "import { Router } from '@vaadin/router';");

        assertThat(classify(Optional.empty())).isFalse();
    }

    private boolean classify(Optional<Boolean> reactEnabledOverride) {
        return FileRoutesManifestClassifier.isManifestExpected(frontendDirectory.toFile(), reactEnabledOverride);
    }
}
