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

import java.security.Principal;
import java.util.List;
import java.util.function.Predicate;

import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.auth.AccessCheckDecisionResolver;
import io.quarkus.security.identity.SecurityIdentity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuarkusNavigationAccessControlTest {

    @Test
    void authenticatedIdentity_suppliesPrincipalWhenRequestHasNone() {
        SecurityIdentity identity = identity("oidc-user", "USER");
        TestAccessControl accessControl = accessControl(identity);
        VaadinRequest request = mock(VaadinRequest.class);

        assertEquals("oidc-user", accessControl.principal(request).getName());
    }

    @Test
    void authenticatedIdentity_suppliesRolesWhenRequestHasNone() {
        SecurityIdentity identity = identity("oidc-user", "USER");
        TestAccessControl accessControl = accessControl(identity);
        VaadinRequest request = mock(VaadinRequest.class);

        Predicate<String> roles = accessControl.roles(request);

        assertTrue(roles.test("USER"));
        assertFalse(roles.test("ADMIN"));
    }

    @Test
    void requestSecurity_remainsAvailableWhenIdentityIsAnonymous() {
        SecurityIdentity identity = mock(SecurityIdentity.class);
        when(identity.isAnonymous()).thenReturn(true);
        TestAccessControl accessControl = accessControl(identity);
        VaadinRequest request = mock(VaadinRequest.class);
        Principal principal = () -> "form-user";
        when(request.getUserPrincipal()).thenReturn(principal);
        when(request.isUserInRole("USER")).thenReturn(true);

        assertEquals(principal, accessControl.principal(request));
        assertTrue(accessControl.roles(request).test("USER"));
    }

    @Test
    void anonymousIdentity_withoutRequestSecurityHasNoAccess() {
        SecurityIdentity identity = mock(SecurityIdentity.class);
        when(identity.isAnonymous()).thenReturn(true);
        TestAccessControl accessControl = accessControl(identity);

        assertNull(accessControl.principal(null));
        assertFalse(accessControl.roles(null).test("USER"));
    }

    private static TestAccessControl accessControl(SecurityIdentity identity) {
        return new TestAccessControl(identity);
    }

    private static SecurityIdentity identity(String name, String... roles) {
        SecurityIdentity identity = mock(SecurityIdentity.class);
        when(identity.isAnonymous()).thenReturn(false);
        when(identity.getPrincipal()).thenReturn((Principal) () -> name);
        for (String role : roles) {
            when(identity.hasRole(role)).thenReturn(true);
        }
        return identity;
    }

    private static final class TestAccessControl extends QuarkusNavigationAccessControl {

        private TestAccessControl(SecurityIdentity identity) {
            super(List.of(), mock(AccessCheckDecisionResolver.class), identity);
        }

        private Principal principal(VaadinRequest request) {
            return getPrincipal(request);
        }

        private Predicate<String> roles(VaadinRequest request) {
            return getRolesChecker(request);
        }
    }
}
