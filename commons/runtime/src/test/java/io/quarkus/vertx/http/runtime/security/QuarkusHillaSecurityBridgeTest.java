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
package io.quarkus.vertx.http.runtime.security;

import jakarta.enterprise.inject.Instance;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.spi.runtime.BlockingSecurityExecutor;
import io.quarkus.vertx.http.runtime.AuthRuntimeConfig;
import io.quarkus.vertx.http.runtime.VertxHttpBuildTimeConfig;
import io.quarkus.vertx.http.runtime.VertxHttpConfig;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class QuarkusHillaSecurityBridgeTest {

    @Test
    void bridge_usesQuarkusPathMatchingAndAuthenticationMechanismModel() {
        PathMatchingHttpSecurityPolicy policy = pathMatchingPolicy();
        RoutingContext matched = routingContext("/secure");
        RoutingContext unmatched = routingContext("/other");
        HttpSecurityPolicy.AuthorizationRequestContext requestContext =
                QuarkusHillaSecurityBridge.authorizationRequestContext(blockingExecutor(null));
        SecurityIdentity identity = mock(SecurityIdentity.class);

        assertTrue(QuarkusHillaSecurityBridge.requiredAuthenticationMechanisms(policy, matched)
                .contains("oidc"));
        assertTrue(policy.checkPermission(matched, Uni.createFrom().item(identity), requestContext)
                .await()
                .indefinitely()
                .isPermitted());
        assertTrue(QuarkusHillaSecurityBridge.pathPolicyApplied(matched));

        assertTrue(policy.checkPermission(unmatched, Uni.createFrom().item(identity), requestContext)
                .await()
                .indefinitely()
                .isPermitted());
        assertFalse(QuarkusHillaSecurityBridge.pathPolicyApplied(unmatched));
    }

    @Test
    void authorizationRequestContext_usesProvidedBlockingExecutor() {
        AtomicBoolean invoked = new AtomicBoolean();
        BlockingSecurityExecutor executor = blockingExecutor(invoked);
        RoutingContext context = routingContext("/secure");
        SecurityIdentity identity = mock(SecurityIdentity.class);

        QuarkusHillaSecurityBridge.authorizationRequestContext(executor)
                .runBlocking(
                        context,
                        Uni.createFrom().item(identity),
                        (request, value) -> HttpSecurityPolicy.CheckResult.PERMIT)
                .await()
                .indefinitely();

        assertTrue(invoked.get());
    }

    @Test
    void prepareTargetAuthentication_clearsTransportUserBeforeSelectedMechanismRuns() {
        RoutingContext context = routingContext("/secure");
        context.setUser(mock(User.class));

        QuarkusHillaSecurityBridge.prepareTargetAuthentication(context, pathMatchingPolicy());

        assertNull(context.user());
    }

    private static BlockingSecurityExecutor blockingExecutor(AtomicBoolean invoked) {
        return new BlockingSecurityExecutor() {
            @Override
            public <T> Uni<T> executeBlocking(Supplier<? extends T> action) {
                if (invoked != null) {
                    invoked.set(true);
                }
                return Uni.createFrom().item(action.get());
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static PathMatchingHttpSecurityPolicy pathMatchingPolicy() {
        Instance<HttpSecurityPolicy> installedPolicies = mock(Instance.class);
        when(installedPolicies.handles()).thenReturn(List.of());
        AuthRuntimeConfig authRuntimeConfig = mock(AuthRuntimeConfig.class);
        when(authRuntimeConfig.rolePolicy()).thenReturn(Map.of());
        VertxHttpConfig httpConfig = mock(VertxHttpConfig.class);
        when(httpConfig.auth()).thenReturn(authRuntimeConfig);
        VertxHttpBuildTimeConfig buildTimeConfig = mock(VertxHttpBuildTimeConfig.class);
        when(buildTimeConfig.rootPath()).thenReturn("/");
        HttpSecurityConfiguration configuration = mock(HttpSecurityConfiguration.class);
        when(configuration.httpPermissions()).thenReturn(List.of(permission()));

        try (MockedStatic<HttpSecurityConfiguration> mockedConfiguration =
                mockStatic(HttpSecurityConfiguration.class)) {
            mockedConfiguration.when(HttpSecurityConfiguration::get).thenReturn(configuration);
            return new PathMatchingHttpSecurityPolicy(httpConfig, buildTimeConfig, installedPolicies);
        }
    }

    private static HttpSecurityConfiguration.HttpPermissionCarrier permission() {
        return new HttpSecurityConfiguration.HttpPermissionCarrier() {
            @Override
            public Set<String> getPaths() {
                return Set.of("/secure");
            }

            @Override
            public boolean isShared() {
                return false;
            }

            @Override
            public boolean shouldApplyToJaxRs() {
                return false;
            }

            @Override
            public Set<String> getMethods() {
                return Set.of();
            }

            @Override
            public HttpSecurityConfiguration.AuthenticationMechanisms getAuthMechanisms() {
                return new HttpSecurityConfiguration.AuthenticationMechanisms("OIDC");
            }

            @Override
            public HttpSecurityConfiguration.Policy getPolicy() {
                return new HttpSecurityConfiguration.Policy(null, new PermitSecurityPolicy());
            }
        };
    }

    private static RoutingContext routingContext(String path) {
        RoutingContext context = mock(RoutingContext.class);
        HttpServerRequest request = mock(HttpServerRequest.class);
        Map<String, Object> data = new HashMap<>();
        AtomicReference<User> user = new AtomicReference<>();
        when(context.normalizedPath()).thenReturn(path);
        when(context.request()).thenReturn(request);
        when(request.method()).thenReturn(new HttpMethod("GET"));
        when(context.get(anyString())).thenAnswer(invocation -> data.get(invocation.getArgument(0)));
        when(context.put(anyString(), org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            data.put(invocation.getArgument(0), invocation.getArgument(1));
            return context;
        });
        when(context.user()).thenAnswer(ignored -> user.get());
        org.mockito.Mockito.doAnswer(invocation -> {
                    user.set(invocation.getArgument(0));
                    return null;
                })
                .when(context)
                .setUser(org.mockito.ArgumentMatchers.nullable(User.class));
        return context;
    }
}
