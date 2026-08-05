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

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import com.vaadin.flow.server.VaadinRequest;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.security.HttpSecurityUtils;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class QuarkusSecurityIdentityHolderTest {

    @Test
    void capture_storesLiveIdentityOnHttpServletRequest() {
        SecurityIdentity identity = TestSecurityIdentity.authenticated("user", "USER");
        QuarkusSecurityIdentityHolder holder = new QuarkusSecurityIdentityHolder(() -> identity);
        HttpServletRequest request = httpRequest();

        assertSame(identity, holder.capture(request));
        assertSame(identity, request.getAttribute(QuarkusSecurityIdentityHolder.REQUEST_ATTRIBUTE));
    }

    @Test
    void capture_withRoutingContextStoresRequestBoundIdentity() {
        SecurityIdentity identity = TestSecurityIdentity.authenticated("user", "USER");
        RoutingContext routingContext =
                proxy(RoutingContext.class, (proxy, method, args) -> defaultValue(method.getReturnType()));
        QuarkusSecurityIdentityHolder holder = new QuarkusSecurityIdentityHolder(() -> identity);
        HttpServletRequest request = httpRequest();

        SecurityIdentity captured = holder.capture(request, routingContext);

        assertSame(routingContext, HttpSecurityUtils.getRoutingContextAttribute(captured));
        assertSame(captured, request.getAttribute(QuarkusSecurityIdentityHolder.REQUEST_ATTRIBUTE));
    }

    @Test
    void currentIdentity_prefersCapturedVaadinRequestIdentityOverLiveIdentity() {
        SecurityIdentity capturedIdentity = TestSecurityIdentity.authenticated("captured", "USER");
        SecurityIdentity liveIdentity = TestSecurityIdentity.authenticated("live", "ADMIN");
        QuarkusSecurityIdentityHolder holder = new QuarkusSecurityIdentityHolder(() -> liveIdentity);
        VaadinRequest request = vaadinRequest(capturedIdentity);

        assertSame(capturedIdentity, holder.currentIdentity(request));
    }

    @Test
    void capture_storesPrePathIdentitySeparatelyFromTransportIdentity() {
        SecurityIdentity baseIdentity = TestSecurityIdentity.authenticated("user", "USER");
        RoutingContext routingContext = proxy(RoutingContext.class, (proxy, method, args) -> {
            if ("get".equals(method.getName())) {
                return null;
            }
            return defaultValue(method.getReturnType());
        });
        SecurityIdentity transportIdentity = new SecurityIdentityWithAttributes(
                TestSecurityIdentity.authenticated("user", "USER", "TRANSPORT_ADMIN"),
                Map.of(
                        QuarkusSecurityIdentityAugmentor.BASE_IDENTITY_ATTRIBUTE,
                        baseIdentity,
                        RoutingContext.class.getName(),
                        routingContext,
                        HttpSecurityUtils.ROUTING_CONTEXT_ATTRIBUTE,
                        routingContext));
        QuarkusSecurityIdentityHolder holder = new QuarkusSecurityIdentityHolder(() -> transportIdentity);
        HttpServletRequest request = httpRequest();

        holder.capture(request);

        assertSame(transportIdentity, request.getAttribute(QuarkusSecurityIdentityHolder.REQUEST_ATTRIBUTE));
        assertSame(baseIdentity, request.getAttribute(QuarkusSecurityIdentityHolder.NAVIGATION_REQUEST_ATTRIBUTE));
    }

    @Test
    void activate_exposesIdentityAndRestoresPreviousIdentity() {
        SecurityIdentity userIdentity = TestSecurityIdentity.authenticated("user", "USER");
        SecurityIdentity adminIdentity = TestSecurityIdentity.authenticated("admin", "ADMIN");
        QuarkusSecurityIdentityHolder holder = new QuarkusSecurityIdentityHolder(() -> null);

        try (QuarkusSecurityIdentityHolder.IdentityScope userScope = holder.activate(userIdentity)) {
            assertSame(userIdentity, holder.currentIdentity());
            try (QuarkusSecurityIdentityHolder.IdentityScope adminScope = holder.activate(adminIdentity)) {
                assertSame(adminIdentity, holder.currentIdentity());
            }
            assertSame(userIdentity, holder.currentIdentity());
        }

        assertNull(holder.currentIdentity());
    }

    private static HttpServletRequest httpRequest() {
        Map<String, Object> attributes = new HashMap<>();
        return proxy(HttpServletRequest.class, (proxy, method, args) -> {
            if ("getAttribute".equals(method.getName())) {
                return attributes.get(args[0]);
            }
            if ("setAttribute".equals(method.getName())) {
                attributes.put((String) args[0], args[1]);
                return null;
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static VaadinRequest vaadinRequest(SecurityIdentity identity) {
        return proxy(VaadinRequest.class, (proxy, method, args) -> {
            if ("getAttribute".equals(method.getName())
                    && QuarkusSecurityIdentityHolder.REQUEST_ATTRIBUTE.equals(args[0])) {
                return identity;
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
