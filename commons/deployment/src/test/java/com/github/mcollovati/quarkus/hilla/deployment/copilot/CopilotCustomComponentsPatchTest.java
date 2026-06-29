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

import java.lang.reflect.Proxy;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import io.quarkus.test.QuarkusExtensionTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

class CopilotCustomComponentsPatchTest {

    private static final String HIDDEN_COMPONENT = "dev.codex.quarkushilla.hidden.HiddenComponent";

    @RegisterExtension
    static final QuarkusExtensionTest config = CopilotQuarkusIntegrationTestSupport.extensionTest()
            .setArchiveProducer(CopilotQuarkusIntegrationTestSupport::rootArchive);

    @Test
    void customComponentsIsCustomComponent_loadsClassesFromThreadContextClassLoader() throws Exception {
        Path outputDirectory = compileHiddenComponent();
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();

        try (URLClassLoader hiddenClassLoader =
                new URLClassLoader(new java.net.URL[] {outputDirectory.toUri().toURL()}, null)) {
            Class<?> hiddenComponent = hiddenClassLoader.loadClass(HIDDEN_COMPONENT);
            registerCustomComponent(hiddenComponent);

            Thread.currentThread().setContextClassLoader(hiddenClassLoader);

            Class<?> customComponents = Class.forName("com.vaadin.copilot.customcomponent.CustomComponents");
            boolean customComponent = (boolean) customComponents
                    .getMethod("isCustomComponent", String.class)
                    .invoke(null, HIDDEN_COMPONENT);

            assertThat(customComponent).isTrue();
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    private static Path compileHiddenComponent() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).isNotNull();

        Path sourceDirectory = Files.createTempDirectory("copilot-hidden-component-src");
        Path outputDirectory = Files.createTempDirectory("copilot-hidden-component-classes");
        Path sourceFile = sourceDirectory.resolve("dev/codex/quarkushilla/hidden/HiddenComponent.java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, """
                package dev.codex.quarkushilla.hidden;

                public class HiddenComponent {
                }
                """);

        int result = compiler.run(null, null, null, "-d", outputDirectory.toString(), sourceFile.toString());
        assertThat(result).isZero();
        return outputDirectory;
    }

    private static void registerCustomComponent(Class<?> componentClass) throws Exception {
        Class<?> customComponentType = Class.forName("com.vaadin.copilot.customcomponent.CustomComponent");
        Object customComponent = Proxy.newProxyInstance(
                customComponentType.getClassLoader(),
                new Class<?>[] {customComponentType},
                (proxy, method, args) -> switch (method.getName()) {
                    case "componentClass" -> componentClass;
                    case "getType" -> null;
                    case "litTemplate" -> false;
                    case "getChildAddableMethods" -> List.of();
                    case "toString" -> "HiddenComponentCustomComponent";
                    default -> null;
                });

        Class<?> customComponents = Class.forName("com.vaadin.copilot.customcomponent.CustomComponents");
        java.lang.reflect.Method put = customComponents.getDeclaredMethod("put", Class.class, Supplier.class);
        put.setAccessible(true);
        put.invoke(null, componentClass, (Supplier<?>) () -> customComponent);
    }
}
