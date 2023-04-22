package org.acme.hilla.test.extension.deployment.asm;

import io.quarkus.gizmo.Gizmo;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.ListIterator;
import java.util.Set;

public class DropInitStatementMethodNode extends MethodNode {

    private final Set<String> classNames;

    public DropInitStatementMethodNode(int access, String name, String desc,
                                       String signature, String[] exceptions, MethodVisitor mv, Set<String> classNames) {
        super(Gizmo.ASM_API_VERSION, access, name, desc, signature, exceptions);
        this.classNames = classNames;
        this.mv = mv;
    }

    @Override
    public void visitEnd() {
        var iter = instructions.iterator();
        while (iter.hasNext()) {
            var typeInsnNode = findTypeInsn(iter);
            if (typeInsnNode != null) {
                removeUntilPreviousLabel(iter);
                removeUntilNextLabel(iter);
            }
        }
        accept(mv);
    }

    private TypeInsnNode findTypeInsn(ListIterator<AbstractInsnNode> iter) {
        while (iter.hasNext()) {
            var instruction = iter.next();
            if (instruction instanceof TypeInsnNode && classNames.contains(((TypeInsnNode) instruction).desc)) {
                return (TypeInsnNode) instruction;
            }
        }
        return null;
    }

    private void removeUntilPreviousLabel(ListIterator<AbstractInsnNode> iter) {
        while (iter.hasPrevious()) {
            var instruction = iter.previous();
            iter.remove();
            if (instruction instanceof LabelNode) {
                break;
            }
        }
    }

    private void removeUntilNextLabel(ListIterator<AbstractInsnNode> iter) {
        while (iter.hasNext()) {
            var instruction = iter.next();
            if (instruction instanceof LabelNode) {
                break;
            }
            iter.remove();
        }
    }
}
