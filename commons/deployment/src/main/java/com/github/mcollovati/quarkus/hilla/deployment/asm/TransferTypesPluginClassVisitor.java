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
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class TransferTypesPluginClassVisitor extends ClassVisitor {

    private static final String CLASS_INITIALIZER_METHOD_NAME = "<clinit>";
    private static final MethodSignature SET_OF_SIGNATURE = MethodSignature.of("java/util/Set", "of");

    public TransferTypesPluginClassVisitor(ClassVisitor classVisitor) {
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
                    AbstractInsnNode returnNode = AsmUtils.findNextInsnNode(
                            iterator, node -> node instanceof InsnNode n && n.getOpcode() == Opcodes.RETURN);
                    if (returnNode == null) {
                        return;
                    }
                    mapToEndpointSubscription("io.smallrye.mutiny.Multi", returnNode);
                    mapToEndpointSubscription(
                            "com.github.mcollovati.quarkus.hilla.MutinyEndpointSubscription", returnNode);
                    accept(superVisitor);
                }

                // GETSTATIC com/vaadin/hilla/parser/plugins/transfertypes/TransferTypesPlugin.classMap :
                // Ljava/util/Map;
                // LDC "com.vaadin.hilla.EndpointSubscription"
                // LDC Lcom/vaadin/hilla/runtime/transfertypes/EndpointSubscription;.class
                // INVOKEINTERFACE java/util/Map.put (Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object; (itf)
                // POP
                private void mapToEndpointSubscription(String typeName, AbstractInsnNode beforeNode) {
                    instructions.insertBefore(
                            beforeNode,
                            new FieldInsnNode(
                                    Opcodes.GETSTATIC,
                                    "com/vaadin/hilla/parser/plugins/transfertypes/TransferTypesPlugin",
                                    "classMap",
                                    "Ljava/util/Map;"));
                    instructions.insertBefore(beforeNode, new LdcInsnNode(typeName));
                    instructions.insertBefore(
                            beforeNode,
                            new LdcInsnNode(
                                    Type.getType("Lcom/vaadin/hilla/runtime/transfertypes/EndpointSubscription;")));
                    instructions.insertBefore(
                            beforeNode,
                            new MethodInsnNode(
                                    Opcodes.INVOKEINTERFACE,
                                    "java/util/Map",
                                    "put",
                                    "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"));
                    instructions.insertBefore(beforeNode, new InsnNode(Opcodes.POP));
                }
            };
        }
        return superVisitor;
    }
}
