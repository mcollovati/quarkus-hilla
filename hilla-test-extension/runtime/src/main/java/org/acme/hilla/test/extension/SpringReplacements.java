package org.acme.hilla.test.extension;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

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
        System.out.println("authenticationUtil_getSecurityHolderAuthentication");
        Authentication authentication = SecurityContextHolder.getContext()
                .getAuthentication();
        if (authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }

        return authentication;

    }

    public static Function<String, Boolean> authenticationUtil_getSecurityHolderRoleChecker() {
        System.out.println("authenticationUtil_getSecurityHolderRoleChecker");
        Principal authentication = authenticationUtil_getSecurityHolderAuthentication();
        if (authentication == null) {
            return role -> false;
        }
        return role -> true;
    }
}
