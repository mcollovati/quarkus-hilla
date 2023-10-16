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

import java.util.Set;

import io.quarkus.gizmo.Gizmo;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

public class TransferTypesPluginClassVisitor extends ClassVisitor {
    private final String methodName = "<init>";

    public TransferTypesPluginClassVisitor(ClassVisitor classVisitor) {
        super(Gizmo.ASM_API_VERSION, classVisitor);
    }

    @Override
    public MethodVisitor visitMethod(
            int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor superVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
        if (methodName.equals(name)) {
            return new DropLdcStatementMethodNode(
                    access,
                    name,
                    descriptor,
                    signature,
                    exceptions,
                    superVisitor,
                    Set.of(
                            Type.getType("Ldev/hilla/mappedtypes/Order;"),
                            Type.getType("Ldev/hilla/mappedtypes/Pageable;"),
                            Type.getType("Ldev/hilla/mappedtypes/Sort;")));
        }
        return superVisitor;
    }
}