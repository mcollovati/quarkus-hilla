package org.acme.hilla.test.extension;

import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.security.identity.SecurityIdentity;

import java.security.Principal;
import java.util.function.Function;

public class SpringReplacements {

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
        System.out
                .println("authenticationUtil_getSecurityHolderAuthentication");
        return CurrentIdentityAssociation.current().getPrincipal();
    }

    public static Function<String, Boolean> authenticationUtil_getSecurityHolderRoleChecker() {
        System.out.println("authenticationUtil_getSecurityHolderRoleChecker");
        SecurityIdentity identity = CurrentIdentityAssociation.current();
        if (identity == null) {
            return role -> false;
        }
        return identity::hasRole;
    }
}
