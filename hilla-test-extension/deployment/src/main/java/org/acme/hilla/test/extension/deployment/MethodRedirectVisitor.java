package org.acme.hilla.test.extension.deployment;

import io.quarkus.gizmo.Gizmo;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

class MethodRedirectVisitor extends MethodVisitor {

    private final String srcClass;
    private final String srcMethod;
    private final String targetClass;
    private final String targetMethod;

    protected MethodRedirectVisitor(MethodVisitor mv, String srcClass, String srcMethod, String targetClass, String targetMethod) {
        super(Gizmo.ASM_API_VERSION, mv);
        this.srcClass = srcClass;
        this.srcMethod = srcMethod;
        this.targetClass = targetClass;
        this.targetMethod = targetMethod;
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name,
                                String descriptor, boolean isInterface) {
        if (Opcodes.INVOKESTATIC != opcode || !srcClass.equals(owner) || !srcMethod.equals(name)) {
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            return;
        }
        // call is dropped
        if (targetClass == null || targetMethod == null) {
            final var paramAmount = Type.getArgumentTypes(descriptor).length;
            for (int i = 0; i < paramAmount; i++) {
                super.visitInsn(Opcodes.POP);
            }
            return;
        }
        // call is redirected
        if("()Lorg/springframework/security/core/Authentication;".equals(descriptor)) {
            super.visitMethodInsn(Opcodes.INVOKESTATIC, targetClass, targetMethod, "()Ljava/security/Principal;", false);
            return;
        }
        super.visitMethodInsn(Opcodes.INVOKESTATIC, targetClass, targetMethod, descriptor, false);
    }
}
