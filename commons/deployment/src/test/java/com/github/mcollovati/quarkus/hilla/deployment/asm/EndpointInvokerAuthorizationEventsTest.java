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
package com.github.mcollovati.quarkus.hilla.deployment.asm;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.vaadin.hilla.EndpointInvoker;
import io.quarkus.deployment.builditem.BytecodeTransformerBuildItem;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EndpointInvokerAuthorizationEventsTest {

    @Test
    void endpointInvoker_loadsWithoutSpringSecurityAuditTypes() throws Exception {
        byte[] original;
        try (InputStream stream =
                Objects.requireNonNull(EndpointInvoker.class.getResourceAsStream("EndpointInvoker.class"))) {
            original = stream.readAllBytes();
        }

        ClassReader reader = new ClassReader(original);
        ClassNode originalClass = new ClassNode();
        reader.accept(originalClass, ClassReader.SKIP_CODE);
        if (originalClass.methods.stream().anyMatch(method -> "publishAuthorizationDeniedEvent".equals(method.name))) {
            // Reproduce the startup failure even if the test classpath contains Spring Security.
            // Older Hilla versions have no audit publisher and must also remain loadable.
            assertThatThrownBy(() -> new InvokerClassLoader(original)
                            .loadClass(EndpointInvoker.class.getName())
                            .getDeclaredMethods())
                    .isInstanceOf(NoClassDefFoundError.class);
        }

        List<BytecodeTransformerBuildItem> transformers = new ArrayList<>();
        OffendingMethodCallsReplacer.addClassVisitors(transformers::add);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        ClassVisitor visitor = writer;
        for (BytecodeTransformerBuildItem transformer : transformers) {
            if (EndpointInvoker.class.getName().equals(transformer.getClassToTransform())) {
                visitor = transformer.getVisitorFunction().apply(EndpointInvoker.class.getName(), visitor);
            }
        }
        reader.accept(visitor, 0);

        Class<?> invoker = new InvokerClassLoader(writer.toByteArray()).loadClass(EndpointInvoker.class.getName());
        assertThat(invoker.getDeclaredMethods()).extracting("name").contains("checkAccess", "invoke");
        assertThat(invoker.getDeclaredFields()).extracting("name").doesNotContain("authorizationEventPublisher");
        assertThat(invoker.getDeclaredMethods())
                .extracting("name")
                .doesNotContain("getAuthorizationEventPublisher")
                .noneMatch(name -> name.toString().startsWith("lambda$publishAuthorizationDeniedEvent$"));
    }

    private static final class InvokerClassLoader extends ClassLoader {
        private final byte[] invokerBytes;

        private InvokerClassLoader(byte[] invokerBytes) {
            super(EndpointInvokerAuthorizationEventsTest.class.getClassLoader());
            this.invokerBytes = invokerBytes;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("org.springframework.security.")) {
                throw new ClassNotFoundException(name);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null && EndpointInvoker.class.getName().equals(name)) {
                    loaded = defineClass(name, invokerBytes, 0, invokerBytes.length);
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
