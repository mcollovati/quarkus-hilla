package org.acme.hilla.test.extension.deployment.asm;

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

    public static MethodSignature of(String methodOwner, String methodName) {
        return new MethodSignature(methodOwner, methodName, null);
    }

    public static MethodSignature of(String methodOwner, String methodName, String methodDescriptor) {
        return new MethodSignature(methodOwner, methodName, methodDescriptor);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodSignature that = (MethodSignature) o;
        return Objects.equals(methodOwner, that.methodOwner) && Objects.equals(methodName, that.methodName) && Objects.equals(methodDescriptor, that.methodDescriptor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(methodOwner, methodName, methodDescriptor);
    }
}
