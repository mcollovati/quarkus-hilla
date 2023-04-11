package org.acme.hilla.test.extension;

import javax.security.auth.Subject;
import java.security.Principal;
import java.util.Collection;
import java.util.Collections;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;

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

    public static void securityContextHolder_setContext(SecurityContext ctx) {
        // DO nothing
        System.out.println("=========== Ignore securityContextHolder_setContext " + ctx);
    }

    public static void securityContextHolder_clearContext() {
        // DO nothing
        System.out.println("=========== Ignore securityContextHolder_clearContext");
    }

    public static SecurityContextImpl securityContextImpl_ctor(Object auth) {
        return new SecurityContextImpl();
    }

    static class AuthenticationWrapper implements Authentication, Principal {
        private final Principal principal;

        public AuthenticationWrapper(Principal principal) {
            this.principal = principal;
        }

        @Override
        public boolean equals(Object another) {
            return principal.equals(another);
        }

        @Override
        public String toString() {
            return principal.toString();
        }

        @Override
        public int hashCode() {
            return principal.hashCode();
        }

        @Override
        public String getName() {
            return principal.getName();
        }

        @Override
        public boolean implies(Subject subject) {
            return principal.implies(subject);
        }

        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            return Collections.emptyList();
        }

        @Override
        public Object getCredentials() {
            return null;
        }

        @Override
        public Object getDetails() {
            return null;
        }

        @Override
        public Object getPrincipal() {
            return principal;
        }

        @Override
        public boolean isAuthenticated() {
            return principal != null;
        }

        @Override
        public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
            throw new UnsupportedOperationException("Not supported");
        }
    }
}
