package org.acme.hilla.test.extension;

import javax.security.auth.Subject;
import java.security.Principal;
import java.util.Collection;
import java.util.Collections;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

public abstract class SpringReplacements {

    public static Class<?> classUtils_getUserClass(Class<?> clazz) {
        if (clazz.isSynthetic()) {
            Class<?> superclass = clazz.getSuperclass();
            if (superclass != null && superclass != Object.class) {
                return superclass;
            }
        }
        return clazz;
    }
}
