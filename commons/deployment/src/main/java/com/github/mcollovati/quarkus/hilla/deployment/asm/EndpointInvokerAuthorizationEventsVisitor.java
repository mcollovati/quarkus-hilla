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

import io.quarkus.gizmo.Gizmo;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Removes optional Spring Security audit publishing from the endpoint invoker.
 * The endpoint access checks and their denial results remain unchanged.
 */
public class EndpointInvokerAuthorizationEventsVisitor extends ClassVisitor {

    private boolean removedAuditMember;
    private boolean transformedPublisher;

    /**
     * Creates the visitor.
     *
     * @param classVisitor the next visitor in the transformation chain
     */
    public EndpointInvokerAuthorizationEventsVisitor(ClassVisitor classVisitor) {
        super(Gizmo.ASM_API_VERSION, classVisitor);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        if ("authorizationEventPublisher".equals(name)) {
            removedAuditMember = true;
            return null;
        }
        return super.visitField(access, name, descriptor, signature, value);
    }

    @Override
    public MethodVisitor visitMethod(
            int access, String name, String descriptor, String signature, String[] exceptions) {
        if ("getAuthorizationEventPublisher".equals(name)
                || name.startsWith("lambda$publishAuthorizationDeniedEvent$")) {
            removedAuditMember = true;
            return null;
        }
        MethodVisitor visitor = super.visitMethod(access, name, descriptor, signature, exceptions);
        if ("publishAuthorizationDeniedEvent".equals(name)) {
            String expectedDescriptor = "(Ljava/lang/Class;Lcom/vaadin/hilla/EndpointRegistry$VaadinEndpointData;"
                    + "Ljava/lang/reflect/Method;Ljava/security/Principal;)V";
            if (!expectedDescriptor.equals(descriptor) || (access & Opcodes.ACC_STATIC) != 0) {
                throw new IllegalStateException(
                        "Unsupported Hilla authorization audit method signature: " + descriptor);
            }
            transformedPublisher = true;
            // Quarkus owns authentication; there is no Spring AuthorizationEventPublisher bean.
            // Keep the helper callable so checkAccess still returns its original denial reason.
            visitor.visitCode();
            visitor.visitInsn(Opcodes.RETURN);
            visitor.visitMaxs(0, 5);
            visitor.visitEnd();
            return null;
        }
        return new MethodVisitor(Gizmo.ASM_API_VERSION, visitor) {
            private void checkReference(String owner, String member) {
                if ("com/vaadin/hilla/EndpointInvoker".equals(owner)
                        && ("authorizationEventPublisher".equals(member)
                                || "getAuthorizationEventPublisher".equals(member)
                                || member.startsWith("lambda$publishAuthorizationDeniedEvent$"))) {
                    throw new IllegalStateException(
                            "Unsupported Hilla authorization audit reference: " + name + " still references " + member);
                }
            }

            private void checkConstant(Object constant) {
                if (constant instanceof Handle handle) {
                    checkReference(handle.getOwner(), handle.getName());
                } else if (constant instanceof ConstantDynamic dynamic) {
                    checkConstant(dynamic.getBootstrapMethod());
                    for (int index = 0; index < dynamic.getBootstrapMethodArgumentCount(); index++) {
                        checkConstant(dynamic.getBootstrapMethodArgument(index));
                    }
                }
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String member, String type, boolean isInterface) {
                checkReference(owner, member);
                super.visitMethodInsn(opcode, owner, member, type, isInterface);
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String member, String type) {
                checkReference(owner, member);
                super.visitFieldInsn(opcode, owner, member, type);
            }

            @Override
            public void visitInvokeDynamicInsn(String member, String type, Handle bootstrap, Object... arguments) {
                checkConstant(bootstrap);
                for (Object argument : arguments) {
                    checkConstant(argument);
                }
                super.visitInvokeDynamicInsn(member, type, bootstrap, arguments);
            }

            @Override
            public void visitLdcInsn(Object value) {
                checkConstant(value);
                super.visitLdcInsn(value);
            }
        };
    }

    @Override
    public void visitEnd() {
        if (removedAuditMember && !transformedPublisher) {
            throw new IllegalStateException(
                    "Unsupported Hilla authorization audit layout: publishAuthorizationDeniedEvent was not transformed");
        }
        super.visitEnd();
    }
}
