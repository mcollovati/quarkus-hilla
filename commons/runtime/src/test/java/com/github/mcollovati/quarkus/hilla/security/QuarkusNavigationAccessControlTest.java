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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.security.Principal;
import java.util.List;
import java.util.function.Predicate;

import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.auth.DefaultAccessCheckDecisionResolver;
import com.vaadin.flow.server.auth.NavigationAccessChecker;
import io.quarkus.security.identity.SecurityIdentity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuarkusNavigationAccessControlTest {

    @Test
    void getPrincipalAndRolesChecker_usePrePathIdentity() {
        SecurityIdentity navigationIdentity = TestSecurityIdentity.authenticated("user", "USER");
        SecurityIdentity transportIdentity = TestSecurityIdentity.authenticated("user", "USER", "TRANSPORT_ADMIN");
        TestNavigationAccessControl accessControl =
                new TestNavigationAccessControl(new QuarkusSecurityIdentityHolder(() -> null));
        VaadinRequest request = vaadinRequest(transportIdentity, navigationIdentity);

        Principal principal = accessControl.principal(request);
        Predicate<String> rolesChecker = accessControl.rolesChecker(request);

        assertEquals("user", principal.getName());
        assertTrue(rolesChecker.test("USER"));
        org.junit.jupiter.api.Assertions.assertFalse(rolesChecker.test("TRANSPORT_ADMIN"));
    }

    private static final class TestNavigationAccessControl extends QuarkusNavigationAccessControl {

        private TestNavigationAccessControl(QuarkusSecurityIdentityHolder identityHolder) {
            super(List.<NavigationAccessChecker>of(), new DefaultAccessCheckDecisionResolver(), identityHolder);
        }

        private Principal principal(VaadinRequest request) {
            return getPrincipal(request);
        }

        private Predicate<String> rolesChecker(VaadinRequest request) {
            return getRolesChecker(request);
        }
    }

    private static VaadinRequest vaadinRequest(
            SecurityIdentity transportIdentity, SecurityIdentity navigationIdentity) {
        return proxy(VaadinRequest.class, (proxy, method, args) -> {
            if ("getAttribute".equals(method.getName())
                    && QuarkusSecurityIdentityHolder.REQUEST_ATTRIBUTE.equals(args[0])) {
                return transportIdentity;
            }
            if ("getAttribute".equals(method.getName())
                    && QuarkusSecurityIdentityHolder.NAVIGATION_REQUEST_ATTRIBUTE.equals(args[0])) {
                return navigationIdentity;
            }
            if ("isUserInRole".equals(method.getName())) {
                return false;
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (boolean.class.equals(type)) {
            return false;
        }
        if (void.class.equals(type)) {
            return null;
        }
        return 0;
    }
}
