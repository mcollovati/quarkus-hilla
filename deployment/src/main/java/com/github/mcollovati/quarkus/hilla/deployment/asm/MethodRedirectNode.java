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
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

class MethodRedirectNode extends MethodNode {
    private final Map<MethodSignature, MethodSignature> redirects;

    protected MethodRedirectNode(
            int api,
            int access,
            String name,
            String descriptor,
            String signature,
            String[] exceptions,
            MethodVisitor mv,
            Map<MethodSignature, MethodSignature> redirects) {
        super(api, access, name, descriptor, signature, exceptions);
        this.mv = mv;
        this.redirects = redirects;
    }

    @Override
    public void visitEnd() {
        final var iterator = instructions.iterator();
        while (iterator.hasNext()) {
            final var instruction = iterator.next();
            if (instruction instanceof MethodInsnNode) {
                redirects.entrySet().stream()
                        .filter(e -> AsmUtils.hasMethodInsnSignature(e.getKey(), (MethodInsnNode) instruction))
                        .findFirst()
                        .ifPresent(mvr -> {
                            if (MethodSignature.DROP_METHOD.equals(mvr.getValue())) drop(iterator);
                            else redirect((MethodInsnNode) instruction, mvr.getValue());
                        });
            }
        }
        accept(mv);
    }

    private void drop(ListIterator<AbstractInsnNode> iterator) {
        final var labelsToKeep = tryCatchBlocks.stream()
                .flatMap(block -> Stream.of(block.start, block.end, block.handler))
                .collect(Collectors.toUnmodifiableSet());
        AsmUtils.deleteStatement(iterator, labelsToKeep);
    }

    private void redirect(MethodInsnNode methodInsnNode, MethodSignature targetMethod) {
        methodInsnNode.owner = targetMethod.getOwner();
        methodInsnNode.name = targetMethod.getName();
        if (targetMethod.getDescriptor() != null) methodInsnNode.desc = targetMethod.getDescriptor();
    }
}
