package org.acme.hilla.test.extension.deployment;

import io.quarkus.gizmo.Gizmo;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

class SpringReplacementsClassVisitor extends ClassVisitor {

    private final String methodName;

    public SpringReplacementsClassVisitor(ClassVisitor classVisitor,
                                          String methodName) {
        super(Gizmo.ASM_API_VERSION, classVisitor);
        this.methodName = methodName;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name,
                                     String descriptor, String signature, String[] exceptions) {
        if (methodName.equals(name)) {
            MethodVisitor superVisitor = super.visitMethod(access, name,
                    descriptor, signature, exceptions);
            return new SpringReplacementsRedirectMethodVisitor(
                    superVisitor);
        }
        return super.visitMethod(access, name, descriptor, signature,
                exceptions);
    }
}
