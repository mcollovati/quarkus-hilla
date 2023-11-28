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

import java.util.Objects;

public class MethodSignature {

    public static final MethodSignature DROP_METHOD = new MethodSignature(null, null, null);

    private final String methodOwner;
    private final String methodName;
    private final String methodDescriptor;

    public MethodSignature(String methodOwner, String methodName, String methodDescriptor) {
        this.methodOwner = methodOwner;
        this.methodName = methodName;
        this.methodDescriptor = methodDescriptor;
    }

    public String getOwner() {
        return methodOwner;
    }

    public String getName() {
        return methodName;
    }

    public String getDescriptor() {
        return methodDescriptor;
    }

    public static MethodSignature of(Class<?> methodOwner, String methodName) {
        return of(methodOwner.getName().replace('.', '/'), methodName);
    }

    public static MethodSignature of(String methodOwner, String methodName) {
        return new MethodSignature(methodOwner, methodName, null);
    }

    public static MethodSignature of(Class<?> methodOwner, String methodName, String methodDescriptor) {
        return of(methodOwner.getName().replace('.', '/'), methodName, methodDescriptor);
    }

    public static MethodSignature of(String methodOwner, String methodName, String methodDescriptor) {
        return new MethodSignature(methodOwner, methodName, methodDescriptor);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodSignature that = (MethodSignature) o;
        return Objects.equals(methodOwner, that.methodOwner)
                && Objects.equals(methodName, that.methodName)
                && Objects.equals(methodDescriptor, that.methodDescriptor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(methodOwner, methodName, methodDescriptor);
    }
}
