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

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;

public class AsmUtils {

    public static boolean hasMethodInsnSignature(MethodSignature srcMethod, MethodInsnNode methodNode) {
        return hasMethodInsnSignature(srcMethod, methodNode.owner, methodNode.name, methodNode.desc);
    }

    public static boolean hasMethodInsnSignature(
            MethodSignature srcMethod, String owner, String name, String descriptor) {
        return srcMethod.getOwner().equals(owner)
                && srcMethod.getName().equals(name)
                && (srcMethod.getDescriptor() == null
                        || srcMethod.getDescriptor().equals(descriptor));
    }

    public static <T extends AbstractInsnNode> T findNextInsnNode(
            ListIterator<AbstractInsnNode> iterator, Predicate<T> predicate, Class<T> clazz) {
        return clazz.cast(findNextInsnNode(iterator, node -> clazz.isInstance(node) && predicate.test((T) node)));
    }

    public static AbstractInsnNode findNextInsnNode(
            ListIterator<AbstractInsnNode> iterator, Predicate<AbstractInsnNode> predicate) {
        while (iterator.hasNext()) {
            final var instruction = iterator.next();
            if (predicate.test(instruction)) {
                return instruction;
            }
        }
        return null;
    }

    /**
     * It is expected, that the last call to the iterator was next(), otherwise the previous bytecode block is deleted if pointing at a LabelNode.
     * @param iterator the iterator
     * @param keepStartLabel should the start label be kept, e.g. for try-catch blocks
     */
    public static void deleteStatement(ListIterator<AbstractInsnNode> iterator, boolean keepStartLabel) {
        removeUntilPreviousLabel(iterator, keepStartLabel);
        removeUntilNextLabel(iterator);
    }

    private static void removeUntilPreviousLabel(ListIterator<AbstractInsnNode> iter, boolean keepStartLabel) {
        while (iter.hasPrevious()) {
            final var instruction = iter.previous();
            if (instruction instanceof LabelNode) {
                if (keepStartLabel) {
                    // If label should be preserved, move to next node
                    // otherwise removeUntilNextLabel will exit immediately
                    iter.next();
                } else {
                    iter.remove();
                }
                break;
            } else iter.remove();
        }
    }

    private static void removeUntilNextLabel(ListIterator<AbstractInsnNode> iter) {
        while (iter.hasNext()) {
            final var instruction = iter.next();
            if (instruction instanceof LabelNode) {
                break;
            }
            iter.remove();
        }
    }
}
