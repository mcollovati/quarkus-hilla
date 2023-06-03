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
import org.objectweb.asm.MethodVisitor;

class SpringReplacementsRedirectMethodVisitor extends MethodVisitor {

    private final MethodVisitor redirectVisitors;

    protected SpringReplacementsRedirectMethodVisitor(MethodVisitor mv) {
        super(Gizmo.ASM_API_VERSION, mv);
        var redirects = Map.of(MethodSignature.of(
                "org/springframework/security/core/context/SecurityContextHolder",
                "getContext"), MethodSignature.DROP_METHOD,
                MethodSignature.of("org/springframework/util/ClassUtils",
                        "getUserClass"),
                MethodSignature.of(
                        "com/github/mcollovati/quarkus/hilla/SpringReplacements",
                        "classUtils_getUserClass"),
                MethodSignature.of("dev/hilla/AuthenticationUtil",
                        "getSecurityHolderAuthentication",
                        "()Lorg/springframework/security/core/Authentication;"),
                MethodSignature.of(
                        "com/github/mcollovati/quarkus/hilla/SpringReplacements",
                        "authenticationUtil_getSecurityHolderAuthentication",
                        "()Ljava/security/Principal;"),
                MethodSignature.of("dev/hilla/AuthenticationUtil",
                        "getSecurityHolderRoleChecker"),
                MethodSignature.of(
                        "com/github/mcollovati/quarkus/hilla/SpringReplacements",
                        "authenticationUtil_getSecurityHolderRoleChecker"));
        redirectVisitors = buildVisitorChain(mv, redirects);
    }

    private MethodVisitor buildVisitorChain(MethodVisitor mv,
            Map<MethodSignature, MethodSignature> redirects) {
        var newMv = mv;
        for (var e : redirects.entrySet()) {
            newMv = new MethodRedirectVisitor(newMv, e.getKey(), e.getValue());
        }
        return newMv;
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name,
            String descriptor, boolean isInterface) {
        redirectVisitors.visitMethodInsn(opcode, owner, name, descriptor,
                isInterface);
    }
}
