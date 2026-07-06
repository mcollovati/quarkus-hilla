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
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.deployment.builditem.BytecodeTransformerBuildItem;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import com.github.mcollovati.quarkus.hilla.deployment.asm.OffendingMethodCallsReplacer;

import static org.assertj.core.api.Assertions.assertThat;

class CopilotCustomComponentsPatchTest {

    private static final String HIDDEN_COMPONENT = "dev.codex.quarkushilla.hidden.HiddenComponent";

    @Test
    void springBridgePatch_routesCallsToQuarkusIntegration() throws Exception {
        Class<?> springBridge = transformedCopilotClass("com.vaadin.copilot.SpringBridge");
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(springBridge.getClassLoader());

            Object versionInfo = springBridge.getMethod("getVersionInfo").invoke(null);

            assertThat(versionInfo.getClass().getMethod("springBootVersion").invoke(versionInfo))
                    .isEqualTo("Quarkus Hilla");
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    void customComponentsIsCustomComponent_loadsClassesFromThreadContextClassLoader() throws Exception {
        Path outputDirectory = compileHiddenComponent();
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();

        try (URLClassLoader hiddenClassLoader =
                new URLClassLoader(new java.net.URL[] {outputDirectory.toUri().toURL()}, null)) {
            Class<?> hiddenComponent = hiddenClassLoader.loadClass(HIDDEN_COMPONENT);
            Class<?> customComponents = transformedCopilotClass("com.vaadin.copilot.customcomponent.CustomComponents");
            registerCustomComponent(customComponents, hiddenComponent);

            Thread.currentThread().setContextClassLoader(hiddenClassLoader);

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

    private static void registerCustomComponent(Class<?> customComponents, Class<?> componentClass) throws Exception {
        Class<?> customComponentType = Class.forName(
                "com.vaadin.copilot.customcomponent.CustomComponent", false, customComponents.getClassLoader());
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

        java.lang.reflect.Method put = customComponents.getDeclaredMethod("put", Class.class, Supplier.class);
        put.setAccessible(true);
        put.invoke(null, componentClass, (Supplier<?>) () -> customComponent);
    }

    private static Class<?> transformedCopilotClass(String className) throws Exception {
        List<BytecodeTransformerBuildItem> transformers = new ArrayList<>();
        OffendingMethodCallsReplacer.addCopilotClassVisitors(transformers::add);
        Optional<BytecodeTransformerBuildItem> transformer = transformers.stream()
                .filter(item -> item.getClassToTransform().equals(className))
                .findFirst();
        if (transformer.isEmpty()) {
            throw new AssertionError("No Copilot transformer registered for " + className);
        }
        URL[] urls = {copilotJar().toUri().toURL()};
        URLClassLoader copilotClassLoader =
                new URLClassLoader(urls, CopilotCustomComponentsPatchTest.class.getClassLoader());
        Map<String, byte[]> transformedClasses = Map.of(className, transform(transformer.get(), copilotClassLoader));
        return new TransformedClassLoader(copilotClassLoader, transformedClasses).loadClass(className);
    }

    private static byte[] transform(BytecodeTransformerBuildItem transformer, ClassLoader copilotClassLoader)
            throws IOException {
        String className = transformer.getClassToTransform();
        String resourceName = className.replace('.', '/') + ".class";
        try (InputStream input = Objects.requireNonNull(
                copilotClassLoader.getResourceAsStream(resourceName), () -> "Cannot find " + resourceName)) {
            ClassReader reader = new ClassReader(input);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
                @Override
                protected String getCommonSuperClass(String type1, String type2) {
                    return Object.class.getName().replace('.', '/');
                }
            };
            ClassVisitor visitor = transformer.getVisitorFunction().apply(className, writer);
            reader.accept(visitor, transformer.getClassReaderOptions());
            return writer.toByteArray();
        }
    }

    private static Path copilotJar() throws Exception {
        return MavenArtifactResolver.builder()
                .build()
                .resolve(new DefaultArtifact("com.vaadin", "copilot", "jar", System.getProperty("vaadin.version")))
                .getArtifact()
                .getFile()
                .toPath();
    }

    private static final class TransformedClassLoader extends ClassLoader {

        private final Map<String, byte[]> transformedClasses;

        private TransformedClassLoader(ClassLoader parent, Map<String, byte[]> transformedClasses) {
            super(parent);
            this.transformedClasses = transformedClasses;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null && transformedClasses.containsKey(name)) {
                    byte[] classBytes = transformedClasses.get(name);
                    loaded = defineClass(name, classBytes, 0, classBytes.length);
                }
                if (loaded == null) {
                    loaded = super.loadClass(name, false);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }
    }
}
