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
package com.github.mcollovati.quarkus.hilla.deployment.asm;

import io.quarkus.gizmo.Gizmo;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.Map;

class MethodRedirectVisitor extends MethodVisitor {
    private final int redirectOpcode;
    private final Map<MethodSignature, MethodSignature> redirects;

    protected MethodRedirectVisitor(
            MethodVisitor mv, int redirectOpcode, Map<MethodSignature, MethodSignature> redirects) {
        super(Gizmo.ASM_API_VERSION, mv);
        this.redirectOpcode = redirectOpcode;
        this.redirects = redirects;
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        if (redirectOpcode == opcode) {
            redirects.entrySet().stream()
                    .filter(e -> AsmUtils.hasMethodInsnSignature(e.getKey(), owner, name, descriptor))
                    .findFirst()
                    .ifPresentOrElse(
                            e -> redirect(e.getValue(), descriptor),
                            () -> super.visitMethodInsn(opcode, owner, name, descriptor, isInterface));
        }
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
    }

    private void redirect(MethodSignature targetMethod, String descriptor) {
        if (targetMethod.equals(MethodSignature.DROP_METHOD)) {
            // DROP CALL
            // Clear stack
            final var paramAmount = Type.getArgumentTypes(descriptor).length;
            for (int i = 0; i < paramAmount; i++) {
                super.visitInsn(Opcodes.POP);
            }
        } else {
            // REDIRECT CALL
            var targetDescriptor = targetMethod.getDescriptor() == null ? descriptor : targetMethod.getDescriptor();
            super.visitMethodInsn(
                    redirectOpcode, targetMethod.getOwner(), targetMethod.getName(), targetDescriptor, false);
        }
    }
}
