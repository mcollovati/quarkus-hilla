package org.acme.hilla.test.extension.deployment.asm;

import io.quarkus.gizmo.Gizmo;
import org.acme.hilla.test.extension.deployment.asm.MethodRedirectVisitor;
import org.acme.hilla.test.extension.deployment.asm.MethodSignature;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Map;

class SpringReplacementsRedirectMethodVisitor extends MethodVisitor {

    private final MethodVisitor redirectVisitors;

    protected SpringReplacementsRedirectMethodVisitor(MethodVisitor mv) {
        super(Gizmo.ASM_API_VERSION, mv);
        var redirects = Map.of(
                MethodSignature.of("org/springframework/security/core/context/SecurityContextHolder", "getContext"),
                MethodSignature.DROP_METHOD,
                MethodSignature.of("org/springframework/security/core/context/SecurityContextHolder", "setContext"),
                MethodSignature.DROP_METHOD,
                MethodSignature.of("org/springframework/util/ClassUtils", "getUserClass"),
                MethodSignature.of("org/acme/hilla/test/extension/SpringReplacements", "classUtils_getUserClass"),
                MethodSignature.of("dev/hilla/AuthenticationUtil", "getSecurityHolderAuthentication", "()Lorg/springframework/security/core/Authentication;"),
                MethodSignature.of("org/acme/hilla/test/extension/SpringReplacements", "authenticationUtil_getSecurityHolderAuthentication", "()Ljava/security/Principal;"),
                MethodSignature.of("dev/hilla/AuthenticationUtil", "getSecurityHolderRoleChecker"),
                MethodSignature.of("org/acme/hilla/test/extension/SpringReplacements", "authenticationUtil_getSecurityHolderRoleChecker")
        );
        redirectVisitors = buildVisitorChain(mv, redirects);
    }

    private MethodVisitor buildVisitorChain(MethodVisitor mv, Map<MethodSignature, MethodSignature> redirects) {
        var newMv = mv;
        for (var e : redirects.entrySet()) {
            newMv = new MethodRedirectVisitor(newMv, e.getKey(), e.getValue());
        }
        return newMv;
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        if (opcode == Opcodes.CHECKCAST
                && "org/springframework/security/core/Authentication"
                .equals(type)) {
            // Hack: drop explicit cast to Authentication to prevent runtime
            // error
            return;
        }
        super.visitTypeInsn(opcode, type);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name,
                                String descriptor, boolean isInterface) {
        redirectVisitors.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
    }
}
