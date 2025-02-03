/*
 * Copyright 2025 Marco Collovati, Dario Götze
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
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/**
 * Modifies EndpointController class to use the extension MultipartRequest
 * wrapper instead of Spring MultipartHttpServletRequest.
 */
public class EndpointControllerVisitor extends ClassVisitor {

    public static final String SPRING_MULTIPART_HTTP_SERVLET_REQUEST =
            "org/springframework/web/multipart/MultipartHttpServletRequest";
    public static final String QH_MULTIPART_HTTP_SERVLET_REQUEST =
            "com/github/mcollovati/quarkus/hilla/multipart/MultipartRequest";

    public EndpointControllerVisitor(ClassVisitor classVisitor) {
        super(Gizmo.ASM_API_VERSION, classVisitor);
    }

    @Override
    public MethodVisitor visitMethod(
            int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor superVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
        if ("doServeEndpoint".equals(name)) {
            return new MethodNode(Gizmo.ASM_API_VERSION, access, name, descriptor, signature, exceptions) {
                @Override
                public void visitEnd() {
                    var iterator = instructions.iterator();
                    TypeInsnNode checkCastNode = AsmUtils.findNextInsnNode(
                            iterator,
                            node -> node.getOpcode() == Opcodes.CHECKCAST
                                    && node.desc.equals(SPRING_MULTIPART_HTTP_SERVLET_REQUEST),
                            TypeInsnNode.class);
                    checkCastNode.desc = QH_MULTIPART_HTTP_SERVLET_REQUEST;
                    MethodInsnNode getParameterNode = AsmUtils.findNextInsnNode(
                            iterator,
                            node -> node.getOpcode() == Opcodes.INVOKEINTERFACE
                                    && node.owner.equals(SPRING_MULTIPART_HTTP_SERVLET_REQUEST)
                                    && node.name.equals("getParameter"),
                            MethodInsnNode.class);
                    getParameterNode.setOpcode(Opcodes.INVOKEVIRTUAL);
                    getParameterNode.owner = QH_MULTIPART_HTTP_SERVLET_REQUEST;
                    getParameterNode.itf = false;

                    MethodInsnNode getFileMapNode = AsmUtils.findNextInsnNode(
                            iterator,
                            node -> node.getOpcode() == Opcodes.INVOKEINTERFACE
                                    && node.owner.equals(SPRING_MULTIPART_HTTP_SERVLET_REQUEST)
                                    && node.name.equals("getFileMap"),
                            MethodInsnNode.class);
                    getFileMapNode.setOpcode(Opcodes.INVOKEVIRTUAL);
                    getFileMapNode.owner = QH_MULTIPART_HTTP_SERVLET_REQUEST;
                    getFileMapNode.itf = false;
                    accept(superVisitor);
                }
            };
        }
        return superVisitor;
    }
}
