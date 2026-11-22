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
package com.github.mcollovati.quarkus.hilla.devmode;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.quarkus.maven.it.RunAndCheckMojoTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Starts sample projects with {@code quarkus:dev}, changes a source file and checks that the application is
 * recompiled and restarted.
 * <p>
 * Vaadin Copilot 25.3 bundles a Kotlin compiler whose {@code org.jetbrains.kotlin} packages are not relocated.
 * Quarkus' Kotlin recompilation then picks up Copilot's compiler classes and the restart fails with
 * {@code NoSuchFieldError}. The Kotlin test with Copilot is skipped while the Copilot jar on the class path still
 * contains these classes, and runs as soon as a fixed Copilot is used. Any other failure still fails the test.
 *
 * @see <a href="https://github.com/mcollovati/quarkus-hilla/issues/2434">#2434</a>
 * @see <a href="https://github.com/vaadin/copilot/issues/160">vaadin/copilot#160</a>
 */
@Tag("development-only")
class LiveReloadWithCopilotTest extends RunAndCheckMojoTestBase {

    private static final String ENDPOINT = "/rest/greeting";
    private static final String KOTLIN_COMPILER_CLASS = "org/jetbrains/kotlin/config/CompilerConfiguration.class";
    private static final Pattern ERROR_PAGE_TITLE = Pattern.compile("<title>(.*?)</title>", Pattern.DOTALL);

    @Override
    protected int getPort() {
        return 18089;
    }

    @Override
    protected int getAllowedHeapInMb() {
        return 1024;
    }

    @Test
    void kotlinSourceChange_copilotOnClassPath_reloadsApplication() throws Exception {
        URL bundledCompiler = bundledKotlinCompiler();
        assumeTrue(
                bundledCompiler == null,
                () -> "Copilot bundles an unrelocated Kotlin compiler (vaadin/copilot#160): " + bundledCompiler);

        testDir = initProject("projects/kotlin-app", "projects/kotlin-app-with-copilot");

        run(true, copilotVersionArgs());

        assertReloadAfterChange("src/main/kotlin/org/acme/GreetingResource.kt");
    }

    @Test
    void kotlinSourceChange_copilotExcludedAsDocumented_reloadsApplication() throws Exception {
        testDir = initProject("projects/kotlin-app-copilot-excluded", "projects/kotlin-app-copilot-excluded-run");

        run(true);

        assertReloadAfterChange("src/main/kotlin/org/acme/GreetingResource.kt");
    }

    @Test
    void javaSourceChange_copilotOnClassPath_reloadsApplication() throws Exception {
        testDir = initProject("projects/java-app", "projects/java-app-with-copilot");

        run(true, copilotVersionArgs());

        assertReloadAfterChange("src/main/java/org/acme/GreetingResource.java");
    }

    /**
     * Returns the location of the Kotlin compiler classes bundled in the Copilot jar of the test class path, or
     * {@code null} if Copilot does not contain them. The test class path uses the same Copilot version as the sample
     * projects, including the {@code copilot.version} override.
     */
    private static URL bundledKotlinCompiler() throws IOException {
        Enumeration<URL> resources =
                LiveReloadWithCopilotTest.class.getClassLoader().getResources(KOTLIN_COMPILER_CLASS);
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            if (resource.toString().contains("/copilot-")) {
                return resource;
            }
        }
        return null;
    }

    /**
     * Forwards {@code -Dcopilot.version=...} to the sample project, so the tests can run against an unreleased
     * Copilot build installed in the local Maven repository.
     */
    private static String[] copilotVersionArgs() {
        String version = System.getProperty("copilot.version", "").strip();
        return version.isEmpty() ? new String[0] : new String[] {"-Dcopilot.version=" + version};
    }

    private void assertReloadAfterChange(String sourceFile) throws Exception {
        assertThat(devModeClient.getHttpResponse(ENDPOINT)).isEqualTo("initial");

        filter(new File(testDir, sourceFile), Map.of("\"initial\"", "\"changed\""));

        // A failed restart is not logged: Quarkus only reports it in the error page of the request that triggered it
        AtomicReference<String> response = new AtomicReference<>();
        await().pollDelay(Duration.ofSeconds(1)).atMost(Duration.ofMinutes(3)).until(() -> {
            response.set(devModeClient.getHttpResponse(ENDPOINT, true));
            return !"initial".equals(response.get());
        });
        if (!"changed".equals(response.get())) {
            fail("Reload after changing %s failed: %s", sourceFile, summarize(response.get()));
        }
    }

    private static String summarize(String response) {
        if (response == null) {
            return "no response";
        }
        Matcher title = ERROR_PAGE_TITLE.matcher(response);
        if (title.find()) {
            return title.group(1).strip();
        }
        return response.length() > 300 ? response.substring(0, 300) + "..." : response;
    }
}
