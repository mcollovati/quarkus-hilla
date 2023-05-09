package com.github.mcollovati.quarkus.hilla.deployment.asm;

import io.quarkus.gizmo.Gizmo;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

public class PushEndpointClassVisitor extends ClassVisitor {
    private final String methodName = "onMessageRequest";
    private final MethodSignature setContextSignature = MethodSignature.of("org/springframework/security/core/context/SecurityContextHolder", "setContext");
    private final MethodSignature clearContextSignature = MethodSignature.of("org/springframework/security/core/context/SecurityContextHolder", "clearContext");

    public PushEndpointClassVisitor(ClassVisitor classVisitor) {
        super(Gizmo.ASM_API_VERSION, classVisitor);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name,
                                     String descriptor, String signature, String[] exceptions) {
        MethodVisitor superVisitor = super.visitMethod(access, name,
                descriptor, signature, exceptions);
        if (methodName.equals(name)) {
            return buildMethodVisitorChain(access, name, descriptor, signature, exceptions, superVisitor);
        }
        return superVisitor;
    }

    private DropStatementMethodNode buildMethodVisitorChain(int access, String name, String descriptor, String signature, String[] exceptions, MethodVisitor superVisitor) {
        var clearCtxVisitor = new MethodRedirectVisitor(superVisitor, clearContextSignature, MethodSignature.DROP_METHOD);
        return new DropStatementMethodNode(Gizmo.ASM_API_VERSION, access, name, descriptor,
                signature, exceptions, clearCtxVisitor, this::isDropMethodCall);
    }

    private boolean isDropMethodCall(AbstractInsnNode instruction) {
        return instruction instanceof MethodInsnNode && AsmUtils.hasMethodInsnSignature(setContextSignature, (MethodInsnNode) instruction);
    }
}
