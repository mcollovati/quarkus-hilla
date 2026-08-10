/*
 * Copyright 2026 Marco Collovati, Dario Götze
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
package com.github.mcollovati.quarkus.hilla.security;

import jakarta.enterprise.inject.Instance;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.function.Function;
import java.util.function.Supplier;

import com.vaadin.flow.server.auth.AccessAnnotationChecker;
import com.vaadin.hilla.auth.EndpointAccessChecker;
import io.quarkus.security.identity.SecurityIdentity;

/**
 * Endpoint access checker that evaluates Hilla endpoint annotations against the
 * Quarkus request {@link SecurityIdentity}.
 */
public class QuarkusEndpointAccessChecker extends EndpointAccessChecker {

    private final transient QuarkusSecurityIdentityHolder identityHolder;

    public QuarkusEndpointAccessChecker(
            AccessAnnotationChecker accessAnnotationChecker, Instance<SecurityIdentity> securityIdentity) {
        this(accessAnnotationChecker, new QuarkusSecurityIdentityHolder(securityIdentity::get));
    }

    public QuarkusEndpointAccessChecker(
            AccessAnnotationChecker accessAnnotationChecker, QuarkusSecurityIdentityHolder identityHolder) {
        super(accessAnnotationChecker);
        this.identityHolder = identityHolder;
    }

    QuarkusEndpointAccessChecker(
            AccessAnnotationChecker accessAnnotationChecker, Supplier<SecurityIdentity> securityIdentity) {
        super(accessAnnotationChecker);
        this.identityHolder = new QuarkusSecurityIdentityHolder(securityIdentity);
    }

    @Override
    public String check(Method method, HttpServletRequest request) {
        SecurityIdentity identity = identityHolder.currentIdentity(request);
        return super.check(
                method, principal(identity, request.getUserPrincipal()), rolesChecker(identity, request::isUserInRole));
    }

    @Override
    public String check(Class<?> clazz, HttpServletRequest request) {
        SecurityIdentity identity = identityHolder.currentIdentity(request);
        return super.check(
                clazz, principal(identity, request.getUserPrincipal()), rolesChecker(identity, request::isUserInRole));
    }

    @Override
    public String check(Method method, Principal principal, Function<String, Boolean> rolesChecker) {
        SecurityIdentity identity = currentIdentity();
        return super.check(method, principal(identity, principal), rolesChecker(identity, rolesChecker));
    }

    @Override
    public String check(Class<?> clazz, Principal principal, Function<String, Boolean> rolesChecker) {
        SecurityIdentity identity = currentIdentity();
        return super.check(clazz, principal(identity, principal), rolesChecker(identity, rolesChecker));
    }

    private static Principal principal(SecurityIdentity identity, Principal fallback) {
        if (identity == null) {
            return fallback;
        }
        return identity.isAnonymous() ? null : identity.getPrincipal();
    }

    private static Function<String, Boolean> rolesChecker(
            SecurityIdentity identity, Function<String, Boolean> fallback) {
        if (identity == null) {
            return fallback;
        }
        if (identity.isAnonymous()) {
            return role -> false;
        }
        return role -> role != null && identity.hasRole(role);
    }

    private SecurityIdentity currentIdentity() {
        return identityHolder.capturedIdentity();
    }
}
