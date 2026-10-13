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
import org.objectweb.asm.Opcodes;
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

    @Test
    void authorizationAudit_rejectsUnsupportedSignature() {
        var visitor = new EndpointInvokerAuthorizationEventsVisitor(new ClassWriter(0));
        assertThatThrownBy(() ->
                        visitor.visitMethod(Opcodes.ACC_PRIVATE, "publishAuthorizationDeniedEvent", "()Z", null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported Hilla authorization audit method signature");
    }

    @Test
    void authorizationAudit_rejectsMembersWithoutKnownPublisher() {
        var visitor = new EndpointInvokerAuthorizationEventsVisitor(new ClassWriter(0));
        visitor.visitField(Opcodes.ACC_PRIVATE, "authorizationEventPublisher", "Ljava/lang/Object;", null, null);
        assertThatThrownBy(visitor::visitEnd)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("publishAuthorizationDeniedEvent was not transformed");
    }

    @Test
    void authorizationAudit_rejectsGetterWithoutKnownPublisher() {
        var visitor = new EndpointInvokerAuthorizationEventsVisitor(new ClassWriter(0));
        visitor.visitMethod(
                Opcodes.ACC_PRIVATE, "getAuthorizationEventPublisher", "()Ljava/util/Optional;", null, null);
        assertThatThrownBy(visitor::visitEnd).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void authorizationAudit_preservesClassesWithoutAuditMembers() {
        var visitor = new EndpointInvokerAuthorizationEventsVisitor(new ClassWriter(0));
        visitor.visitEnd();
    }

    @Test
    void authorizationAudit_rejectsAdditionalGetterCaller() {
        var visitor = new EndpointInvokerAuthorizationEventsVisitor(new ClassWriter(0));
        var method = visitor.visitMethod(Opcodes.ACC_PUBLIC, "futureAccessCheck", "()V", null, null);
        assertThatThrownBy(() -> method.visitMethodInsn(
                        Opcodes.INVOKEVIRTUAL,
                        "com/vaadin/hilla/EndpointInvoker",
                        "getAuthorizationEventPublisher",
                        "()Ljava/util/Optional;",
                        false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("futureAccessCheck still references getAuthorizationEventPublisher");
    }

    @Test
    void authorizationAudit_rejectsAdditionalFieldUser() {
        var visitor = new EndpointInvokerAuthorizationEventsVisitor(new ClassWriter(0));
        var method = visitor.visitMethod(Opcodes.ACC_PUBLIC, "futureAccessCheck", "()V", null, null);
        assertThatThrownBy(() -> method.visitFieldInsn(
                        Opcodes.GETFIELD,
                        "com/vaadin/hilla/EndpointInvoker",
                        "authorizationEventPublisher",
                        "Ljava/lang/Object;"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("futureAccessCheck still references authorizationEventPublisher");
    }

    @Test
    void authorizationAudit_rejectsAdditionalMethodReference() {
        var visitor = new EndpointInvokerAuthorizationEventsVisitor(new ClassWriter(0));
        var method = visitor.visitMethod(Opcodes.ACC_PUBLIC, "futureAccessCheck", "()V", null, null);
        var reference = new org.objectweb.asm.Handle(
                Opcodes.H_INVOKEVIRTUAL,
                "com/vaadin/hilla/EndpointInvoker",
                "getAuthorizationEventPublisher",
                "()Ljava/util/Optional;",
                false);
        assertThatThrownBy(() -> method.visitLdcInsn(reference))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("getAuthorizationEventPublisher");
    }

    @Test
    void authorizationAudit_rejectsAdditionalLambdaReference() {
        var visitor = new EndpointInvokerAuthorizationEventsVisitor(new ClassWriter(0));
        var method = visitor.visitMethod(Opcodes.ACC_PUBLIC, "futureAccessCheck", "()V", null, null);
        var bootstrap = new org.objectweb.asm.Handle(
                Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory", "()V", false);
        var reference = new org.objectweb.asm.Handle(
                Opcodes.H_INVOKEVIRTUAL,
                "com/vaadin/hilla/EndpointInvoker",
                "getAuthorizationEventPublisher",
                "()Ljava/util/Optional;",
                false);
        assertThatThrownBy(() ->
                        method.visitInvokeDynamicInsn("get", "()Ljava/util/function/Supplier;", bootstrap, reference))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("getAuthorizationEventPublisher");
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
