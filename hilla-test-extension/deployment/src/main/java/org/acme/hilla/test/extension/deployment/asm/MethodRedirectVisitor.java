package org.acme.hilla.test.extension.deployment.asm;

import io.quarkus.gizmo.Gizmo;
import org.acme.hilla.test.extension.deployment.asm.MethodSignature;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

class MethodRedirectVisitor extends MethodVisitor {


    private final MethodSignature srcMethod;
    private final MethodSignature targetMethod;

    protected MethodRedirectVisitor(MethodVisitor mv, MethodSignature srcMethod, MethodSignature targetMethod) {
        super(Gizmo.ASM_API_VERSION, mv);
        this.srcMethod = srcMethod;
        this.targetMethod = targetMethod;
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name,
                                String descriptor, boolean isInterface) {
        if (Opcodes.INVOKESTATIC == opcode && hasSrcSignature(owner, name, descriptor)) {
            // DROP CALL
            if (targetMethod.equals(MethodSignature.DROP_METHOD)) {
                // Clear stack
                final var paramAmount = Type.getArgumentTypes(descriptor).length;
                for (int i = 0; i < paramAmount; i++) {
                    super.visitInsn(Opcodes.POP);
                }
            }
            else {
                // REDIRECT CALL
                var targetDescriptor = targetMethod.getDescriptor() == null ? descriptor : targetMethod.getDescriptor();
                super.visitMethodInsn(Opcodes.INVOKESTATIC, targetMethod.getOwner(), targetMethod.getName(), targetDescriptor, false);
            }

            return;
        }

        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
    }

    private boolean hasSrcSignature(String owner, String name, String descriptor) {
        return srcMethod.getOwner().equals(owner)
                && srcMethod.getName().equals(name)
                && (srcMethod.getDescriptor() == null || srcMethod.getDescriptor().equals(descriptor));
    }
}
