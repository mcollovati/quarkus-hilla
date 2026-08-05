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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.security.AbstractPathMatchingHttpSecurityPolicy;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticator;
import io.quarkus.vertx.http.runtime.security.HttpSecurityPolicy;
import io.quarkus.vertx.http.runtime.security.HttpSecurityUtils;
import io.quarkus.vertx.http.runtime.security.PathMatchingHttpSecurityPolicy;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuarkusAccessPathCheckerTest {

    @Test
    void check_onEventLoopDeniesBeforeInvokingPolicies() throws Exception {
        AtomicBoolean invoked = new AtomicBoolean();
        HttpSecurityPolicy policy = (request, identity, requestContext) -> {
            invoked.set(true);
            return HttpSecurityPolicy.CheckResult.permit();
        };
        QuarkusAccessPathChecker checker = checker(() -> null, List.of(policy));
        Vertx vertx = Vertx.vertx();
        try {
            CompletableFuture<QuarkusAccessPathChecker.AccessCheck> result = new CompletableFuture<>();

            vertx.runOnContext(ignored -> result.complete(checker.check("/secure", null, role -> false)));

            assertEquals(
                    QuarkusAccessPathChecker.Decision.DENY,
                    result.get(5, TimeUnit.SECONDS).decision());
            assertFalse(invoked.get());
        } finally {
            vertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void check_usesPrePathIdentityInsteadOfTransportAugmentation() {
        RoutingContext transportContext = transportContext();
        SecurityIdentity baseIdentity = TestSecurityIdentity.authenticated("user", "USER");
        SecurityIdentity transportIdentity = transportIdentity(
                TestSecurityIdentity.authenticated("user", "USER", "TRANSPORT_ADMIN"), baseIdentity, transportContext);
        AtomicReference<SecurityIdentity> evaluatedIdentity = new AtomicReference<>();
        HttpSecurityPolicy pathPolicy = (request, identity, requestContext) -> identity.map(value -> {
            evaluatedIdentity.set(value);
            request.put(AbstractPathMatchingHttpSecurityPolicy.class.getName() + ".POLICY_FOUND", true);
            return HttpSecurityPolicy.CheckResult.PERMIT;
        });
        QuarkusAccessPathChecker checker = checker(() -> transportIdentity, List.of(pathPolicy));

        QuarkusAccessPathChecker.AccessCheck result =
                checker.check("/secure", baseIdentity.getPrincipal(), baseIdentity::hasRole);

        assertEquals(QuarkusAccessPathChecker.Decision.ALLOW, result.decision());
        assertEquals(Set.of("USER"), evaluatedIdentity.get().getRoles());
    }

    @Test
    void check_globalPolicyDenyIsAppliedWithoutPathMatch() {
        RoutingContext transportContext = transportContext();
        SecurityIdentity baseIdentity = TestSecurityIdentity.authenticated("user", "USER");
        SecurityIdentity transportIdentity = transportIdentity(baseIdentity, baseIdentity, transportContext);
        HttpSecurityPolicy globalDeny = (request, identity, requestContext) -> HttpSecurityPolicy.CheckResult.deny();
        QuarkusAccessPathChecker checker = checker(() -> transportIdentity, List.of(globalDeny));

        QuarkusAccessPathChecker.AccessCheck result =
                checker.check("/unmatched", baseIdentity.getPrincipal(), baseIdentity::hasRole);

        assertEquals(QuarkusAccessPathChecker.Decision.DENY, result.decision());
        assertSame(baseIdentity, QuarkusSecurityIdentityAugmentor.baseIdentity(transportIdentity));
    }

    @Test
    void check_resolvesNavigationPathAgainstQuarkusRootPath() {
        RoutingContext transportContext = transportContext();
        SecurityIdentity baseIdentity = TestSecurityIdentity.authenticated("user", "USER");
        SecurityIdentity transportIdentity = transportIdentity(baseIdentity, baseIdentity, transportContext);
        AtomicReference<String> evaluatedPath = new AtomicReference<>();
        HttpSecurityPolicy pathPolicy = (request, identity, requestContext) -> identity.map(value -> {
            evaluatedPath.set(request.normalizedPath());
            request.put(AbstractPathMatchingHttpSecurityPolicy.class.getName() + ".POLICY_FOUND", true);
            return HttpSecurityPolicy.CheckResult.PERMIT;
        });
        QuarkusAccessPathChecker checker = checker(() -> transportIdentity, List.of(pathPolicy), "/app/");

        QuarkusAccessPathChecker.AccessCheck result =
                checker.check("/secure", baseIdentity.getPrincipal(), baseIdentity::hasRole);

        assertEquals(QuarkusAccessPathChecker.Decision.ALLOW, result.decision());
        assertEquals("/app/secure", evaluatedPath.get());
    }

    @Test
    void check_doesNotCopyTransportPolicyMemoizationIntoTargetContext() {
        RoutingContext transportContext = transportContext();
        transportContext.put("stateful-policy-checked", true);
        SecurityIdentity baseIdentity = TestSecurityIdentity.authenticated("user", "USER");
        SecurityIdentity transportIdentity = transportIdentity(baseIdentity, baseIdentity, transportContext);
        AtomicBoolean syntheticTargetSeen = new AtomicBoolean();
        HttpSecurityPolicy statefulPolicy = (request, identity, requestContext) -> {
            syntheticTargetSeen.set(
                    Boolean.TRUE.equals(request.get(QuarkusAccessPathChecker.SYNTHETIC_NAVIGATION_ATTRIBUTE)));
            if (Boolean.TRUE.equals(request.get("stateful-policy-checked"))) {
                return HttpSecurityPolicy.CheckResult.permit();
            }
            request.put("stateful-policy-checked", true);
            return HttpSecurityPolicy.CheckResult.deny();
        };
        HttpSecurityPolicy pathPolicy = (request, identity, requestContext) -> identity.map(value -> {
            request.put(AbstractPathMatchingHttpSecurityPolicy.class.getName() + ".POLICY_FOUND", true);
            return HttpSecurityPolicy.CheckResult.PERMIT;
        });
        QuarkusAccessPathChecker checker = checker(() -> transportIdentity, List.of(statefulPolicy, pathPolicy));

        QuarkusAccessPathChecker.AccessCheck result =
                checker.check("/admin", baseIdentity.getPrincipal(), baseIdentity::hasRole);

        assertEquals(QuarkusAccessPathChecker.Decision.DENY, result.decision());
        assertTrue(syntheticTargetSeen.get());
    }

    @Test
    void check_withoutExplicitMechanismReauthenticatesForTargetPath() {
        RoutingContext transportContext = transportContext();
        SecurityIdentity baseIdentity = TestSecurityIdentity.authenticated("user", "ADMIN");
        SecurityIdentity transportIdentity = transportIdentity(baseIdentity, baseIdentity, transportContext);
        HttpAuthenticator authenticator = mock(HttpAuthenticator.class);
        when(authenticator.attemptAuthentication(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Uni.createFrom().nullItem());
        PathMatchingHttpSecurityPolicy pathMatchingPolicy = mock(PathMatchingHttpSecurityPolicy.class);
        QuarkusAccessPathChecker checker = new QuarkusAccessPathChecker(
                new QuarkusSecurityIdentityHolder(() -> transportIdentity),
                List.of(),
                pathMatchingPolicy,
                authenticator,
                (context, identityUni, function) -> identityUni.map(value -> function.apply(context, value)),
                () -> runtimeConfiguration("/"));

        QuarkusAccessPathChecker.AccessCheck result =
                checker.check("/tenant-b/admin", baseIdentity.getPrincipal(), baseIdentity::hasRole);

        assertEquals(QuarkusAccessPathChecker.Decision.DENY, result.decision());
        assertEquals("target authentication could not reproduce the transport identity", result.policyName());
        verify(authenticator).attemptAuthentication(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void check_successfulAuthenticationSkipsDiagnosticMechanismLookup() {
        RoutingContext transportContext = transportContext();
        SecurityIdentity baseIdentity = TestSecurityIdentity.authenticated("user", "USER");
        SecurityIdentity transportIdentity = transportIdentity(baseIdentity, baseIdentity, transportContext);
        HttpAuthenticator authenticator = mock(HttpAuthenticator.class);
        when(authenticator.attemptAuthentication(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Uni.createFrom().item(baseIdentity));
        PathMatchingHttpSecurityPolicy pathMatchingPolicy = mock(PathMatchingHttpSecurityPolicy.class, invocation -> {
            if ("getAuthMechanisms".equals(invocation.getMethod().getName())) {
                throw new IllegalStateException("diagnostic lookup must not run");
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        HttpSecurityPolicy pathPolicy = (request, identity, requestContext) -> identity.map(value -> {
            request.put(AbstractPathMatchingHttpSecurityPolicy.class.getName() + ".POLICY_FOUND", true);
            return HttpSecurityPolicy.CheckResult.PERMIT;
        });
        QuarkusAccessPathChecker checker = new QuarkusAccessPathChecker(
                new QuarkusSecurityIdentityHolder(() -> transportIdentity),
                List.of(pathPolicy),
                pathMatchingPolicy,
                authenticator,
                (context, identityUni, function) -> identityUni.map(value -> function.apply(context, value)),
                () -> runtimeConfiguration("/"));

        QuarkusAccessPathChecker.AccessCheck result =
                checker.check("/secure", baseIdentity.getPrincipal(), baseIdentity::hasRole);

        assertEquals(QuarkusAccessPathChecker.Decision.ALLOW, result.decision());
    }

    @Test
    void check_failedDiagnosticMechanismLookupDeniesClosed() {
        RoutingContext transportContext = transportContext();
        SecurityIdentity baseIdentity = TestSecurityIdentity.authenticated("user", "USER");
        SecurityIdentity transportIdentity = transportIdentity(baseIdentity, baseIdentity, transportContext);
        HttpAuthenticator authenticator = mock(HttpAuthenticator.class);
        when(authenticator.attemptAuthentication(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Uni.createFrom().nullItem());
        PathMatchingHttpSecurityPolicy pathMatchingPolicy = mock(PathMatchingHttpSecurityPolicy.class, invocation -> {
            if ("getAuthMechanisms".equals(invocation.getMethod().getName())) {
                throw new IllegalStateException("diagnostic lookup failed");
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        QuarkusAccessPathChecker checker = new QuarkusAccessPathChecker(
                new QuarkusSecurityIdentityHolder(() -> transportIdentity),
                List.of(),
                pathMatchingPolicy,
                authenticator,
                (context, identityUni, function) -> identityUni.map(value -> function.apply(context, value)),
                () -> runtimeConfiguration("/"));

        QuarkusAccessPathChecker.AccessCheck result =
                checker.check("/secure", baseIdentity.getPrincipal(), baseIdentity::hasRole);

        assertEquals(QuarkusAccessPathChecker.Decision.DENY, result.decision());
        assertEquals("target policy evaluation failed", result.policyName());
    }

    @Test
    void check_exposesWrappedTargetIdentityAndCanonicalHttpMethodToPolicy() {
        RoutingContext transportContext = transportContext();
        SecurityIdentity baseIdentity = TestSecurityIdentity.authenticated("user", "USER");
        SecurityIdentity transportIdentity = transportIdentity(baseIdentity, baseIdentity, transportContext);
        AtomicReference<io.vertx.ext.auth.User> evaluatedUser = new AtomicReference<>();
        AtomicReference<HttpMethod> evaluatedMethod = new AtomicReference<>();
        HttpSecurityPolicy pathPolicy = (request, identity, requestContext) -> identity.map(value -> {
            evaluatedUser.set(request.user());
            evaluatedMethod.set(request.request().method());
            request.put(AbstractPathMatchingHttpSecurityPolicy.class.getName() + ".POLICY_FOUND", true);
            return HttpSecurityPolicy.CheckResult.PERMIT;
        });
        QuarkusAccessPathChecker checker = checker(() -> transportIdentity, List.of(pathPolicy));

        QuarkusAccessPathChecker.AccessCheck result =
                checker.check("/secure", "GET", baseIdentity.getPrincipal(), baseIdentity::hasRole);

        assertEquals(QuarkusAccessPathChecker.Decision.ALLOW, result.decision());
        assertTrue(evaluatedUser.get() instanceof io.quarkus.vertx.http.runtime.security.QuarkusHttpUser);
        assertSame(HttpMethod.GET, evaluatedMethod.get());
    }

    @Test
    void check_preservesCustomHttpMethod() {
        RoutingContext transportContext = transportContext();
        SecurityIdentity baseIdentity = TestSecurityIdentity.authenticated("user", "USER");
        SecurityIdentity transportIdentity = transportIdentity(baseIdentity, baseIdentity, transportContext);
        AtomicReference<HttpMethod> evaluatedMethod = new AtomicReference<>();
        HttpSecurityPolicy pathPolicy = (request, identity, requestContext) -> identity.map(value -> {
            evaluatedMethod.set(request.request().method());
            request.put(AbstractPathMatchingHttpSecurityPolicy.class.getName() + ".POLICY_FOUND", true);
            return HttpSecurityPolicy.CheckResult.PERMIT;
        });
        QuarkusAccessPathChecker checker = checker(() -> transportIdentity, List.of(pathPolicy));

        QuarkusAccessPathChecker.AccessCheck result =
                checker.check("/secure", "PURGE", baseIdentity.getPrincipal(), baseIdentity::hasRole);

        assertEquals(QuarkusAccessPathChecker.Decision.ALLOW, result.decision());
        assertEquals("PURGE", evaluatedMethod.get().name());
    }

    @Test
    void checkCurrentRequest_readsAuthoritativePathMarkerWithoutInvokingPolicies() {
        RoutingContext transportContext = transportContext();
        transportContext.put(AbstractPathMatchingHttpSecurityPolicy.class.getName() + ".POLICY_FOUND", true);
        SecurityIdentity baseIdentity = TestSecurityIdentity.authenticated("user", "USER");
        SecurityIdentity transportIdentity = transportIdentity(
                TestSecurityIdentity.authenticated("user", "USER", "TARGET_ROLE"), baseIdentity, transportContext);
        QuarkusAccessPathChecker checker = checker(() -> transportIdentity, List.of());

        when(transportContext.normalizedPath()).thenReturn("/secure");

        QuarkusAccessPathChecker.AccessCheck result =
                checker.checkCurrentRequest("/secure", baseIdentity.getPrincipal(), baseIdentity::hasRole);

        assertEquals(QuarkusAccessPathChecker.Decision.ALLOW, result.decision());
        assertSame(transportIdentity, result.identity());
        assertTrue(result.identity().hasRole("TARGET_ROLE"));
    }

    @Test
    void checkCurrentRequest_otherMenuTargetEvaluatesSyntheticTarget() {
        RoutingContext transportContext = transportContext();
        when(transportContext.normalizedPath()).thenReturn("/app/current");
        SecurityIdentity baseIdentity = TestSecurityIdentity.authenticated("user", "USER");
        SecurityIdentity transportIdentity = transportIdentity(baseIdentity, baseIdentity, transportContext);
        AtomicReference<String> evaluatedPath = new AtomicReference<>();
        HttpSecurityPolicy pathPolicy = (request, identity, requestContext) -> identity.map(value -> {
            evaluatedPath.set(request.normalizedPath());
            request.put(AbstractPathMatchingHttpSecurityPolicy.class.getName() + ".POLICY_FOUND", true);
            return HttpSecurityPolicy.CheckResult.PERMIT;
        });
        QuarkusAccessPathChecker checker = checker(() -> transportIdentity, List.of(pathPolicy), "/app/");

        QuarkusAccessPathChecker.AccessCheck result =
                checker.checkCurrentRequest("/menu-target", baseIdentity.getPrincipal(), baseIdentity::hasRole);

        assertEquals(QuarkusAccessPathChecker.Decision.ALLOW, result.decision());
        assertEquals("/app/menu-target", evaluatedPath.get());
    }

    @Test
    void checkCurrentRequest_otherMenuTargetReadsRuntimeConfigurationOnce() {
        RoutingContext transportContext = transportContext();
        when(transportContext.normalizedPath()).thenReturn("/app/current");
        SecurityIdentity baseIdentity = TestSecurityIdentity.authenticated("user", "USER");
        SecurityIdentity transportIdentity = transportIdentity(baseIdentity, baseIdentity, transportContext);
        HttpSecurityPolicy pathPolicy = (request, identity, requestContext) -> identity.map(value -> {
            request.put(AbstractPathMatchingHttpSecurityPolicy.class.getName() + ".POLICY_FOUND", true);
            return HttpSecurityPolicy.CheckResult.PERMIT;
        });
        PathMatchingHttpSecurityPolicy pathMatchingPolicy = mock(PathMatchingHttpSecurityPolicy.class);
        HttpAuthenticator authenticator = mock(HttpAuthenticator.class);
        when(authenticator.attemptAuthentication(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Uni.createFrom().item(baseIdentity));
        AtomicInteger configurationLookups = new AtomicInteger();
        QuarkusAccessPathChecker checker = new QuarkusAccessPathChecker(
                new QuarkusSecurityIdentityHolder(() -> transportIdentity),
                List.of(pathPolicy),
                pathMatchingPolicy,
                authenticator,
                (context, identityUni, function) -> identityUni.map(value -> function.apply(context, value)),
                () -> {
                    configurationLookups.incrementAndGet();
                    return runtimeConfiguration("/app/");
                });

        QuarkusAccessPathChecker.AccessCheck result =
                checker.checkCurrentRequest("/menu-target", baseIdentity.getPrincipal(), baseIdentity::hasRole);

        assertEquals(QuarkusAccessPathChecker.Decision.ALLOW, result.decision());
        assertEquals(1, configurationLookups.get());
    }

    private static QuarkusAccessPathChecker checker(
            java.util.function.Supplier<SecurityIdentity> identity, List<HttpSecurityPolicy> policies) {
        return checker(identity, policies, "/");
    }

    private static QuarkusAccessPathChecker checker(
            java.util.function.Supplier<SecurityIdentity> identity,
            List<HttpSecurityPolicy> policies,
            String rootPath) {
        PathMatchingHttpSecurityPolicy pathMatchingPolicy = mock(PathMatchingHttpSecurityPolicy.class);
        HttpAuthenticator authenticator = mock(HttpAuthenticator.class);
        when(authenticator.attemptAuthentication(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(ignored -> {
                    SecurityIdentity transportIdentity = identity.get();
                    SecurityIdentity baseIdentity = QuarkusSecurityIdentityAugmentor.baseIdentity(transportIdentity);
                    return baseIdentity == null
                            ? Uni.createFrom().nullItem()
                            : Uni.createFrom().item(baseIdentity);
                });
        return new QuarkusAccessPathChecker(
                new QuarkusSecurityIdentityHolder(identity),
                policies,
                pathMatchingPolicy,
                authenticator,
                (context, identityUni, function) -> identityUni.map(value -> function.apply(context, value)),
                () -> runtimeConfiguration(rootPath));
    }

    private static VaadinSecurityRuntimeConfiguration runtimeConfiguration(String rootPath) {
        return new VaadinSecurityRuntimeConfiguration(
                Map.of(), Map.of(), rootPath, VaadinSecurityRuntimeConfig.AnnotationConfigMismatch.WARN);
    }

    private static RoutingContext transportContext() {
        RoutingContext context = mock(RoutingContext.class);
        HttpServerRequest request = mock(HttpServerRequest.class);
        Map<String, Object> data = new java.util.HashMap<>();
        when(context.data()).thenReturn(data);
        when(context.request()).thenReturn(request);
        when(context.get(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> data.get(invocation.getArgument(0)));
        when(context.put(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    data.put(invocation.getArgument(0), invocation.getArgument(1));
                    return context;
                });
        when(context.remove(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> data.remove(invocation.getArgument(0)));
        return context;
    }

    private static SecurityIdentity transportIdentity(
            SecurityIdentity effectiveIdentity, SecurityIdentity baseIdentity, RoutingContext routingContext) {
        return new SecurityIdentityWithAttributes(
                effectiveIdentity,
                Map.of(
                        QuarkusSecurityIdentityAugmentor.BASE_IDENTITY_ATTRIBUTE,
                        baseIdentity,
                        RoutingContext.class.getName(),
                        routingContext,
                        HttpSecurityUtils.ROUTING_CONTEXT_ATTRIBUTE,
                        routingContext));
    }
}
