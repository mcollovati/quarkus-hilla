/*
 * Copyright 2024 Marco Collovati, Dario Götze
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
package com.github.mcollovati.quarkus.hilla.deployment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import com.vaadin.flow.internal.FrontendUtils;
import io.quarkus.bootstrap.model.ApplicationModelBuilder;
import io.quarkus.bootstrap.model.DefaultApplicationModel;
import io.quarkus.bootstrap.prebuild.CodeGenException;
import io.quarkus.bootstrap.workspace.WorkspaceModule;
import io.quarkus.bootstrap.workspace.WorkspaceModuleId;
import io.quarkus.deployment.CodeGenContext;
import io.quarkus.maven.dependency.ResolvedDependencyBuilder;
import io.smallrye.config.PropertiesConfigSource;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

import static com.github.mcollovati.quarkus.hilla.QuarkusEndpointConfiguration.VAADIN_ENDPOINT_PREFIX;

class TypescriptClientCodeGenProviderTest {

    final Properties configProperties = new Properties();
    final TypescriptClientCodeGenProvider provider = new TypescriptClientCodeGenProvider();
    DefaultApplicationModel appModel;

    @TempDir
    Path projectDir;

    @BeforeEach
    void setUp() {
        appModel = new ApplicationModelBuilder()
                .setAppArtifact(ResolvedDependencyBuilder.newInstance()
                        .setWorkspaceModule(WorkspaceModule.builder()
                                .setModuleId(WorkspaceModuleId.of("org.acme", "app", "1.0"))
                                .setModuleDir(projectDir)
                                .build()))
                .build();
    }

    private Config buildConfig() {
        return ConfigProviderResolver.instance()
                .getBuilder()
                .withSources(new PropertiesConfigSource(configProperties, "test"))
                .build();
    }

    @Test
    void init_legacyFolder_setInputDirectory() throws IOException {
        Path frontendFolder = createFolder("frontend");

        provider.init(appModel, Map.of());

        assertThat(provider.getInputDirectory()).isEqualTo(frontendFolder);
    }

    @Test
    void init_defaultFrontendFolder_setInputDirectory() throws IOException {
        Path frontendFolder = createFolder("src", "main", "frontend");

        provider.init(appModel, Map.of());

        assertThat(provider.getInputDirectory()).isEqualTo(frontendFolder);
    }

    @Test
    void init_legacyAndDefaultFrontendFolders_setDefaultAsInputDirectory() throws IOException {
        createFolder("frontend");
        Path frontendFolder = createFolder("src", "main", "frontend");

        provider.init(appModel, Map.of());

        assertThat(provider.getInputDirectory()).isEqualTo(frontendFolder);
    }

    @Test
    @Disabled("Not sure about this test")
    void init_customFrontendFolder_setInputDirectory() throws IOException {
        String originalValue = System.getProperty(FrontendUtils.PARAM_FRONTEND_DIR);
        String customFrontend = "custom/frontend";

        createFolder("src", "main", "frontend");
        Path customFrontendFolder = createFolder("custom", "frontend");

        System.setProperty(FrontendUtils.PARAM_FRONTEND_DIR, customFrontend);
        try {
            provider.init(appModel, Map.of());

            assertThat(provider.getInputDirectory()).isEqualTo(customFrontendFolder);
        } finally {
            if (originalValue != null) {
                System.setProperty(FrontendUtils.PARAM_FRONTEND_DIR, originalValue);
            } else {
                System.clearProperty(FrontendUtils.PARAM_FRONTEND_DIR);
            }
        }
    }

    @Test
    void init_noFrontendFolder_dontSetInputDirectory() {
        provider.init(appModel, Map.of());

        assertThat(provider.getInputDirectory()).isNull();
    }

    @Test
    void shouldRun_pathIsFile_dontRun() throws IOException {
        Path file = projectDir.resolve("file.txt");
        Files.writeString(file, "text");

        assertThat(provider.shouldRun(file, buildConfig())).isFalse();
    }

    @Test
    void shouldRun_frontendDirectoryNotExists_dontRun() {
        Path path = projectDir.resolve("frontend");

        assertThat(provider.shouldRun(path, buildConfig())).isFalse();
    }

    @Test
    void shouldRun_defaultPrefix_noCustomClient_dontRun() throws IOException {
        Path path = createFolder("frontend");
        configProperties.put(VAADIN_ENDPOINT_PREFIX, "/connect");

        assertThat(provider.shouldRun(path, buildConfig())).isFalse();
    }

    @Test
    void shouldRun_defaultPrefix_customClientWithDefaultPrefix_dontRun() throws IOException {
        Path path = createFolder("frontend");
        TypescriptClientCodeGenProvider.writeConnectClient("connect", path.resolve("connect-client.ts"));
        configProperties.put(VAADIN_ENDPOINT_PREFIX, "/connect");

        assertThat(provider.shouldRun(path, buildConfig())).isFalse();
    }

    @Test
    void shouldRun_customPrefix_customClientWithSamePrefix_dontRun() throws IOException {
        Path path = createFolder("frontend");
        TypescriptClientCodeGenProvider.writeConnectClient("my-prefix", path.resolve("connect-client.ts"));
        configProperties.put(VAADIN_ENDPOINT_PREFIX, "/my-prefix");

        assertThat(provider.shouldRun(path, buildConfig())).isFalse();
    }

    @Test
    void shouldRun_customPrefix_noCustomClient_run() throws IOException {
        Path path = createFolder("frontend");
        configProperties.put(VAADIN_ENDPOINT_PREFIX, "/my-prefix");

        assertThat(provider.shouldRun(path, buildConfig())).isTrue();
    }

    @Test
    void shouldRun_customPrefix_customClientWithDifferentPrefix_run() throws IOException {
        Path path = createFolder("frontend");
        TypescriptClientCodeGenProvider.writeConnectClient("my-prefix", path.resolve("connect-client.ts"));
        configProperties.put(VAADIN_ENDPOINT_PREFIX, "/different-prefix");

        assertThat(provider.shouldRun(path, buildConfig())).isTrue();
    }

    @Test
    void trigger_customPrefix_noCustomClientWithDifferentPrefix_clientGenerated() throws IOException, CodeGenException {
        Path frontendFolder = createFolder("frontend");
        Path outDir = createFolder("target");
        configProperties.put(VAADIN_ENDPOINT_PREFIX, "/my-prefix");

        CodeGenContext context =
                new CodeGenContext(appModel, outDir, projectDir, frontendFolder, true, buildConfig(), false);
        provider.trigger(context);

        assertConnectClientGenerated(frontendFolder, "my-prefix");
    }

    @Test
    void trigger_customPrefix_customClientWithDifferentPrefix_clientGenerated() throws IOException, CodeGenException {
        Path frontendFolder = createFolder("frontend");
        Path outDir = createFolder("target");
        TypescriptClientCodeGenProvider.writeConnectClient("my-prefix", frontendFolder.resolve("connect-client.ts"));
        configProperties.put(VAADIN_ENDPOINT_PREFIX, "/new-prefix");

        CodeGenContext context =
                new CodeGenContext(appModel, outDir, projectDir, frontendFolder, true, buildConfig(), false);
        provider.trigger(context);

        assertConnectClientGenerated(frontendFolder, "new-prefix");
    }

    @Test
    void trigger_defaultPrefix_customClientWithDifferentPrefix_clientGenerated() throws IOException, CodeGenException {
        Path frontendFolder = createFolder("frontend");
        Path outDir = createFolder("target");
        TypescriptClientCodeGenProvider.writeConnectClient("my-prefix", frontendFolder.resolve("connect-client.ts"));
        configProperties.put(VAADIN_ENDPOINT_PREFIX, "/connect");

        CodeGenContext context =
                new CodeGenContext(appModel, outDir, projectDir, frontendFolder, true, buildConfig(), false);
        provider.trigger(context);

        assertConnectClientGenerated(frontendFolder, "connect");
    }

    private static void assertConnectClientGenerated(Path frontendFolder, String expectedPrefix) {
        Path connectClient = frontendFolder.resolve("connect-client.ts");
        assertThat(connectClient).isRegularFile().content().contains(connectClientSnippet(expectedPrefix));
    }

    private Path createFolder(String first, String... others) throws IOException {
        Path frontendFolder = projectDir.resolve(Path.of(first, others));
        Files.createDirectories(frontendFolder);
        return frontendFolder;
    }

    private static String connectClientSnippet(String prefix) {
        return String.format("const client = new ConnectClient({prefix: '%s'})", prefix);
    }
}
