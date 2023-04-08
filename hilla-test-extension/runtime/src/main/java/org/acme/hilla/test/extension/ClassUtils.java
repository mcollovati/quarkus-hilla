package org.acme.hilla.test.extension;

public abstract class ClassUtils {

    public static Class<?> getUserClass(Class<?> clazz) {
        if (clazz.isSynthetic()) {
            Class<?> superclass = clazz.getSuperclass();
            if (superclass != null && superclass != Object.class) {
                return superclass;
            }
        }
        return clazz;
    }

}
