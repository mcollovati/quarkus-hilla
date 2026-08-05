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

import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import com.vaadin.flow.router.RouteAliasData;
import com.vaadin.flow.router.RouteBaseData;
import com.vaadin.flow.router.RouteData;
import com.vaadin.flow.server.RouteRegistry;
import com.vaadin.flow.server.auth.AccessAnnotationChecker;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import io.quarkus.vertx.http.runtime.PolicyConfig;
import io.quarkus.vertx.http.runtime.PolicyMappingConfig;
import io.quarkus.vertx.http.runtime.security.HttpSecurityUtils;
import io.quarkus.vertx.http.runtime.security.ImmutablePathMatcher;

/**
 * Performs a side-effect-free, configuration-only comparison between
 * Quarkus HTTP permissions and Vaadin route annotations.
 */
final class AnnotationConfigMismatchAnalyzer {

    private static final String HTTP_METHOD = "GET";
    private final AccessAnnotationChecker annotationChecker = new AccessAnnotationChecker();
    private final PermissionMatcher permissionMatcher;
    private final String rootPath;

    AnnotationConfigMismatchAnalyzer(
            Map<String, PolicyMappingConfig> permissions, Map<String, PolicyConfig> rolePolicies, String rootPath) {
        this.permissionMatcher = new PermissionMatcher(permissions, rolePolicies, rootPath);
        this.rootPath = normalizeRootPath(rootPath);
    }

    Analysis analyze(RouteRegistry registry) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (RouteData route : registry.getRegisteredRoutes()) {
            analyzeRouteSafely(route, registry, diagnostics);
            for (RouteAliasData alias : route.getRouteAliases()) {
                analyzeRouteSafely(alias, registry, diagnostics);
            }
        }
        return new Analysis(diagnostics.stream()
                .distinct()
                .sorted(Comparator.comparing(Diagnostic::path)
                        .thenComparing(diagnostic -> diagnostic.kind().name())
                        .thenComparing(Diagnostic::message))
                .toList());
    }

    private void analyzeRouteSafely(RouteBaseData<?> route, RouteRegistry registry, List<Diagnostic> diagnostics) {
        try {
            analyzeRoute(route, registry, diagnostics);
        } catch (RuntimeException exception) {
            diagnostics.add(Diagnostic.unverified(
                    safeDisplayPath(route.getTemplate()),
                    route.getNavigationTarget(),
                    "analysis failed with " + exception.getClass().getSimpleName()));
        }
    }

    private void analyzeRoute(RouteBaseData<?> route, RouteRegistry registry, List<Diagnostic> diagnostics) {
        AnnotationRequirement annotation = annotationRequirement(route, registry);
        if (!annotation.present()) {
            return;
        }
        if (!route.getRouteParameters().isEmpty()) {
            if (permissionMatcher.hasPermissions()) {
                diagnostics.add(Diagnostic.unverified(
                        displayPath(route.getTemplate()),
                        route.getNavigationTarget(),
                        "parameterized route templates cannot be compared without choosing request values"));
            }
            return;
        }

        String path = requestPath(route.getTemplate());
        PermissionRequirement permission = permissionMatcher.match(path);
        if (!permission.present()) {
            return;
        }
        if (permission.opaqueReason() != null) {
            diagnostics.add(Diagnostic.unverified(path, route.getNavigationTarget(), permission.opaqueReason()));
            return;
        }
        if (!annotation.requirement().equals(permission.requirement())) {
            diagnostics.add(Diagnostic.mismatch(
                    path,
                    route.getNavigationTarget(),
                    annotation.requirement(),
                    permission.requirement(),
                    permission.policyNames()));
        }
    }

    private AnnotationRequirement annotationRequirement(RouteBaseData<?> route, RouteRegistry registry) {
        List<Class<?>> chain = new ArrayList<>(route.getParentLayouts());
        String routePath = route.getTemplate() == null ? "" : route.getTemplate();
        if (route.getParentLayouts().isEmpty()
                && com.vaadin.flow.router.internal.RouteUtil.isAutolayoutEnabled(route.getNavigationTarget(), routePath)
                && registry.hasLayout(routePath)) {
            chain.add(registry.getLayout(routePath));
        }
        chain.add(route.getNavigationTarget());
        boolean anyAnnotation = chain.stream().anyMatch(this::hasSecurityAnnotation);
        if (!anyAnnotation) {
            return AnnotationRequirement.absent();
        }

        Requirement requirement = Requirement.permit();
        for (Class<?> type : chain) {
            AnnotatedElement target = annotationChecker.getSecurityTarget(type);
            requirement = requirement.and(requirement(target));
        }
        return AnnotationRequirement.present(requirement);
    }

    private boolean hasSecurityAnnotation(Class<?> type) {
        return hasSecurityAnnotation(annotationChecker.getSecurityTarget(type));
    }

    private static boolean hasSecurityAnnotation(AnnotatedElement target) {
        return target.isAnnotationPresent(DenyAll.class)
                || target.isAnnotationPresent(AnonymousAllowed.class)
                || target.isAnnotationPresent(RolesAllowed.class)
                || target.isAnnotationPresent(PermitAll.class);
    }

    private static Requirement requirement(AnnotatedElement target) {
        if (target.isAnnotationPresent(DenyAll.class) || !hasSecurityAnnotation(target)) {
            return Requirement.deny();
        }
        if (target.isAnnotationPresent(AnonymousAllowed.class)) {
            return Requirement.permit();
        }
        RolesAllowed rolesAllowed = target.getAnnotation(RolesAllowed.class);
        if (rolesAllowed != null) {
            return Requirement.roles(new LinkedHashSet<>(List.of(rolesAllowed.value())));
        }
        return target.isAnnotationPresent(PermitAll.class) ? Requirement.authenticatedOnly() : Requirement.deny();
    }

    private String requestPath(String routeTemplate) {
        if (routeTemplate == null || routeTemplate.isBlank()) {
            return rootPath;
        }
        return HttpSecurityUtils.normalizePath(rootPath + routeTemplate);
    }

    private String displayPath(String routeTemplate) {
        return requestPath(routeTemplate == null ? "" : routeTemplate);
    }

    private String safeDisplayPath(String routeTemplate) {
        try {
            return displayPath(routeTemplate);
        } catch (RuntimeException exception) {
            String template = routeTemplate == null ? "" : routeTemplate;
            return rootPath + template;
        }
    }

    private static String normalizeRootPath(String rootPath) {
        String normalized = rootPath == null || rootPath.isBlank() ? "/" : rootPath.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        if (!normalized.endsWith("/")) {
            normalized += "/";
        }
        return HttpSecurityUtils.normalizePath(normalized);
    }

    record Analysis(List<Diagnostic> diagnostics) {

        Analysis {
            diagnostics = List.copyOf(diagnostics);
        }

        List<Diagnostic> mismatches() {
            return diagnostics.stream()
                    .filter(diagnostic -> diagnostic.kind() == Kind.MISMATCH)
                    .toList();
        }
    }

    enum Kind {
        MISMATCH,
        UNVERIFIED
    }

    record Diagnostic(Kind kind, String path, String targetClass, String message) {

        private static Diagnostic mismatch(
                String path, Class<?> target, Requirement annotation, Requirement permission, Set<String> policyNames) {
            String message = "Quarkus HTTP configuration requires "
                    + permission
                    + " via "
                    + policyNames
                    + ", while route annotations require "
                    + annotation
                    + ". Effective access remains the conjunction of both.";
            return new Diagnostic(Kind.MISMATCH, path, target.getName(), message);
        }

        private static Diagnostic unverified(String path, Class<?> target, String reason) {
            return new Diagnostic(
                    Kind.UNVERIFIED,
                    path,
                    target.getName(),
                    "Static annotation/configuration comparison is unavailable: " + reason + ".");
        }

        String logMessage() {
            return HTTP_METHOD + " " + path + " (" + targetClass + "): " + message;
        }
    }

    private record AnnotationRequirement(boolean present, Requirement requirement) {

        private static AnnotationRequirement absent() {
            return new AnnotationRequirement(false, null);
        }

        private static AnnotationRequirement present(Requirement requirement) {
            return new AnnotationRequirement(true, requirement);
        }
    }

    private record PermissionRequirement(
            boolean present, Requirement requirement, Set<String> policyNames, String opaqueReason) {

        private static PermissionRequirement absent() {
            return new PermissionRequirement(false, null, Set.of(), null);
        }

        private static PermissionRequirement known(Requirement requirement, Set<String> policyNames) {
            return new PermissionRequirement(true, requirement, Set.copyOf(policyNames), null);
        }

        private static PermissionRequirement opaque(Set<String> policyNames, String reason) {
            return new PermissionRequirement(true, null, Set.copyOf(policyNames), reason);
        }
    }

    private static final class PermissionMatcher {

        private final ImmutablePathMatcher<List<Rule>> permissions;
        private final List<ImmutablePathMatcher<List<Rule>>> sharedPermissions;
        private final boolean hasPermissions;

        private PermissionMatcher(
                Map<String, PolicyMappingConfig> permissionConfigs,
                Map<String, PolicyConfig> rolePolicies,
                String rootPath) {
            ImmutablePathMatcher.ImmutablePathMatcherBuilder<List<Rule>> builder = matcherBuilder(rootPath);
            List<ImmutablePathMatcher<List<Rule>>> shared = new ArrayList<>();
            boolean configured = false;
            for (Map.Entry<String, PolicyMappingConfig> entry : permissionConfigs.entrySet()) {
                PolicyMappingConfig config = entry.getValue();
                if (!config.enabled().orElse(true)
                        || config.paths().isEmpty()
                        || config.paths().get().isEmpty()
                        || config.appliesTo() == PolicyMappingConfig.AppliesTo.JAXRS) {
                    continue;
                }
                configured = true;
                Rule rule = Rule.from(entry.getKey(), config, rolePolicies);
                if (config.shared()) {
                    ImmutablePathMatcher.ImmutablePathMatcherBuilder<List<Rule>> sharedBuilder =
                            matcherBuilder(rootPath);
                    addRule(sharedBuilder, config.paths().get(), rule);
                    shared.add(sharedBuilder.build());
                } else {
                    addRule(builder, config.paths().get(), rule);
                }
            }
            this.permissions = builder.build();
            this.sharedPermissions = List.copyOf(shared);
            this.hasPermissions = configured;
        }

        private boolean hasPermissions() {
            return hasPermissions;
        }

        private PermissionRequirement match(String path) {
            List<Rule> rules = new ArrayList<>();
            for (ImmutablePathMatcher<List<Rule>> shared : sharedPermissions) {
                rules.addAll(methodRules(shared.match(path).getValue()));
            }
            rules.addAll(methodRules(permissions.match(path).getValue()));
            if (rules.isEmpty()) {
                return PermissionRequirement.absent();
            }

            Set<String> policyNames = new TreeSet<>();
            Requirement requirement = Requirement.permit();
            List<String> opaquePolicies = new ArrayList<>();
            for (Rule rule : rules) {
                policyNames.add(rule.displayName());
                if (rule.requirement() == null) {
                    opaquePolicies.add(rule.displayName());
                } else {
                    requirement = requirement.and(rule.requirement());
                }
            }
            if (requirement.denied()) {
                return PermissionRequirement.known(requirement, policyNames);
            }
            if (!opaquePolicies.isEmpty()) {
                return PermissionRequirement.opaque(
                        policyNames,
                        "custom, authentication-mechanism, or role-augmenting policies "
                                + opaquePolicies
                                + " are opaque");
            }
            return PermissionRequirement.known(requirement, policyNames);
        }

        private static List<Rule> methodRules(List<Rule> pathRules) {
            if (pathRules == null || pathRules.isEmpty()) {
                return List.of();
            }
            List<Rule> methodMatches = pathRules.stream()
                    .filter(rule -> !rule.methods().isEmpty() && rule.methods().contains(HTTP_METHOD))
                    .toList();
            if (!methodMatches.isEmpty()) {
                return methodMatches;
            }
            List<Rule> methodless =
                    pathRules.stream().filter(rule -> rule.methods().isEmpty()).toList();
            return methodless.isEmpty() ? List.of(Rule.methodMismatch()) : methodless;
        }

        private static ImmutablePathMatcher.ImmutablePathMatcherBuilder<List<Rule>> matcherBuilder(String rootPath) {
            return ImmutablePathMatcher.<List<Rule>>builder()
                    .handlerAccumulator(List::addAll)
                    .rootPath(normalizeRootPath(rootPath));
        }

        private static void addRule(
                ImmutablePathMatcher.ImmutablePathMatcherBuilder<List<Rule>> builder,
                Collection<String> paths,
                Rule rule) {
            for (String path : paths) {
                builder.addPath(HttpSecurityUtils.normalizePath(path), new ArrayList<>(List.of(rule)));
            }
        }
    }

    private record Rule(String name, Set<String> methods, Requirement requirement, String policyName) {

        private static Rule from(
                String permissionName, PolicyMappingConfig permission, Map<String, PolicyConfig> rolePolicies) {
            String policyName = permission.policy();
            Requirement requirement;
            if (permission
                    .authMechanism()
                    .filter(mechanisms -> !mechanisms.isEmpty())
                    .isPresent()) {
                requirement = null;
            } else if ("permit".equals(policyName)) {
                requirement = Requirement.permit();
            } else if ("deny".equals(policyName)) {
                requirement = Requirement.deny();
            } else if ("authenticated".equals(policyName)) {
                requirement = Requirement.authenticatedOnly();
            } else {
                PolicyConfig rolePolicy = rolePolicies.get(policyName);
                requirement = rolePolicy == null || !rolePolicy.roles().isEmpty()
                        ? null
                        : Requirement.configRoles(rolePolicy.rolesAllowed());
            }
            return new Rule(
                    permissionName, permission.methods().map(Set::copyOf).orElseGet(Set::of), requirement, policyName);
        }

        private static Rule methodMismatch() {
            return new Rule("method-mismatch", Set.of(), Requirement.deny(), "deny");
        }

        private String displayName() {
            return name + "=" + policyName;
        }
    }

    private record Requirement(boolean denied, boolean authenticated, Set<Set<String>> roleClauses) {

        private Requirement {
            if (denied) {
                authenticated = false;
                roleClauses = Set.of();
            } else {
                roleClauses = normalizeClauses(roleClauses);
                if (!roleClauses.isEmpty()) {
                    authenticated = true;
                }
            }
        }

        private static Requirement permit() {
            return new Requirement(false, false, Set.of());
        }

        private static Requirement deny() {
            return new Requirement(true, false, Set.of());
        }

        private static Requirement authenticatedOnly() {
            return new Requirement(false, true, Set.of());
        }

        private static Requirement roles(Set<String> roles) {
            if (roles.isEmpty()) {
                return deny();
            }
            return new Requirement(false, true, Set.of(Set.copyOf(roles)));
        }

        private static Requirement configRoles(List<String> roles) {
            if (roles.contains("**")) {
                return authenticatedOnly();
            }
            return roles(new LinkedHashSet<>(roles));
        }

        private Requirement and(Requirement other) {
            Objects.requireNonNull(other);
            if (denied || other.denied) {
                return deny();
            }
            Set<Set<String>> clauses = new LinkedHashSet<>(roleClauses);
            clauses.addAll(other.roleClauses);
            return new Requirement(false, authenticated || other.authenticated, clauses);
        }

        private static Set<Set<String>> normalizeClauses(Set<Set<String>> clauses) {
            if (clauses == null || clauses.isEmpty()) {
                return Set.of();
            }
            List<Set<String>> sorted = clauses.stream()
                    .map(TreeSet::new)
                    .map(Set::copyOf)
                    .sorted(Comparator.comparing(Requirement::rolesDescription))
                    .toList();
            Set<Set<String>> result = new LinkedHashSet<>();
            for (Set<String> candidate : sorted) {
                if (candidate.isEmpty()) {
                    return Set.of(Set.of());
                }
                if (result.stream().noneMatch(candidate::containsAll)) {
                    result.removeIf(existing -> existing.containsAll(candidate));
                    result.add(candidate);
                }
            }
            return Set.copyOf(result);
        }

        private static String rolesDescription(Set<String> roles) {
            return String.join("|", new TreeSet<>(roles));
        }

        @Override
        public String toString() {
            if (denied || roleClauses.stream().anyMatch(Set::isEmpty)) {
                return "deny";
            }
            if (roleClauses.isEmpty()) {
                return authenticated ? "authenticated" : "permit anonymous";
            }
            return roleClauses.stream()
                    .map(clause -> "role(" + rolesDescription(clause) + ")")
                    .sorted()
                    .reduce((left, right) -> left + " AND " + right)
                    .orElse("authenticated");
        }
    }
}
