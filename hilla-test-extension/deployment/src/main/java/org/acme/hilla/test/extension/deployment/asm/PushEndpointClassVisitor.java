package org.acme.hilla.test.extension.deployment.asm;

import io.quarkus.gizmo.Gizmo;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.Set;

public class PushEndpointClassVisitor extends ClassVisitor {
    private final String methodName = "onMessageRequest";
    private final Set<MethodSignature> methodCallsToDrop = Set.of(
            MethodSignature.of("org/springframework/security/core/context/SecurityContextHolder", "setContext"),
            MethodSignature.of("org/springframework/security/core/context/SecurityContextHolder", "clearContext")
            );

    public PushEndpointClassVisitor(ClassVisitor classVisitor) {
        super(Gizmo.ASM_API_VERSION, classVisitor);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name,
                                     String descriptor, String signature, String[] exceptions) {
        MethodVisitor superVisitor = super.visitMethod(access, name,
                descriptor, signature, exceptions);
        if (methodName.equals(name)) {
            return new DropStatementMethodNode(Gizmo.ASM_API_VERSION, access, name, descriptor,
                    signature, exceptions, superVisitor, this::isDropMethodCall);
        }
        return superVisitor;
    }

    private boolean isDropMethodCall(AbstractInsnNode instruction) {
        return instruction instanceof MethodInsnNode &&
                methodCallsToDrop.stream().anyMatch(signature -> AsmUtils.hasMethodInsnSignature(signature, (MethodInsnNode) instruction));
    }
}
