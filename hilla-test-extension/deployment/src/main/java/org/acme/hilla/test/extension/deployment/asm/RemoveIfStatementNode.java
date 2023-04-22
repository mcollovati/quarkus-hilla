package org.acme.hilla.test.extension.deployment.asm;

import io.quarkus.gizmo.Gizmo;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ListIterator;

public class RemoveIfStatementNode extends MethodNode {

    private final int ifStatementOpcode;

    /**
     * Removes the first if statement until the targeted label is reached
     *
     * @param access
     * @param name
     * @param desc
     * @param signature
     * @param exceptions
     * @param mv
     * @param ifStatementOpcode
     */
    public RemoveIfStatementNode(int access, String name, String desc,
                                 String signature, String[] exceptions, MethodVisitor mv, int ifStatementOpcode) {
        super(Gizmo.ASM_API_VERSION, access, name, desc, signature, exceptions);
        this.ifStatementOpcode = ifStatementOpcode;
        this.mv = mv;
    }

    @Override
    public void visitEnd() {
        var iter = instructions.iterator();
        outerLoop:
        while (iter.hasNext()) {
            var instruction = iter.next();
            if (instruction.getOpcode() == ifStatementOpcode && instruction instanceof JumpInsnNode) {
                var targetLabel = ((JumpInsnNode) instruction).label;
                dropCurrentStatement(iter);
                dropUntilTargetLabel(iter, targetLabel);
            }
        }
        accept(mv);
    }

    private void dropCurrentStatement(ListIterator<AbstractInsnNode> iter) {
        iter.remove();
        while (iter.hasPrevious()) {
            var previousInstruction = iter.previous();
            iter.remove();
            if (previousInstruction instanceof LabelNode) {
                break;
            }
        }
    }

    private void dropUntilTargetLabel(ListIterator<AbstractInsnNode> iter, LabelNode targetLabel) {
        while (iter.hasNext()) {
            var nextInstruction = iter.next();
            if (nextInstruction.equals(targetLabel)) {
                break;
            }
            iter.remove();
        }
    }
}
