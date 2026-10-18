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

import java.util.Map;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.Router;
import com.vaadin.flow.router.internal.NavigationRouteTarget;
import com.vaadin.flow.router.internal.RouteTarget;
import com.vaadin.flow.server.RouteRegistry;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.auth.AccessCheckResult;
import com.vaadin.flow.server.auth.NavigationAccessControl;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.security.HttpSecurityPolicy.CheckResult;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.Test;

import com.github.mcollovati.quarkus.hilla.QuarkusEndpointConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HillaSecurityPolicyTest {

    @Test
    void checkPermission_beforeServiceInitializationDeniesWithoutFailure() {
        QuarkusEndpointConfiguration endpointConfiguration = mock(QuarkusEndpointConfiguration.class);
        when(endpointConfiguration.getNormalizedEndpointPrefix()).thenReturn("/connect");
        EndpointUtil endpointUtil = mock(EndpointUtil.class);
        RoutingContext context = context("/first-request");
        HillaSecurityPolicy policy = new HillaSecurityPolicy(
                mock(com.vaadin.flow.server.auth.NavigationAccessControl.class), endpointConfiguration, endpointUtil);

        CheckResult result = policy.checkPermission(
                        context,
                        Uni.createFrom().item(mock(SecurityIdentity.class)),
                        mock(
                                io.quarkus.vertx.http.runtime.security.HttpSecurityPolicy.AuthorizationRequestContext
                                        .class))
                .await()
                .indefinitely();

        assertFalse(result.isPermitted());
    }

    @Test
    void checkPermission_matchesPermitAllPathsAgainstNormalizedPath() {
        HillaSecurityPolicy policy = policy();

        assertFalse(check(policy, context("/VAADIN/../hilla-admin", "/hilla-admin"), mock(SecurityIdentity.class))
                .isPermitted());
        assertFalse(check(policy, context("/VAADIN%2F..%2Fhilla-admin"), mock(SecurityIdentity.class))
                .isPermitted());
        assertTrue(check(policy, context("/VAADIN/client.js"), mock(SecurityIdentity.class))
                .isPermitted());
    }

    @Test
    void checkPermission_usesQuarkusCanonicalPathForPermitAndRouteMatching() {
        TestPolicy policy = policy();
        policy.setFileRoutesManifestExpected(true);
        policy.onVaadinServiceInit(new ServiceInitEvent(service()));
        SecurityIdentity identity = mock(SecurityIdentity.class);
        RoutingContext matrixPath = context("/hilla-admin;a=b");
        RoutingContext encodedMatrixPath = context("/hilla-admin%3Ba=b");
        when(policy.routeUtil.checkRouteAccess("/hilla-admin", "/hilla-admin;a=b", identity))
                .thenReturn(AuthorizationDecision.DENY);
        when(policy.routeUtil.checkRouteAccess("/hilla-admin", "/hilla-admin%3Ba=b", identity))
                .thenReturn(AuthorizationDecision.DENY);

        assertFalse(check(policy, matrixPath, identity).isPermitted());
        assertFalse(check(policy, encodedMatrixPath, identity).isPermitted());
        verify(policy.routeUtil).checkRouteAccess("/hilla-admin", "/hilla-admin;a=b", identity);
        verify(policy.routeUtil).checkRouteAccess("/hilla-admin", "/hilla-admin%3Ba=b", identity);
    }

    @Test
    void checkPermission_preservesEncodedSlashWhenResolvingFlowRoutes() {
        NavigationAccessControl accessControl = mock(NavigationAccessControl.class);
        when(accessControl.isEnabled()).thenReturn(true);
        when(accessControl.checkAccess(any(), anyBoolean())).thenReturn(AccessCheckResult.deny("admin only"));
        QuarkusEndpointConfiguration endpointConfiguration = mock(QuarkusEndpointConfiguration.class);
        when(endpointConfiguration.getNormalizedEndpointPrefix()).thenReturn("/connect");
        TestPolicy policy = new TestPolicy(accessControl, endpointConfiguration, mock(EndpointUtil.class));
        policy.setFileRoutesManifestExpected(true);

        RouteRegistry registry = mock(RouteRegistry.class);
        NavigationRouteTarget target =
                new NavigationRouteTarget("items/:id", new RouteTarget(ProtectedFlowView.class), Map.of("id", "a/b"));
        when(registry.getNavigationRouteTarget("items/a%2Fb")).thenReturn(target);
        policy.onVaadinServiceInit(new ServiceInitEvent(service(registry)));
        SecurityIdentity identity = mock(SecurityIdentity.class);
        when(identity.isAnonymous()).thenReturn(true);

        assertFalse(check(policy, context("/items/a%2Fb"), identity).isPermitted());
        verify(accessControl).checkAccess(any(), anyBoolean());
    }

    @Test
    void checkPermission_matchesWebIconsAgainstNormalizedPath() {
        TestPolicy policy = policy();
        policy.setFileRoutesManifestExpected(true);
        policy.onVaadinServiceInit(new ServiceInitEvent(service()));
        RoutingContext context = context("/icons/../hilla-admin", "/hilla-admin");
        SecurityIdentity identity = mock(SecurityIdentity.class);
        when(policy.webIconsRequestMatcher.isWebIconRequest("/icons/../hilla-admin"))
                .thenReturn(true);
        when(policy.routeUtil.checkRouteAccess("/hilla-admin", "/hilla-admin", identity))
                .thenReturn(AuthorizationDecision.DENY);

        assertFalse(check(policy, context, identity).isPermitted());
        verify(policy.webIconsRequestMatcher).isWebIconRequest("/hilla-admin");
    }

    @Test
    void checkPermission_keepsNormalizedWebIconRequestsPermitted() {
        TestPolicy policy = policy();
        policy.onVaadinServiceInit(new ServiceInitEvent(service()));
        when(policy.webIconsRequestMatcher.isWebIconRequest("/icons/favicon.svg"))
                .thenReturn(true);

        assertTrue(check(
                        policy,
                        context("/icons/../icons/favicon.svg", "/icons/favicon.svg"),
                        mock(SecurityIdentity.class))
                .isPermitted());
    }

    @Test
    void routeUtilInitialization_waitsForServiceAndClassificationInEitherOrder() {
        TestPolicy classificationFirst = policy();
        classificationFirst.setFileRoutesManifestExpected(false);
        assertEquals(0, classificationFirst.createdRouteUtils);
        classificationFirst.onVaadinServiceInit(new ServiceInitEvent(service()));
        assertEquals(1, classificationFirst.createdRouteUtils);
        verify(classificationFirst.routeUtil).initializeProductionSnapshot();

        TestPolicy serviceFirst = policy();
        serviceFirst.onVaadinServiceInit(new ServiceInitEvent(service()));
        assertEquals(0, serviceFirst.createdRouteUtils);
        serviceFirst.setFileRoutesManifestExpected(false);
        assertEquals(1, serviceFirst.createdRouteUtils);
        verify(serviceFirst.routeUtil).initializeProductionSnapshot();
    }

    @Test
    void routeUtilInitialization_propagatesFailureWithoutPublishingEvaluator() {
        TestPolicy policy = policy();
        policy.onVaadinServiceInit(new ServiceInitEvent(service()));
        doThrow(new IllegalStateException("invalid manifest"))
                .when(policy.routeUtil)
                .initializeProductionSnapshot();

        assertThrows(IllegalStateException.class, () -> policy.setFileRoutesManifestExpected(true));
        assertFalse(check(policy, context("/client-route"), mock(SecurityIdentity.class))
                .isPermitted());
    }

    @Test
    void checkPermission_mapsHillaAuthorizationDecisions() {
        TestPolicy policy = policy();
        policy.setFileRoutesManifestExpected(true);
        policy.onVaadinServiceInit(new ServiceInitEvent(service()));
        RoutingContext context = context("/client-route");
        SecurityIdentity identity = mock(SecurityIdentity.class);

        when(policy.routeUtil.checkRouteAccess("/client-route", "/client-route", identity))
                .thenReturn(AuthorizationDecision.ALLOW);
        assertTrue(check(policy, context, identity).isPermitted());

        when(policy.routeUtil.checkRouteAccess("/client-route", "/client-route", identity))
                .thenReturn(AuthorizationDecision.DENY);
        assertFalse(check(policy, context, identity).isPermitted());

        when(policy.routeUtil.checkRouteAccess("/client-route", "/client-route", identity))
                .thenReturn(AuthorizationDecision.NO_MATCH);
        assertTrue(check(policy, context, identity).isPermitted());
    }

    private static CheckResult check(HillaSecurityPolicy policy, RoutingContext context, SecurityIdentity identity) {
        return policy.checkPermission(
                        context,
                        Uni.createFrom().item(identity),
                        mock(
                                io.quarkus.vertx.http.runtime.security.HttpSecurityPolicy.AuthorizationRequestContext
                                        .class))
                .await()
                .indefinitely();
    }

    private static TestPolicy policy() {
        QuarkusEndpointConfiguration endpointConfiguration = mock(QuarkusEndpointConfiguration.class);
        when(endpointConfiguration.getNormalizedEndpointPrefix()).thenReturn("/connect");
        return new TestPolicy(
                mock(com.vaadin.flow.server.auth.NavigationAccessControl.class),
                endpointConfiguration,
                mock(EndpointUtil.class));
    }

    private static VaadinService service() {
        RouteRegistry registry = mock(RouteRegistry.class);
        when(registry.getNavigationRouteTarget(any())).thenReturn(null);
        return service(registry);
    }

    private static VaadinService service(RouteRegistry registry) {
        VaadinService service = mock(VaadinService.class);
        Router router = mock(Router.class);
        when(service.getRouter()).thenReturn(router);
        when(router.getRegistry()).thenReturn(registry);
        when(service.getDeploymentConfiguration())
                .thenReturn(mock(com.vaadin.flow.function.DeploymentConfiguration.class));
        return service;
    }

    private static RoutingContext context(String path) {
        return context(path, path);
    }

    private static RoutingContext context(String rawPath, String normalizedPath) {
        RoutingContext context = mock(RoutingContext.class);
        HttpServerRequest request = mock(HttpServerRequest.class);
        when(context.request()).thenReturn(request);
        when(request.path()).thenReturn(rawPath);
        when(request.params()).thenReturn(io.vertx.core.MultiMap.caseInsensitiveMultiMap());
        when(context.normalizedPath()).thenReturn(normalizedPath);
        return context;
    }

    private static final class TestPolicy extends HillaSecurityPolicy {

        private final RouteUtil routeUtil = mock(RouteUtil.class);
        private final WebIconsRequestMatcher webIconsRequestMatcher = mock(WebIconsRequestMatcher.class);
        private int createdRouteUtils;

        private TestPolicy(
                com.vaadin.flow.server.auth.NavigationAccessControl accessControl,
                QuarkusEndpointConfiguration endpointConfiguration,
                EndpointUtil endpointUtil) {
            super(accessControl, endpointConfiguration, endpointUtil);
        }

        @Override
        RouteUtil createRouteUtil(VaadinService service, boolean manifestExpected) {
            createdRouteUtils++;
            return routeUtil;
        }

        @Override
        WebIconsRequestMatcher createWebIconsRequestMatcher(VaadinService service) {
            return webIconsRequestMatcher;
        }
    }

    private static final class ProtectedFlowView extends Div {}
}
