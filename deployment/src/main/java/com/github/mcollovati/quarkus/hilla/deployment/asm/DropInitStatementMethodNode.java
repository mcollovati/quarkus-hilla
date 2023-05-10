package com.github.mcollovati.quarkus.hilla.deployment.asm;

import io.quarkus.gizmo.Gizmo;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.Set;

public class DropInitStatementMethodNode extends DropStatementMethodNode {

    public DropInitStatementMethodNode(int access, String name, String desc,
                                       String signature, String[] exceptions, MethodVisitor mv, Set<String> classNames) {
        super(Gizmo.ASM_API_VERSION, access, name, desc, signature, exceptions, mv, (node) -> isTypeInsn(node, classNames));
    }

    private static boolean isTypeInsn(AbstractInsnNode instruction, Set<String> classNames) {
        return instruction instanceof TypeInsnNode && classNames.contains(((TypeInsnNode) instruction).desc);
    }
}
