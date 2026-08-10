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
import jakarta.inject.Inject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.quarkus.arc.ClientProxy;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.spi.runtime.BlockingSecurityExecutor;
import io.quarkus.vertx.http.runtime.security.AbstractPathMatchingHttpSecurityPolicy;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticator;
import io.quarkus.vertx.http.runtime.security.HttpSecurityPolicy;
import io.quarkus.vertx.http.runtime.security.HttpSecurityUtils;
import io.quarkus.vertx.http.runtime.security.PathMatchingHttpSecurityPolicy;
import io.quarkus.vertx.http.runtime.security.QuarkusHillaSecurityBridge;
import io.quarkus.vertx.http.runtime.security.QuarkusHttpUser;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import org.jboss.logging.Logger;

/**
 * Evaluates synthetic Vaadin navigation requests against the authoritative
 * Quarkus HTTP security policies.
 */
public class QuarkusAccessPathChecker {

    /**
     * Routing-context marker exposed to custom policies while they evaluate a
     * synthetic navigation target.
     */
    public static final String SYNTHETIC_NAVIGATION_ATTRIBUTE =
            QuarkusAccessPathChecker.class.getName() + ".synthetic-navigation";

    private static final Logger LOGGER = Logger.getLogger(QuarkusAccessPathChecker.class);
    private static final String NAVIGATION_METHOD = "GET";

    private final transient QuarkusSecurityIdentityHolder identityHolder;
    private final transient List<HttpSecurityPolicy> globalPolicies;
    private final transient PathMatchingHttpSecurityPolicy pathMatchingPolicy;
    private final transient HttpAuthenticator httpAuthenticator;
    private final transient HttpSecurityPolicy.AuthorizationRequestContext authorizationRequestContext;
    private final transient Supplier<VaadinSecurityRuntimeConfiguration> runtimeConfiguration;

    @Inject
    public QuarkusAccessPathChecker(
            Instance<SecurityIdentity> securityIdentity,
            Instance<HttpSecurityPolicy> installedPolicies,
            PathMatchingHttpSecurityPolicy pathMatchingPolicy,
            HttpAuthenticator httpAuthenticator,
            BlockingSecurityExecutor blockingSecurityExecutor,
            Instance<VaadinSecurityRuntimeConfiguration> runtimeConfiguration) {
        this(
                new QuarkusSecurityIdentityHolder(securityIdentity::get),
                globalPolicies(installedPolicies, pathMatchingPolicy),
                pathMatchingPolicy,
                httpAuthenticator,
                QuarkusHillaSecurityBridge.authorizationRequestContext(blockingSecurityExecutor),
                runtimeConfiguration::get);
    }

    QuarkusAccessPathChecker(
            QuarkusSecurityIdentityHolder identityHolder,
            List<HttpSecurityPolicy> globalPolicies,
            PathMatchingHttpSecurityPolicy pathMatchingPolicy,
            HttpAuthenticator httpAuthenticator,
            HttpSecurityPolicy.AuthorizationRequestContext authorizationRequestContext,
            Supplier<VaadinSecurityRuntimeConfiguration> runtimeConfiguration) {
        this.identityHolder = identityHolder;
        this.globalPolicies = List.copyOf(globalPolicies);
        this.pathMatchingPolicy = pathMatchingPolicy;
        this.httpAuthenticator = httpAuthenticator;
        this.authorizationRequestContext = authorizationRequestContext;
        this.runtimeConfiguration = runtimeConfiguration;
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debugf(
                    "Synthetic Vaadin navigation will evaluate Quarkus HTTP policies %s",
                    this.globalPolicies.stream()
                            .map(QuarkusAccessPathChecker::policyDescription)
                            .toList());
        }
    }

    public AccessCheck check(String path, Principal principal, Predicate<String> roleChecker) {
        return check(path, NAVIGATION_METHOD, principal, roleChecker);
    }

    public AccessCheck check(String path, String method, Principal principal, Predicate<String> roleChecker) {
        TargetRequest targetRequest = targetRequest(runtimeConfiguration.get().resolveApplicationPath(path), method);
        return check(targetRequest, principal);
    }

    private AccessCheck check(TargetRequest targetRequest, Principal principal) {
        if (!targetRequest.valid()) {
            return AccessCheck.deny("invalid path: " + targetRequest.diagnostic(), null);
        }
        if (Context.isOnEventLoopThread()) {
            LOGGER.warnf(
                    "Synthetic HTTP permission evaluation for %s %s was requested on an event-loop thread; "
                            + "denying before invoking application policies",
                    targetRequest.method(), targetRequest.path());
            return AccessCheck.deny("synthetic navigation policy evaluation on event-loop", null);
        }

        SecurityIdentity transportIdentity = identityHolder.currentIdentity();
        SecurityIdentity baseIdentity = identityHolder.currentNavigationIdentity();
        AccessCheck identityFailure = validateIdentity(principal, transportIdentity, baseIdentity);
        if (identityFailure != null) {
            return identityFailure;
        }

        RoutingContext transportContext = HttpSecurityUtils.getRoutingContextAttribute(transportIdentity);
        if (transportContext == null) {
            return AccessCheck.deny("request RoutingContext unavailable", baseIdentity);
        }

        RoutingContext targetContext = routingContext(targetRequest, transportContext, baseIdentity);
        QuarkusHillaSecurityBridge.prepareTargetAuthentication(targetContext, pathMatchingPolicy);

        try {
            SecurityIdentity targetIdentity = httpAuthenticator
                    .attemptAuthentication(targetContext)
                    .await()
                    .indefinitely();
            if (targetIdentity == null) {
                if (!baseIdentity.isAnonymous()) {
                    Set<String> requiredMechanisms = QuarkusHillaSecurityBridge.requiredAuthenticationMechanisms(
                            pathMatchingPolicy, targetContext);
                    String diagnostic = requiredMechanisms.isEmpty()
                            ? "target authentication could not reproduce the transport identity"
                            : "target requires authentication mechanism " + requiredMechanisms;
                    return AccessCheck.deny(diagnostic, baseIdentity);
                }
                targetIdentity = baseIdentity;
            } else if (!principalMatches(baseIdentity.getPrincipal(), targetIdentity)) {
                return AccessCheck.deny("target authentication principal mismatch", baseIdentity);
            }
            targetIdentity = withRoutingContext(targetIdentity, targetContext);
            AccessCheck result =
                    evaluatePolicies(targetContext, targetIdentity, 0).await().indefinitely();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debugf(
                        "Synthetic navigation %s %s resolved to %s by %s",
                        targetRequest.method(), targetRequest.path(), result.decision(), result.policyName());
            }
            return result;
        } catch (RuntimeException exception) {
            LOGGER.warnf(
                    exception,
                    "Quarkus HTTP security policies cannot be evaluated for synthetic navigation %s %s; denying",
                    targetRequest.method(),
                    targetRequest.path());
            return AccessCheck.deny("target policy evaluation failed", baseIdentity);
        }
    }

    AccessCheck checkCurrentRequest(String path, Principal principal, Predicate<String> roleChecker) {
        SecurityIdentity currentIdentity = identityHolder.currentIdentity();
        if (currentIdentity == null) {
            return AccessCheck.deny("request SecurityIdentity unavailable", null);
        }
        SecurityIdentity navigationIdentity = identityHolder.currentNavigationIdentity();
        if (navigationIdentity == null) {
            return AccessCheck.deny("pre-path SecurityIdentity unavailable", null);
        }
        if (!principalMatches(principal, navigationIdentity)) {
            return AccessCheck.deny("request SecurityIdentity principal mismatch", navigationIdentity);
        }
        RoutingContext routingContext = HttpSecurityUtils.getRoutingContextAttribute(currentIdentity);
        if (routingContext == null) {
            return AccessCheck.deny("request RoutingContext unavailable", navigationIdentity);
        }
        TargetRequest targetRequest =
                targetRequest(runtimeConfiguration.get().resolveApplicationPath(path), NAVIGATION_METHOD);
        if (!targetRequest.valid()) {
            return AccessCheck.deny("invalid path: " + targetRequest.diagnostic(), navigationIdentity);
        }
        CanonicalPath currentPath = canonicalize(routingContext.normalizedPath());
        if (!currentPath.valid()) {
            return AccessCheck.deny("current request path normalization failed", navigationIdentity);
        }
        if (!currentPath.path().equals(targetRequest.path())) {
            return check(targetRequest, principal);
        }
        if (!principalMatches(principal, currentIdentity)) {
            return AccessCheck.deny("current target SecurityIdentity principal mismatch", navigationIdentity);
        }
        return QuarkusHillaSecurityBridge.pathPolicyApplied(routingContext)
                ? AccessCheck.allow("Quarkus HTTP permission policy", currentIdentity)
                : AccessCheck.noMatch(currentIdentity);
    }

    private Uni<AccessCheck> evaluatePolicies(
            RoutingContext targetContext, SecurityIdentity identity, int policyIndex) {
        if (policyIndex == globalPolicies.size()) {
            return Uni.createFrom()
                    .item(
                            QuarkusHillaSecurityBridge.pathPolicyApplied(targetContext)
                                    ? AccessCheck.allow("Quarkus HTTP permission policy", identity)
                                    : AccessCheck.noMatch(identity));
        }

        HttpSecurityPolicy policy = globalPolicies.get(policyIndex);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debugf("Evaluating synthetic navigation with HTTP security policy %s", policyDescription(policy));
        }
        Uni<HttpSecurityPolicy.CheckResult> permission;
        try {
            permission =
                    policy.checkPermission(targetContext, Uni.createFrom().item(identity), authorizationRequestContext);
        } catch (RuntimeException exception) {
            return policyFailure(policy, identity, exception);
        }
        if (permission == null) {
            return Uni.createFrom()
                    .item(AccessCheck.deny(
                            "HTTP security policy " + policyDescription(policy) + " returned no result", identity));
        }
        return permission
                .onItem()
                .transformToUni(result -> {
                    if (result == null || !result.isPermitted()) {
                        SecurityIdentity deniedIdentity = result == null || result.getAugmentedIdentity() == null
                                ? identity
                                : result.getAugmentedIdentity();
                        return Uni.createFrom().item(AccessCheck.deny(policyDescription(policy), deniedIdentity));
                    }
                    SecurityIdentity nextIdentity =
                            result.getAugmentedIdentity() == null ? identity : result.getAugmentedIdentity();
                    return evaluatePolicies(targetContext, nextIdentity, policyIndex + 1);
                })
                .onFailure()
                .recoverWithUni(exception -> policyFailure(policy, identity, exception));
    }

    private static Uni<AccessCheck> policyFailure(
            HttpSecurityPolicy policy, SecurityIdentity identity, Throwable exception) {
        LOGGER.warnf(
                exception,
                "HTTP security policy %s failed during synthetic Vaadin navigation; denying",
                policyDescription(policy));
        return Uni.createFrom()
                .item(AccessCheck.deny("HTTP security policy " + policyDescription(policy) + " failed", identity));
    }

    private static AccessCheck validateIdentity(
            Principal principal, SecurityIdentity transportIdentity, SecurityIdentity baseIdentity) {
        if (transportIdentity == null) {
            return AccessCheck.deny("request SecurityIdentity unavailable", null);
        }
        if (baseIdentity == null) {
            return AccessCheck.deny("pre-path SecurityIdentity unavailable", null);
        }
        if (!principalMatches(principal, baseIdentity)) {
            return AccessCheck.deny("request SecurityIdentity principal mismatch", baseIdentity);
        }
        return null;
    }

    private static boolean principalMatches(Principal expected, SecurityIdentity identity) {
        if (identity == null) {
            return false;
        }
        if (expected == null) {
            return identity.isAnonymous();
        }
        return !identity.isAnonymous()
                && identity.getPrincipal() != null
                && Objects.equals(expected.getName(), identity.getPrincipal().getName());
    }

    private static List<HttpSecurityPolicy> globalPolicies(
            Instance<HttpSecurityPolicy> installedPolicies, PathMatchingHttpSecurityPolicy pathMatchingPolicy) {
        List<HttpSecurityPolicy> policies = new ArrayList<>();
        boolean pathPolicyFound = pathMatchingPolicy.hasNoPermissions();
        Object unwrappedPathPolicy = ClientProxy.unwrap(pathMatchingPolicy);
        for (HttpSecurityPolicy policy : installedPolicies) {
            Object unwrappedPolicy = ClientProxy.unwrap(policy);
            if (unwrappedPolicy == unwrappedPathPolicy) {
                pathPolicyFound = true;
            }
            if (policy.name() != null
                    || unwrappedPolicy instanceof HillaSecurityPolicy
                    || (unwrappedPolicy instanceof AbstractPathMatchingHttpSecurityPolicy pathPolicy
                            && pathPolicy.hasNoPermissions())) {
                continue;
            }
            policies.add(policy);
        }
        if (!pathPolicyFound) {
            throw new IllegalStateException(
                    "Quarkus PathMatchingHttpSecurityPolicy is missing from the installed global policies");
        }
        return List.copyOf(policies);
    }

    private static SecurityIdentity withRoutingContext(SecurityIdentity identity, RoutingContext routingContext) {
        SecurityIdentity targetIdentity = new SecurityIdentityWithAttributes(
                identity,
                Map.of(
                        RoutingContext.class.getName(),
                        routingContext,
                        HttpSecurityUtils.ROUTING_CONTEXT_ATTRIBUTE,
                        routingContext));
        routingContext.setUser(new QuarkusHttpUser(targetIdentity));
        return targetIdentity;
    }

    private static RoutingContext routingContext(
            TargetRequest targetRequest, RoutingContext transportContext, SecurityIdentity baseIdentity) {
        Map<String, Object> data = QuarkusHillaSecurityBridge.targetData(transportContext);
        data.put(SYNTHETIC_NAVIGATION_ATTRIBUTE, Boolean.TRUE);
        HttpServerRequest request = httpServerRequest(targetRequest, transportContext.request());
        Object[] proxyReference = new Object[1];
        User[] user = new User[] {new QuarkusHttpUser(baseIdentity)};
        proxyReference[0] = Proxy.newProxyInstance(
                QuarkusAccessPathChecker.class.getClassLoader(),
                new Class<?>[] {RoutingContext.class},
                (proxy, invokedMethod, arguments) -> switch (invokedMethod.getName()) {
                    case "request" -> request;
                    case "normalizedPath", "normalisedPath" -> targetRequest.path();
                    case "queryParams" -> targetRequest.queryParameters();
                    case "pathParams" -> Map.of();
                    case "pathParam" -> null;
                    case "body", "getBody", "getBodyAsString", "getBodyAsJson", "getBodyAsJsonArray" -> null;
                    case "fileUploads" -> Set.of();
                    case "user" -> user[0];
                    case "setUser" -> {
                        user[0] = (User) arguments[0];
                        yield proxyReference[0];
                    }
                    case "put" -> {
                        data.put((String) arguments[0], arguments[1]);
                        yield proxyReference[0];
                    }
                    case "get" ->
                        arguments.length == 1 ? data.get(arguments[0]) : data.getOrDefault(arguments[0], arguments[1]);
                    case "remove" -> data.remove(arguments[0]);
                    case "data" -> data;
                    case "equals" -> proxy == arguments[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" ->
                        "Vaadin navigation routing context for " + targetRequest.method() + " " + targetRequest.uri();
                    default -> invoke(transportContext, invokedMethod, arguments);
                });
        return (RoutingContext) proxyReference[0];
    }

    private static HttpServerRequest httpServerRequest(
            TargetRequest targetRequest, HttpServerRequest transportRequest) {
        HttpMethod httpMethod = HttpMethod.valueOf(targetRequest.method());
        return (HttpServerRequest) Proxy.newProxyInstance(
                QuarkusAccessPathChecker.class.getClassLoader(),
                new Class<?>[] {HttpServerRequest.class},
                (proxy, invokedMethod, arguments) -> switch (invokedMethod.getName()) {
                    case "method" -> httpMethod;
                    case "path" -> targetRequest.path();
                    case "uri" -> targetRequest.uri();
                    case "absoluteURI" ->
                        transportRequest.scheme() + "://" + transportRequest.host() + targetRequest.uri();
                    case "query" -> targetRequest.query();
                    case "params" -> targetRequest.queryParameters();
                    case "getParam" -> {
                        String value = targetRequest.queryParameters().get((String) arguments[0]);
                        yield value == null && arguments.length > 1 ? arguments[1] : value;
                    }
                    case "equals" -> proxy == arguments[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" ->
                        "Vaadin navigation HTTP request for " + targetRequest.method() + " " + targetRequest.uri();
                    default -> invoke(transportRequest, invokedMethod, arguments);
                });
    }

    private static Object invoke(Object delegate, Method method, Object[] arguments) throws Throwable {
        try {
            return method.invoke(delegate, arguments);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    private static TargetRequest targetRequest(String uri, String method) {
        if (uri == null) {
            return TargetRequest.invalid("path is null");
        }
        int fragmentStart = uri.indexOf('#');
        if (fragmentStart >= 0) {
            uri = uri.substring(0, fragmentStart);
        }
        int queryStart = uri.indexOf('?');
        String rawPath = queryStart < 0 ? uri : uri.substring(0, queryStart);
        String query = queryStart < 0 ? null : uri.substring(queryStart + 1);
        CanonicalPath canonicalPath = canonicalize(rawPath);
        if (!canonicalPath.valid()) {
            return TargetRequest.invalid(canonicalPath.diagnostic());
        }
        MultiMap queryParameters;
        try {
            queryParameters = queryParameters(query);
        } catch (IllegalArgumentException exception) {
            return TargetRequest.invalid("invalid query string");
        }
        String requestMethod = method == null || method.isBlank() ? NAVIGATION_METHOD : method.toUpperCase(Locale.ROOT);
        String targetUri = query == null || query.isEmpty() ? canonicalPath.path() : canonicalPath.path() + "?" + query;
        return TargetRequest.valid(canonicalPath.path(), targetUri, query, queryParameters, requestMethod);
    }

    private static MultiMap queryParameters(String query) {
        MultiMap parameters = MultiMap.caseInsensitiveMultiMap();
        if (query == null || query.isEmpty()) {
            return parameters;
        }
        for (String part : query.split("&")) {
            int separator = part.indexOf('=');
            String name = separator < 0 ? part : part.substring(0, separator);
            String value = separator < 0 ? "" : part.substring(separator + 1);
            parameters.add(
                    URLDecoder.decode(name, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return parameters;
    }

    private static CanonicalPath canonicalize(String path) {
        try {
            return CanonicalPath.valid(HttpSecurityUtils.normalizePath(PathUtil.ensureSlashBegin(path)));
        } catch (RuntimeException exception) {
            return CanonicalPath.invalid("path normalization failed");
        }
    }

    private static String policyDescription(HttpSecurityPolicy policy) {
        String name = policy.name();
        if (name != null) {
            return name;
        }
        Object unwrapped = ClientProxy.unwrap(policy);
        return unwrapped.getClass().getName();
    }

    private record TargetRequest(
            boolean valid,
            String path,
            String uri,
            String query,
            MultiMap queryParameters,
            String method,
            String diagnostic) {

        static TargetRequest valid(String path, String uri, String query, MultiMap queryParameters, String method) {
            return new TargetRequest(true, path, uri, query, queryParameters, method, null);
        }

        static TargetRequest invalid(String diagnostic) {
            return new TargetRequest(false, null, null, null, null, null, diagnostic);
        }
    }

    private record CanonicalPath(boolean valid, String path, String diagnostic) {

        static CanonicalPath valid(String path) {
            return new CanonicalPath(true, path, null);
        }

        static CanonicalPath invalid(String diagnostic) {
            return new CanonicalPath(false, null, diagnostic);
        }
    }

    public enum Decision {
        NO_MATCH,
        ALLOW,
        DENY
    }

    public record AccessCheck(Decision decision, String policyName, SecurityIdentity identity) {

        static AccessCheck noMatch(SecurityIdentity identity) {
            return new AccessCheck(Decision.NO_MATCH, null, identity);
        }

        static AccessCheck allow(String policyName, SecurityIdentity identity) {
            return new AccessCheck(Decision.ALLOW, policyName, identity);
        }

        static AccessCheck deny(String policyName, SecurityIdentity identity) {
            return new AccessCheck(Decision.DENY, policyName, identity);
        }
    }
}
