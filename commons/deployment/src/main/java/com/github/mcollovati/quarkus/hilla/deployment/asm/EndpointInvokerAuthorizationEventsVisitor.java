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
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Removes optional Spring Security audit publishing from the endpoint invoker.
 * The endpoint access checks and their denial results remain unchanged.
 */
public class EndpointInvokerAuthorizationEventsVisitor extends ClassVisitor {

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
            return null;
        }
        return super.visitField(access, name, descriptor, signature, value);
    }

    @Override
    public MethodVisitor visitMethod(
            int access, String name, String descriptor, String signature, String[] exceptions) {
        if ("getAuthorizationEventPublisher".equals(name)
                || name.startsWith("lambda$publishAuthorizationDeniedEvent$")) {
            return null;
        }
        MethodVisitor visitor = super.visitMethod(access, name, descriptor, signature, exceptions);
        if ("publishAuthorizationDeniedEvent".equals(name)) {
            // Quarkus owns authentication; there is no Spring AuthorizationEventPublisher bean.
            // Keep the helper callable so checkAccess still returns its original denial reason.
            visitor.visitCode();
            visitor.visitInsn(Opcodes.RETURN);
            visitor.visitMaxs(0, 5);
            visitor.visitEnd();
            return null;
        }
        return visitor;
    }
}
