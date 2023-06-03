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

class MethodRedirectVisitor extends MethodVisitor {

    private final MethodSignature srcMethod;
    private final MethodSignature targetMethod;

    protected MethodRedirectVisitor(MethodVisitor mv, MethodSignature srcMethod, MethodSignature targetMethod) {
        super(Gizmo.ASM_API_VERSION, mv);
        this.srcMethod = srcMethod;
        this.targetMethod = targetMethod;
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        if (Opcodes.INVOKESTATIC == opcode && AsmUtils.hasMethodInsnSignature(srcMethod, owner, name, descriptor)) {
            // DROP CALL
            if (targetMethod.equals(MethodSignature.DROP_METHOD)) {
                // Clear stack
                final var paramAmount = Type.getArgumentTypes(descriptor).length;
                for (int i = 0; i < paramAmount; i++) {
                    super.visitInsn(Opcodes.POP);
                }
            } else {
                // REDIRECT CALL
                var targetDescriptor = targetMethod.getDescriptor() == null ? descriptor : targetMethod.getDescriptor();
                super.visitMethodInsn(
                        Opcodes.INVOKESTATIC, targetMethod.getOwner(), targetMethod.getName(), targetDescriptor, false);
            }

            return;
        }

        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
    }
}
