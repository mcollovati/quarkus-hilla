package org.acme.hilla.test.extension.deployment;

import io.quarkus.gizmo.Gizmo;
import org.apache.commons.lang3.tuple.Pair;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Map;

class SpringReplacementsRedirectMethodVisitor extends MethodVisitor {

    private final MethodVisitor redirectVisitors;

    protected SpringReplacementsRedirectMethodVisitor(MethodVisitor mv) {
        super(Gizmo.ASM_API_VERSION, mv);
        var redirects = Map.of(
                Pair.of("org/springframework/util/ClassUtils", "getUserClass"),
                Pair.of("org/acme/hilla/test/extension/SpringReplacements", "classUtils_getUserClass"),
                Pair.of("org/springframework/security/core/context/SecurityContextHolder", "getContext"),
                Pair.of((String) null, (String) null),
                Pair.of("org/springframework/security/core/context/SecurityContextHolder", "setContext"),
                Pair.of((String) null, (String) null),
                Pair.of("dev/hilla/AuthenticationUtil", "getSecurityHolderAuthentication"),
                Pair.of("org/acme/hilla/test/extension/SpringReplacements", "authenticationUtil_getSecurityHolderAuthentication"),
                Pair.of("dev/hilla/AuthenticationUtil", "getSecurityHolderRoleChecker"),
                Pair.of("org/acme/hilla/test/extension/SpringReplacements", "authenticationUtil_getSecurityHolderRoleChecker")
        );
        redirectVisitors = buildVisitorChain(mv, redirects);
    }

    private MethodVisitor buildVisitorChain(MethodVisitor mv, Map<Pair<String, String>, Pair<String, String>> redirects) {
        var newMv = mv;
        for (var e : redirects.entrySet()) {
            newMv = new MethodRedirectVisitor(newMv, e.getKey().getKey(), e.getKey().getValue(), e.getValue().getKey(), e.getValue().getValue());
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
