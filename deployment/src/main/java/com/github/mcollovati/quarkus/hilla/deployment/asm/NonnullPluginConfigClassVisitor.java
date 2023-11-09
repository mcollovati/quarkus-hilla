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

import java.util.ListIterator;

import io.quarkus.gizmo.Gizmo;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

public class NonnullPluginConfigClassVisitor extends ClassVisitor {
    public NonnullPluginConfigClassVisitor(ClassVisitor classVisitor) {
        super(Gizmo.ASM_API_VERSION, classVisitor);
    }

    @Override
    public MethodVisitor visitMethod(
            int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor superVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
        if ("<clinit>".equals(name)) {
            return new MethodNode(Gizmo.ASM_API_VERSION, access, name, descriptor, signature, exceptions) {
                @Override
                public void visitEnd() {
                    var iterator = instructions.iterator();
                    IntInsnNode setSize = findSetSizeNode(iterator);
                    if (setSize == null) {
                        return;
                    }
                    setSize.operand = setSize.operand + 1;
                    AbstractInsnNode instruction = findSetOfInstruction(iterator);
                    if (instruction != null) {
                        int nextArrayIndex = setSize.operand - 1;
                        instructions.insertBefore(instruction, new InsnNode(Opcodes.DUP));
                        instructions.insertBefore(instruction, new IntInsnNode(Opcodes.BIPUSH, nextArrayIndex));
                        instructions.insertBefore(
                                instruction,
                                new TypeInsnNode(Opcodes.NEW, "dev/hilla/parser/plugins/nonnull/AnnotationMatcher"));
                        instructions.insertBefore(instruction, new InsnNode(Opcodes.DUP));
                        instructions.insertBefore(
                                instruction, new LdcInsnNode("com.github.mcollovati.quarkus.hilla.NonNullApi"));
                        instructions.insertBefore(instruction, new InsnNode(Opcodes.ICONST_0));
                        instructions.insertBefore(instruction, new IntInsnNode(Opcodes.BIPUSH, 10));
                        instructions.insertBefore(
                                instruction,
                                new MethodInsnNode(
                                        Opcodes.INVOKESPECIAL,
                                        "dev/hilla/parser/plugins/nonnull/AnnotationMatcher",
                                        "<init>",
                                        "(Ljava/lang/String;ZI)V"));
                        instructions.insertBefore(instruction, new InsnNode(Opcodes.AASTORE));
                    }
                    accept(superVisitor);
                }

                private static IntInsnNode findSetSizeNode(ListIterator<AbstractInsnNode> iterator) {
                    while (iterator.hasNext()) {
                        if (iterator.next() instanceof IntInsnNode intInsnNode) {
                            return intInsnNode;
                        }
                    }
                    return null;
                }

                private static AbstractInsnNode findSetOfInstruction(ListIterator<AbstractInsnNode> iterator) {
                    while (iterator.hasNext()) {
                        var instruction = iterator.next();
                        if (instruction.getOpcode() == Opcodes.INVOKESTATIC
                                && instruction instanceof MethodInsnNode methodInsnNode
                                && "java/util/Set".equals(methodInsnNode.owner)
                                && "of".equals(methodInsnNode.name)) {
                            return instruction;
                        }
                    }
                    return null;
                }
            };
        }
        return superVisitor;
    }
}
