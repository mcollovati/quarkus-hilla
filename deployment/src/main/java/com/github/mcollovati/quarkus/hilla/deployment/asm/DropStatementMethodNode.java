/*
 * Copyright 2023 Marco Collovati, Dario Götze
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.mcollovati.quarkus.hilla.deployment.asm;

import java.util.ListIterator;
import java.util.function.Predicate;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;

public class DropStatementMethodNode extends MethodNode {
    protected Predicate<AbstractInsnNode> dropPredicate;

    public DropStatementMethodNode(
            int api,
            int access,
            String name,
            String descriptor,
            String signature,
            String[] exceptions,
            MethodVisitor mv,
            Predicate<AbstractInsnNode> dropPredicate) {
        super(api, access, name, descriptor, signature, exceptions);
        this.dropPredicate = dropPredicate;
        this.mv = mv;
    }

    @Override
    public void visitEnd() {
        var iter = instructions.iterator();
        while (iter.hasNext()) {
            var instruction = findNextDropInstruction(iter);
            if (instruction == null) break;
            iter.remove();
            // If we match against a label, then there is no previous instruction to remove
            if (!(instruction instanceof LabelNode)) removeUntilPreviousLabel(iter);
            removeUntilNextLabel(iter);
        }
        accept(mv);
    }

    private AbstractInsnNode findNextDropInstruction(ListIterator<AbstractInsnNode> iter) {
        while (iter.hasNext()) {
            var instruction = iter.next();
            if (dropPredicate.test(instruction)) {
                return instruction;
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
