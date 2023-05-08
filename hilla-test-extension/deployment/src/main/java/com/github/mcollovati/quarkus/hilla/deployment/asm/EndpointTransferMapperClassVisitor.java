package com.github.mcollovati.quarkus.hilla.deployment.asm;

import io.quarkus.gizmo.Gizmo;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import java.util.Set;

public class EndpointTransferMapperClassVisitor extends ClassVisitor {
    private final String methodName = "<init>";

    public EndpointTransferMapperClassVisitor(ClassVisitor classVisitor) {
        super(Gizmo.ASM_API_VERSION, classVisitor);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name,
                                     String descriptor, String signature, String[] exceptions) {
        MethodVisitor superVisitor = super.visitMethod(access, name,
                descriptor, signature, exceptions);
        if (methodName.equals(name)) {
            return new DropInitStatementMethodNode(access, name, descriptor,
                    signature, exceptions, superVisitor, Set.of(
                    "dev/hilla/endpointransfermapper/PageMapper",
                    "dev/hilla/endpointransfermapper/PageableMapper"
            ));
        }
        return superVisitor;
    }
}
