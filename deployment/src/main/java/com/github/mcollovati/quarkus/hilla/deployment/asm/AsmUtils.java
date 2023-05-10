package com.github.mcollovati.quarkus.hilla.deployment.asm;

import org.objectweb.asm.tree.MethodInsnNode;

public class AsmUtils {


    public static boolean hasMethodInsnSignature(MethodSignature srcMethod, MethodInsnNode methodNode) {
        return hasMethodInsnSignature(srcMethod, methodNode.owner, methodNode.name, methodNode.desc);
    }
    public static boolean hasMethodInsnSignature(MethodSignature srcMethod, String owner, String name, String descriptor) {
        return srcMethod.getOwner().equals(owner)
                && srcMethod.getName().equals(name)
                && (srcMethod.getDescriptor() == null || srcMethod.getDescriptor().equals(descriptor));
    }

}
