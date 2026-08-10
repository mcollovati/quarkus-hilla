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

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ContextNotActiveException;
import java.security.Principal;

import com.vaadin.flow.server.auth.AccessAnnotationChecker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class QuarkusEndpointAccessCheckerTest {

    @Test
    void check_rolesAllowedUsesSecurityIdentityRolesWhenRequestRoleCheckerFails() throws Exception {
        QuarkusSecurityIdentityHolder identityHolder = new QuarkusSecurityIdentityHolder(() -> null);
        QuarkusEndpointAccessChecker checker =
                new QuarkusEndpointAccessChecker(new AccessAnnotationChecker(), identityHolder);

        String result;
        try (QuarkusSecurityIdentityHolder.IdentityScope ignored =
                identityHolder.activate(TestSecurityIdentity.authenticated("user", "USER"))) {
            result = checker.check(SecuredEndpoint.class.getMethod("userOnly"), null, role -> false);
        }

        assertNull(result);
    }

    @Test
    void check_permitAllUsesSecurityIdentityPrincipalWhenRequestPrincipalMissing() throws Exception {
        QuarkusSecurityIdentityHolder identityHolder = new QuarkusSecurityIdentityHolder(() -> null);
        QuarkusEndpointAccessChecker checker =
                new QuarkusEndpointAccessChecker(new AccessAnnotationChecker(), identityHolder);

        String result;
        try (QuarkusSecurityIdentityHolder.IdentityScope ignored =
                identityHolder.activate(TestSecurityIdentity.authenticated("user", "USER"))) {
            result = checker.check(SecuredEndpoint.class.getMethod("authenticated"), null, role -> false);
        }

        assertNull(result);
    }

    @Test
    void check_anonymousSecurityIdentityOverridesFallbackRequestUser() throws Exception {
        QuarkusSecurityIdentityHolder identityHolder = new QuarkusSecurityIdentityHolder(() -> null);
        QuarkusEndpointAccessChecker checker =
                new QuarkusEndpointAccessChecker(new AccessAnnotationChecker(), identityHolder);

        String result;
        try (QuarkusSecurityIdentityHolder.IdentityScope ignored =
                identityHolder.activate(TestSecurityIdentity.anonymous())) {
            result = checker.check(SecuredEndpoint.class.getMethod("userOnly"), principal("user"), role -> true);
        }

        assertNotNull(result);
    }

    @Test
    void check_fallsBackToRequestUserWhenSecurityIdentityUnavailable() throws Exception {
        QuarkusEndpointAccessChecker checker = checkerWithoutIdentity();

        String result = checker.check(
                SecuredEndpoint.class.getMethod("userOnly"), principal("user"), role -> "USER".equals(role));

        assertNull(result);
    }

    @Test
    void check_rolesAllowedUsesCapturedIdentityWhenLiveSecurityIdentityUnavailable() throws Exception {
        QuarkusSecurityIdentityHolder identityHolder = new QuarkusSecurityIdentityHolder(() -> {
            throw new ContextNotActiveException("request context not active");
        });
        QuarkusEndpointAccessChecker checker =
                new QuarkusEndpointAccessChecker(new AccessAnnotationChecker(), identityHolder);

        try (QuarkusSecurityIdentityHolder.IdentityScope ignored =
                identityHolder.activate(TestSecurityIdentity.authenticated("user", "USER"))) {
            String result = checker.check(SecuredEndpoint.class.getMethod("userOnly"), null, role -> false);

            assertNull(result);
        }
    }

    private static QuarkusEndpointAccessChecker checkerWithoutIdentity() {
        return new QuarkusEndpointAccessChecker(new AccessAnnotationChecker(), () -> {
            throw new ContextNotActiveException("request context not active");
        });
    }

    private static Principal principal(String name) {
        return () -> name;
    }

    static class SecuredEndpoint {

        @PermitAll
        public void authenticated() {}

        @RolesAllowed("USER")
        public void userOnly() {}
    }
}
