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
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

public class NonnullPluginConfigProcessorClassVisitor extends ClassVisitor {

    private static final String CLASS_INITIALIZER_METHOD_NAME = "<clinit>";
    private static final MethodSignature SET_OF_SIGNATURE = MethodSignature.of("java/util/Set", "of");

    public NonnullPluginConfigProcessorClassVisitor(ClassVisitor classVisitor) {
        super(Gizmo.ASM_API_VERSION, classVisitor);
    }

    @Override
    public MethodVisitor visitMethod(
            int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor superVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
        if (CLASS_INITIALIZER_METHOD_NAME.equals(name)) {
            return new MethodNode(Gizmo.ASM_API_VERSION, access, name, descriptor, signature, exceptions) {
                @Override
                public void visitEnd() {
                    var iterator = instructions.iterator();
                    // Find array size node, is expected to be the first IntInsnNode
                    IntInsnNode setSize = AsmUtils.findNextInsnNode(iterator, node -> true, IntInsnNode.class);
                    if (setSize == null) {
                        return;
                    }
                    // Increase the array size
                    setSize.operand += 1;
                    // Find first instruction after array construction
                    AbstractInsnNode instruction = AsmUtils.findNextInsnNode(iterator, this::isSetOfInstruction);
                    if (instruction != null) {
                        final InsnList newAnnotationMatcher = constructNewAnnotationMatcherNonNullApi();
                        final InsnList addToArray = addArrayElement(setSize.operand - 1, newAnnotationMatcher);
                        instructions.insertBefore(instruction, addToArray);
                    }
                    accept(superVisitor);
                }

                private Boolean isSetOfInstruction(AbstractInsnNode instruction) {
                    return instruction.getOpcode() == Opcodes.INVOKESTATIC
                            && instruction instanceof MethodInsnNode methodInsnNode
                            && AsmUtils.hasMethodInsnSignature(SET_OF_SIGNATURE, (MethodInsnNode) instruction);
                }

                private static InsnList addArrayElement(int arrayIndex, InsnList elementConstruction) {
                    final InsnList instructions = new InsnList();
                    instructions.add(new InsnNode(Opcodes.DUP));
                    instructions.add(new IntInsnNode(Opcodes.BIPUSH, arrayIndex));
                    instructions.add(elementConstruction);
                    instructions.add(new InsnNode(Opcodes.AASTORE));
                    return instructions;
                }

                /**
                 * @return the instructions for {@code new AnnotationMatcher("com.github.mcollovati.quarkus.hilla.NonNullApi", false, 10)}
                 */
                private static InsnList constructNewAnnotationMatcherNonNullApi() {
                    final InsnList instructions = new InsnList();
                    instructions.add(
                            new TypeInsnNode(Opcodes.NEW, "dev/hilla/parser/plugins/nonnull/AnnotationMatcher"));
                    instructions.add(new InsnNode(Opcodes.DUP));
                    instructions.add(new LdcInsnNode("com.github.mcollovati.quarkus.hilla.NonNullApi"));
                    instructions.add(new InsnNode(Opcodes.ICONST_0));
                    instructions.add(new IntInsnNode(Opcodes.BIPUSH, 10));
                    instructions.add(new MethodInsnNode(
                            Opcodes.INVOKESPECIAL,
                            "dev/hilla/parser/plugins/nonnull/AnnotationMatcher",
                            "<init>",
                            "(Ljava/lang/String;ZI)V"));
                    return instructions;
                }
            };
        }
        return superVisitor;
    }
}
