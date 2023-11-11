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

import java.util.Map;

/**
 * This ClassVisitor searches for used methods which are based on Spring and replaces them with a Quarkus equivalent.
 */
public class SpringReplacementsClassVisitor extends ClassVisitor {

    private static final Map<MethodSignature, MethodSignature> STATIC_SPRING_REDIRECTS = Map.of(
            MethodSignature.of("org/springframework/security/core/context/SecurityContextHolder", "getContext"),
            MethodSignature.DROP_METHOD,
            MethodSignature.of("org/springframework/util/ClassUtils", "getUserClass"),
            MethodSignature.of("com/github/mcollovati/quarkus/hilla/SpringReplacements", "classUtils_getUserClass"),
            MethodSignature.of(
                    "dev/hilla/AuthenticationUtil",
                    "getSecurityHolderAuthentication",
                    "()Lorg/springframework/security/core/Authentication;"),
            MethodSignature.of(
                    "com/github/mcollovati/quarkus/hilla/SpringReplacements",
                    "authenticationUtil_getSecurityHolderAuthentication",
                    "()Ljava/security/Principal;"),
            MethodSignature.of("dev/hilla/AuthenticationUtil", "getSecurityHolderRoleChecker"),
            MethodSignature.of(
                    "com/github/mcollovati/quarkus/hilla/SpringReplacements",
                    "authenticationUtil_getSecurityHolderRoleChecker"));
    private final String methodName;

    /**
     * @param classVisitor the "super" ClassVisitor
     * @param methodName the method in which to search for replacements
     */
    public SpringReplacementsClassVisitor(ClassVisitor classVisitor, String methodName) {
        super(Gizmo.ASM_API_VERSION, classVisitor);
        this.methodName = methodName;
    }

    @Override
    public MethodVisitor visitMethod(
            int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor superVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
        if (methodName.equals(name)) {
            return new MethodRedirectVisitor(superVisitor, Opcodes.INVOKESTATIC, STATIC_SPRING_REDIRECTS);
        }
        return superVisitor;
    }
}
