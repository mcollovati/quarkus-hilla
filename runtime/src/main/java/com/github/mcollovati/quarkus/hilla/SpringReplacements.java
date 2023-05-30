package com.github.mcollovati.quarkus.hilla;

import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.security.identity.SecurityIdentity;
import java.security.Principal;
import java.util.function.Function;

public class SpringReplacements {

    public static Class<?> classUtils_getUserClass(Object object) {
        return classUtils_getUserClass(object.getClass());
    }

    public static Class<?> classUtils_getUserClass(Class<?> clazz) {
        if (clazz.isSynthetic()) {
            Class<?> superclass = clazz.getSuperclass();
            if (superclass != null && superclass != Object.class) {
                return superclass;
            }
        }
        return clazz;
    }

    public static Principal authenticationUtil_getSecurityHolderAuthentication() {
        SecurityIdentity identity = CurrentIdentityAssociation.current();
        if (identity != null && !identity.isAnonymous()) {
            return identity.getPrincipal();
        }
        return null;
    }

    public static Function<String, Boolean> authenticationUtil_getSecurityHolderRoleChecker() {
        SecurityIdentity identity = CurrentIdentityAssociation.current();
        if (identity == null || identity.isAnonymous()) {
            return role -> false;
        }
        return role -> role != null && identity.hasRole(role);
    }
}
