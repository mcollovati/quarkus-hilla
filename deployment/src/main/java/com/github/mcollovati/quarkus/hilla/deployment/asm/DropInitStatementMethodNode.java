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
import java.util.Set;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

public class DropInitStatementMethodNode extends DropStatementMethodNode {

    public DropInitStatementMethodNode(
            int access,
            String name,
            String desc,
            String signature,
            String[] exceptions,
            MethodVisitor mv,
            Set<String> classNames) {
        super(
                Gizmo.ASM_API_VERSION,
                access,
                name,
                desc,
                signature,
                exceptions,
                mv,
                (node) -> isTypeInsn(node, classNames));
    }

    private static boolean isTypeInsn(AbstractInsnNode instruction, Set<String> classNames) {
        return instruction instanceof TypeInsnNode && classNames.contains(((TypeInsnNode) instruction).desc);
    }
}
