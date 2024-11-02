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

import java.util.Map;

import io.quarkus.gizmo.Gizmo;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

public class MethodReplacementClassVisitor extends ClassVisitor {

    private final String methodName;
    private final Map<MethodSignature, MethodSignature> replacements;

    /**
     * @param classVisitor the "super" ClassVisitor
     * @param methodName the method in which to search for replacements
     */
    public MethodReplacementClassVisitor(
            ClassVisitor classVisitor, String methodName, Map<MethodSignature, MethodSignature> replacements) {
        super(Gizmo.ASM_API_VERSION, classVisitor);
        this.methodName = methodName;
        this.replacements = replacements;
    }

    @Override
    public MethodVisitor visitMethod(
            int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor superVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
        if (methodName.equals(name)) {
            return new MethodRedirectNode(
                    api, access, name, descriptor, signature, exceptions, superVisitor, replacements);
        }
        return superVisitor;
    }
}
