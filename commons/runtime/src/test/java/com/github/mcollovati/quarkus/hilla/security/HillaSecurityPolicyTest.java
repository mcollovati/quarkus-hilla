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

import com.vaadin.flow.router.Router;
import com.vaadin.flow.server.RouteRegistry;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinService;
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
        assertTrue(check(policy, context("/VAADIN/client.js"), mock(SecurityIdentity.class))
                .isPermitted());
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
        when(policy.routeUtil.checkRouteAccess(context, identity)).thenReturn(AuthorizationDecision.DENY);

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

        when(policy.routeUtil.checkRouteAccess(context, identity)).thenReturn(AuthorizationDecision.ALLOW);
        assertTrue(check(policy, context, identity).isPermitted());

        when(policy.routeUtil.checkRouteAccess(context, identity)).thenReturn(AuthorizationDecision.DENY);
        assertFalse(check(policy, context, identity).isPermitted());

        when(policy.routeUtil.checkRouteAccess(context, identity)).thenReturn(AuthorizationDecision.NO_MATCH);
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
        VaadinService service = mock(VaadinService.class);
        Router router = mock(Router.class);
        RouteRegistry registry = mock(RouteRegistry.class);
        when(service.getRouter()).thenReturn(router);
        when(router.getRegistry()).thenReturn(registry);
        when(registry.getNavigationRouteTarget(any())).thenReturn(null);
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
}
