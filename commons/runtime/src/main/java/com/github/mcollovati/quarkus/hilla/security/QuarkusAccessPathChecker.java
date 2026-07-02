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

import jakarta.inject.Inject;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.security.Permission;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import io.quarkus.security.credential.Credential;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.security.AbstractPathMatchingHttpSecurityPolicy;
import io.quarkus.vertx.http.runtime.security.DenySecurityPolicy;
import io.quarkus.vertx.http.runtime.security.HttpSecurityConfiguration;
import io.quarkus.vertx.http.runtime.security.HttpSecurityPolicy;
import io.quarkus.vertx.http.runtime.security.ImmutablePathMatcher;
import io.quarkus.vertx.http.runtime.security.PathMatchingHttpSecurityPolicy;
import io.quarkus.vertx.http.runtime.security.RolesMapping;
import io.smallrye.mutiny.Uni;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;

/**
 * Low-level Quarkus HTTP permission path checker for Vaadin route navigation.
 * <p>
 * This checker reuses Quarkus's effective path matching security policy, which
 * already includes runtime configuration, {@code HttpSecurity} observer
 * permissions, named {@link HttpSecurityPolicy} beans, shared permissions, and
 * the method matching rules used for direct HTTP requests. A missing path match
 * is represented as {@link Decision#NO_MATCH}, so callers can combine the
 * result with other access checks without turning absence of a Quarkus rule into
 * an allow or deny.
 */
public class QuarkusAccessPathChecker {

    private static final String NAVIGATION_METHOD = "GET";
    private static final Field HAS_NO_PERMISSIONS =
            field(AbstractPathMatchingHttpSecurityPolicy.class, "hasNoPermissions");
    private static final Field PATH_MATCHER = field(AbstractPathMatchingHttpSecurityPolicy.class, "pathMatcher");
    private static final Field SHARED_PERMISSION_PATH_MATCHERS =
            field(AbstractPathMatchingHttpSecurityPolicy.class, "sharedPermissionsPathMatchers");
    private static final Field HTTP_MATCHER_METHODS = field(httpMatcherClass(), "methods");
    private static final Field HTTP_MATCHER_CHECKER = field(httpMatcherClass(), "checker");
    private static final Field ROLES_ALLOWED =
            field("io.quarkus.vertx.http.runtime.security.RolesAllowedHttpSecurityPolicy", "rolesAllowed");
    private static final Field ROLE_TO_ROLES =
            field("io.quarkus.vertx.http.runtime.security.RolesMapping", "roleToRoles");
    private static final Field HTTP_SECURITY_CONFIGURATION_INSTANCE =
            field(HttpSecurityConfiguration.class, "instance");
    private static final Field HTTP_SECURITY_CONFIGURATION_ROLES_MAPPING =
            field(HttpSecurityConfiguration.class, "rolesMapping");
    private static final HttpSecurityPolicy.AuthorizationRequestContext DIRECT_REQUEST_CONTEXT =
            (context, identity, function) -> identity.map(identityValue -> function.apply(context, identityValue));

    private final transient AbstractPathMatchingHttpSecurityPolicy pathMatchingPolicy;

    @Inject
    public QuarkusAccessPathChecker(PathMatchingHttpSecurityPolicy pathMatchingPolicy) {
        this.pathMatchingPolicy = pathMatchingPolicy;
    }

    public AccessCheck check(String path, Principal principal, Predicate<String> roleChecker) {
        return check(path, NAVIGATION_METHOD, principal, roleChecker);
    }

    public AccessCheck check(String path, String method, Principal principal, Predicate<String> roleChecker) {
        if (hasNoPermissions()) {
            return AccessCheck.noMatch();
        }

        String normalizedPath = removeMatrixParameters(PathUtil.ensureSlashBegin(path));
        String requestMethod = method == null ? NAVIGATION_METHOD : method;
        List<HttpSecurityPolicy> matchingPolicies = matchingPolicies(normalizedPath, requestMethod);

        if (matchingPolicies.isEmpty()) {
            return AccessCheck.noMatch();
        }

        RoutingContext routingContext = routingContext(normalizedPath, requestMethod);
        RolesMapping rolesMapping = rolesMapping();
        SecurityIdentity securityIdentity =
                securityIdentity(principal, roleChecker, candidateRoles(matchingPolicies, rolesMapping));
        if (rolesMapping != null) {
            securityIdentity = rolesMapping.apply(securityIdentity);
        }
        for (HttpSecurityPolicy policy : matchingPolicies) {
            HttpSecurityPolicy.CheckResult checkResult = checkPolicy(policy, routingContext, securityIdentity);
            if (!checkResult.isPermitted()) {
                return new AccessCheck(Decision.DENY, policyDescription(policy));
            }
            if (checkResult.getAugmentedIdentity() != null) {
                securityIdentity = checkResult.getAugmentedIdentity();
            }
        }
        return new AccessCheck(Decision.ALLOW, policyDescription(matchingPolicies.get(0)));
    }

    private List<HttpSecurityPolicy> matchingPolicies(String path, String method) {
        List<HttpSecurityPolicy> result = new ArrayList<>();
        List<ImmutablePathMatcher<List<Object>>> sharedMatchers = sharedPathMatchers();
        if (sharedMatchers != null) {
            for (ImmutablePathMatcher<List<Object>> sharedMatcher : sharedMatchers) {
                result.addAll(findPolicies(sharedMatcher, path, method));
            }
        }
        result.addAll(findPolicies(pathMatcher(), path, method));
        return result;
    }

    private List<HttpSecurityPolicy> findPolicies(
            ImmutablePathMatcher<List<Object>> pathMatcher, String path, String method) {
        List<Object> matchers = pathMatcher.match(path).getValue();
        if (matchers == null || matchers.isEmpty()) {
            return List.of();
        }

        List<HttpSecurityPolicy> methodMatches = new ArrayList<>();
        List<HttpSecurityPolicy> noMethod = new ArrayList<>();
        for (Object matcher : matchers) {
            Set<String> methods = methods(matcher);
            if (methods == null || methods.isEmpty()) {
                noMethod.add(checker(matcher));
            } else if (methods.contains(method)) {
                methodMatches.add(checker(matcher));
            }
        }
        if (!methodMatches.isEmpty()) {
            return methodMatches;
        }
        if (!noMethod.isEmpty()) {
            return noMethod;
        }
        return List.of(DenySecurityPolicy.INSTANCE);
    }

    private HttpSecurityPolicy.CheckResult checkPolicy(
            HttpSecurityPolicy policy, RoutingContext routingContext, SecurityIdentity securityIdentity) {
        try {
            return policy.checkPermission(
                            routingContext, Uni.createFrom().item(securityIdentity), DIRECT_REQUEST_CONTEXT)
                    .await()
                    .indefinitely();
        } catch (RuntimeException exception) {
            UnsupportedOperationException unsupported = findCause(exception, UnsupportedOperationException.class);
            if (unsupported != null) {
                throw new IllegalStateException(
                        "HTTP security policy %s cannot be evaluated during Vaadin navigation because it requires "
                                + "request or identity state that is unavailable outside a real HTTP request: %s"
                                        .formatted(policyDescription(policy), unsupported.getMessage()),
                        exception);
            }
            throw exception;
        }
    }

    private boolean hasNoPermissions() {
        return get(HAS_NO_PERMISSIONS, pathMatchingPolicy);
    }

    @SuppressWarnings("unchecked")
    private ImmutablePathMatcher<List<Object>> pathMatcher() {
        return (ImmutablePathMatcher<List<Object>>) get(PATH_MATCHER, pathMatchingPolicy);
    }

    @SuppressWarnings("unchecked")
    private List<ImmutablePathMatcher<List<Object>>> sharedPathMatchers() {
        return (List<ImmutablePathMatcher<List<Object>>>) get(SHARED_PERMISSION_PATH_MATCHERS, pathMatchingPolicy);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> methods(Object matcher) {
        return (Set<String>) get(HTTP_MATCHER_METHODS, matcher);
    }

    private static HttpSecurityPolicy checker(Object matcher) {
        return get(HTTP_MATCHER_CHECKER, matcher);
    }

    private static RolesMapping rolesMapping() {
        Object configuration = get(HTTP_SECURITY_CONFIGURATION_INSTANCE, null);
        if (configuration == null) {
            return null;
        }
        return get(HTTP_SECURITY_CONFIGURATION_ROLES_MAPPING, configuration);
    }

    private static Set<String> candidateRoles(List<HttpSecurityPolicy> policies, RolesMapping rolesMapping) {
        Set<String> candidates = new HashSet<>();
        for (HttpSecurityPolicy policy : policies) {
            String[] rolesAllowed = getIfAssignable(ROLES_ALLOWED, policy, String[].class);
            if (rolesAllowed != null) {
                Collections.addAll(candidates, rolesAllowed);
            }

            Map<String, List<String>> roleToRoles = getIfAssignable(ROLE_TO_ROLES, policy, Map.class);
            if (roleToRoles != null) {
                candidates.addAll(roleToRoles.keySet());
                roleToRoles.values().forEach(candidates::addAll);
            }
        }
        addRoleMappingCandidates(candidates, rolesMapping);
        return Set.copyOf(candidates);
    }

    private static void addRoleMappingCandidates(Set<String> candidates, RolesMapping rolesMapping) {
        Map<String, List<String>> roleToRoles = getIfAssignable(ROLE_TO_ROLES, rolesMapping, Map.class);
        if (roleToRoles != null) {
            candidates.addAll(roleToRoles.keySet());
            roleToRoles.values().forEach(candidates::addAll);
        }
    }

    private static SecurityIdentity securityIdentity(
            Principal principal, Predicate<String> roleChecker, Set<String> candidateRoles) {
        return new SecurityIdentity() {
            @Override
            public Principal getPrincipal() {
                return principal;
            }

            @Override
            public boolean isAnonymous() {
                return principal == null;
            }

            @Override
            public Set<String> getRoles() {
                if (candidateRoles.isEmpty()) {
                    return Set.of();
                }
                return candidateRoles.stream()
                        .filter(roleChecker)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
            }

            @Override
            public boolean hasRole(String role) {
                return roleChecker.test(role);
            }

            @Override
            public <T extends Credential> T getCredential(Class<T> credentialType) {
                return null;
            }

            @Override
            public Set<Credential> getCredentials() {
                return Set.of();
            }

            @Override
            public <T> T getAttribute(String name) {
                return null;
            }

            @Override
            public Map<String, Object> getAttributes() {
                return Map.of();
            }

            @Override
            public Set<Permission> getPermissions() {
                return Set.of();
            }

            @Override
            public Uni<Boolean> checkPermission(Permission permission) {
                return Uni.createFrom()
                        .failure(new UnsupportedOperationException(
                                "SecurityIdentity permission checks are not exposed by Vaadin NavigationContext"));
            }
        };
    }

    private static RoutingContext routingContext(String path, String method) {
        Map<String, Object> data = new HashMap<>();
        HttpServerRequest request = httpServerRequest(path, method);
        Object[] proxy = new Object[1];
        proxy[0] = Proxy.newProxyInstance(
                QuarkusAccessPathChecker.class.getClassLoader(),
                new Class<?>[] {RoutingContext.class},
                (ignored, invokedMethod, args) -> switch (invokedMethod.getName()) {
                    case "request" -> request;
                    case "normalizedPath", "normalisedPath" -> path;
                    case "put" -> {
                        data.put((String) args[0], args[1]);
                        yield proxy[0];
                    }
                    case "get" -> args.length == 1 ? data.get(args[0]) : data.getOrDefault(args[0], args[1]);
                    case "remove" -> data.remove(args[0]);
                    case "data" -> data;
                    case "toString" -> "Vaadin navigation routing context for " + method + " " + path;
                    default -> throw unsupported(invokedMethod, "RoutingContext");
                });
        return (RoutingContext) proxy[0];
    }

    private static HttpServerRequest httpServerRequest(String path, String method) {
        HttpMethod httpMethod = new HttpMethod(method);
        return (HttpServerRequest) Proxy.newProxyInstance(
                QuarkusAccessPathChecker.class.getClassLoader(),
                new Class<?>[] {HttpServerRequest.class},
                (ignored, invokedMethod, args) -> switch (invokedMethod.getName()) {
                    case "method" -> httpMethod;
                    case "path", "uri", "absoluteURI" -> path;
                    case "scheme" -> "http";
                    case "headers", "params" -> MultiMap.caseInsensitiveMultiMap();
                    case "host" -> "";
                    case "isSSL" -> false;
                    case "toString" -> "Vaadin navigation HTTP request for " + method + " " + path;
                    default -> throw unsupported(invokedMethod, "HttpServerRequest");
                });
    }

    private static UnsupportedOperationException unsupported(java.lang.reflect.Method method, String type) {
        return new UnsupportedOperationException(type + "." + method.getName());
    }

    private static String removeMatrixParameters(String path) {
        StringBuilder result = new StringBuilder(path.length());
        boolean matrixParameter = false;
        for (int i = 0; i < path.length(); i++) {
            char character = path.charAt(i);
            if (character == ';') {
                matrixParameter = true;
            } else if (character == '/') {
                matrixParameter = false;
                result.append(character);
            } else if (!matrixParameter) {
                result.append(character);
            }
        }
        return result.toString();
    }

    private static String policyDescription(HttpSecurityPolicy policy) {
        if (policy.name() != null) {
            return policy.name();
        }
        return policy.getClass().getName();
    }

    private static <T extends Throwable> T findCause(Throwable throwable, Class<T> causeType) {
        Throwable current = throwable;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return causeType.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private static Class<?> httpMatcherClass() {
        try {
            return Class.forName(
                    "io.quarkus.vertx.http.runtime.security.AbstractPathMatchingHttpSecurityPolicy$HttpMatcher");
        } catch (ClassNotFoundException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static Field field(String className, String fieldName) {
        try {
            return field(Class.forName(className), fieldName);
        } catch (ClassNotFoundException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static Field field(Class<?> type, String fieldName) {
        try {
            Field field = type.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T get(Field field, Object instance) {
        try {
            return (T) field.get(instance);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot read Quarkus HTTP security state", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T getIfAssignable(Field field, Object instance, Class<?> expectedType) {
        if (instance == null) {
            return null;
        }
        try {
            Object value = field.get(instance);
            if (value == null || !expectedType.isInstance(value)) {
                return null;
            }
            return (T) value;
        } catch (IllegalArgumentException exception) {
            return null;
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot read Quarkus HTTP security policy state", exception);
        }
    }

    public enum Decision {
        NO_MATCH,
        ALLOW,
        DENY
    }

    public record AccessCheck(Decision decision, String policyName) {

        static AccessCheck noMatch() {
            return new AccessCheck(Decision.NO_MATCH, null);
        }
    }
}
