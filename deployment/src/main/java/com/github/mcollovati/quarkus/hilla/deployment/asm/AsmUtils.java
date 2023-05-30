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
}
