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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAliasData;
import com.vaadin.flow.router.RouteData;
import com.vaadin.flow.router.RouterLayout;
import com.vaadin.flow.server.RouteRegistry;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import io.quarkus.vertx.http.runtime.PolicyConfig;
import io.quarkus.vertx.http.runtime.PolicyMappingConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnnotationConfigMismatchAnalyzerTest {

    @Test
    void analyze_equivalentBuiltInAndRolePolicies_reportsNoMismatch() {
        Map<String, PolicyMappingConfig> permissions = new LinkedHashMap<>();
        permissions.put("public", permission("permit", "/public"));
        permissions.put("authenticated", permission("authenticated", "/authenticated"));
        permissions.put("user", permission("user-policy", "/user"));
        permissions.put("denied", permission("deny", "/denied"));

        AnnotationConfigMismatchAnalyzer.Analysis analysis = analyze(
                List.of(
                        route("public", PublicView.class),
                        route("authenticated", AuthenticatedView.class),
                        route("user", UserView.class),
                        route("denied", DeniedView.class)),
                permissions,
                Map.of("user-policy", rolePolicy(List.of("USER"))),
                "/");

        assertTrue(analysis.diagnostics().isEmpty());
    }

    @Test
    void analyze_provenDifference_reportsConjunctiveMismatch() {
        AnnotationConfigMismatchAnalyzer.Analysis analysis = analyze(
                List.of(route("user", UserView.class)), Map.of("public", permission("permit", "/user")), Map.of(), "/");

        assertEquals(1, analysis.mismatches().size());
        AnnotationConfigMismatchAnalyzer.Diagnostic diagnostic =
                analysis.mismatches().getFirst();
        assertEquals("/user", diagnostic.path());
        assertTrue(diagnostic.message().contains("permit anonymous"));
        assertTrue(diagnostic.message().contains("role(USER)"));
        assertTrue(diagnostic.message().contains("conjunction"));
    }

    @Test
    void analyze_duplicateRolesAllowedValues_areDeduplicated() {
        AnnotationConfigMismatchAnalyzer.Analysis analysis = analyze(
                List.of(route("user", DuplicateUserView.class)),
                Map.of("user", permission("user-policy", "/user")),
                Map.of("user-policy", rolePolicy(List.of("USER"))),
                "/");

        assertTrue(analysis.diagnostics().isEmpty());
    }

    @Test
    void analyze_sharedPoliciesAndLayouts_preservesAndSemantics() {
        PolicyMappingConfig tenantPermission = permission("tenant-policy", "/secured");
        when(tenantPermission.shared()).thenReturn(true);

        AnnotationConfigMismatchAnalyzer.Analysis analysis = analyze(
                List.of(route("secured", UserView.class, TenantLayout.class)),
                Map.of("user", permission("user-policy", "/secured"), "tenant", tenantPermission),
                Map.of(
                        "user-policy", rolePolicy(List.of("USER")),
                        "tenant-policy", rolePolicy(List.of("TENANT"))),
                "/");

        assertTrue(analysis.diagnostics().isEmpty());
    }

    @Test
    void analyze_autoLayout_includesLayoutSecurityRequirement() {
        RouteRegistry registry = mock(RouteRegistry.class);
        when(registry.getRegisteredRoutes()).thenReturn(List.of(route("auto", AutoLayoutView.class)));
        when(registry.hasLayout("auto")).thenReturn(true);
        org.mockito.Mockito.doReturn(AdminAutoLayout.class).when(registry).getLayout("auto");

        AnnotationConfigMismatchAnalyzer.Analysis analysis = new AnnotationConfigMismatchAnalyzer(
                        Map.of("public", permission("permit", "/auto")), Map.of(), "/")
                .analyze(registry);

        assertEquals(1, analysis.mismatches().size());
        assertTrue(analysis.mismatches().getFirst().message().contains("role(ADMIN)"));
    }

    @Test
    void analyze_lowercaseMethod_isAProvenDenyMismatch() {
        PolicyMappingConfig lowercase = permission("permit", "/public");
        when(lowercase.methods()).thenReturn(Optional.of(List.of("get")));

        AnnotationConfigMismatchAnalyzer.Analysis analysis =
                analyze(List.of(route("public", PublicView.class)), Map.of("lowercase", lowercase), Map.of(), "/");

        assertEquals(1, analysis.mismatches().size());
        assertTrue(analysis.mismatches().getFirst().message().contains("requires deny"));
    }

    @Test
    void analyze_rootPathAndAlias_usesEffectiveHttpPaths() {
        RouteAliasData alias = new RouteAliasData(new ArrayList<>(), "alias", new LinkedHashMap<>(), PublicView.class);
        RouteData route = new RouteData(
                new ArrayList<>(), "public", new LinkedHashMap<>(), PublicView.class, new ArrayList<>(List.of(alias)));

        AnnotationConfigMismatchAnalyzer.Analysis analysis = analyze(
                List.of(route),
                Map.of(
                        "public", permission("permit", "public"),
                        "alias", permission("permit", "alias")),
                Map.of(),
                "/root/");

        assertTrue(analysis.diagnostics().isEmpty());
    }

    @Test
    void analyze_unannotatedConfigOwnedRoute_isNotAMismatch() {
        AnnotationConfigMismatchAnalyzer.Analysis analysis = analyze(
                List.of(route("owned", UnannotatedView.class)),
                Map.of("owned", permission("authenticated", "/owned")),
                Map.of(),
                "/");

        assertTrue(analysis.diagnostics().isEmpty());
    }

    @Test
    void analyze_customOrRoleAugmentingPolicy_isUnverifiedNotMismatch() {
        PolicyConfig augmenting = rolePolicy(List.of("USER"));
        when(augmenting.roles()).thenReturn(Map.of("SOURCE", List.of("USER")));

        AnnotationConfigMismatchAnalyzer.Analysis custom = analyze(
                List.of(route("user", UserView.class)),
                Map.of("custom", permission("custom-policy", "/user")),
                Map.of(),
                "/");
        AnnotationConfigMismatchAnalyzer.Analysis augmented = analyze(
                List.of(route("user", UserView.class)),
                Map.of("augmenting", permission("augmenting-policy", "/user")),
                Map.of("augmenting-policy", augmenting),
                "/");

        assertTrue(custom.mismatches().isEmpty());
        assertEquals(1, custom.diagnostics().size());
        assertEquals(
                AnnotationConfigMismatchAnalyzer.Kind.UNVERIFIED,
                custom.diagnostics().getFirst().kind());
        assertTrue(augmented.mismatches().isEmpty());
        assertEquals(1, augmented.diagnostics().size());
        assertEquals(
                AnnotationConfigMismatchAnalyzer.Kind.UNVERIFIED,
                augmented.diagnostics().getFirst().kind());
    }

    @Test
    void analyze_authenticationMechanismConstraint_isUnverifiedNotMismatch() {
        PolicyMappingConfig bearerOnly = permission("authenticated", "/authenticated");
        when(bearerOnly.authMechanism()).thenReturn(Optional.of(Set.of("bearer")));

        AnnotationConfigMismatchAnalyzer.Analysis analysis = analyze(
                List.of(route("authenticated", AuthenticatedView.class)),
                Map.of("bearer-only", bearerOnly),
                Map.of(),
                "/");

        assertTrue(analysis.mismatches().isEmpty());
        assertEquals(1, analysis.diagnostics().size());
        assertEquals(
                AnnotationConfigMismatchAnalyzer.Kind.UNVERIFIED,
                analysis.diagnostics().getFirst().kind());
        assertTrue(analysis.diagnostics().getFirst().message().contains("authentication-mechanism"));
    }

    @Test
    void analyze_parameterizedAnnotatedRoute_isUnverified() {
        RouteData parameterized = new RouteData(
                new ArrayList<>(),
                "users/:id",
                new LinkedHashMap<>(Map.of("id", mock(com.vaadin.flow.router.RouteParameterData.class))),
                UserView.class,
                new ArrayList<>());

        AnnotationConfigMismatchAnalyzer.Analysis analysis = analyze(
                List.of(parameterized),
                Map.of("users", permission("user-policy", "/users/*")),
                Map.of("user-policy", rolePolicy(List.of("USER"))),
                "/");

        assertTrue(analysis.mismatches().isEmpty());
        assertEquals(1, analysis.diagnostics().size());
        assertTrue(analysis.diagnostics().getFirst().message().contains("parameterized"));
    }

    private static AnnotationConfigMismatchAnalyzer.Analysis analyze(
            List<RouteData> routes,
            Map<String, PolicyMappingConfig> permissions,
            Map<String, PolicyConfig> rolePolicies,
            String rootPath) {
        RouteRegistry registry = mock(RouteRegistry.class);
        when(registry.getRegisteredRoutes()).thenReturn(routes);
        return new AnnotationConfigMismatchAnalyzer(permissions, rolePolicies, rootPath).analyze(registry);
    }

    private static RouteData route(
            String template, Class<? extends Component> target, Class<? extends RouterLayout>... layouts) {
        return new RouteData(
                new ArrayList<>(List.of(layouts)), template, new LinkedHashMap<>(), target, new ArrayList<>());
    }

    private static PolicyMappingConfig permission(String policy, String path) {
        PolicyMappingConfig permission = mock(PolicyMappingConfig.class);
        when(permission.enabled()).thenReturn(Optional.empty());
        when(permission.policy()).thenReturn(policy);
        when(permission.paths()).thenReturn(Optional.of(List.of(path)));
        when(permission.methods()).thenReturn(Optional.empty());
        when(permission.authMechanism()).thenReturn(Optional.empty());
        when(permission.shared()).thenReturn(false);
        when(permission.appliesTo()).thenReturn(PolicyMappingConfig.AppliesTo.ALL);
        return permission;
    }

    private static PolicyConfig rolePolicy(List<String> rolesAllowed) {
        PolicyConfig policy = mock(PolicyConfig.class);
        when(policy.rolesAllowed()).thenReturn(rolesAllowed);
        when(policy.roles()).thenReturn(Map.of());
        return policy;
    }

    @Tag("public-view")
    @AnonymousAllowed
    static class PublicView extends Component {}

    @Tag("authenticated-view")
    @PermitAll
    static class AuthenticatedView extends Component {}

    @Tag("user-view")
    @RolesAllowed("USER")
    static class UserView extends Component {}

    @Tag("duplicate-user-view")
    @RolesAllowed({"USER", "USER"})
    static class DuplicateUserView extends Component {}

    @Tag("denied-view")
    @DenyAll
    static class DeniedView extends Component {}

    @Tag("unannotated-view")
    static class UnannotatedView extends Component {}

    @Tag("tenant-layout")
    @RolesAllowed("TENANT")
    static class TenantLayout extends Component implements RouterLayout {}

    @Tag("auto-layout-view")
    @Route(value = "auto", autoLayout = true)
    @AnonymousAllowed
    static class AutoLayoutView extends Component {}

    @Layout
    @RolesAllowed("ADMIN")
    static class AdminAutoLayout extends Component implements RouterLayout {}
}
