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

import jakarta.annotation.security.RolesAllowed;
import java.util.List;
import java.util.Map;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.Router;
import com.vaadin.flow.router.RouterLayout;
import com.vaadin.flow.router.internal.NavigationRouteTarget;
import com.vaadin.flow.router.internal.RouteTarget;
import com.vaadin.flow.server.RouteRegistry;
import com.vaadin.flow.server.auth.AccessCheckDecision;
import com.vaadin.flow.server.auth.AccessCheckResult;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.server.auth.NavigationContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuarkusAnnotatedViewAccessCheckerTest {

    @Test
    void check_aliasUsesExactParentLayoutSecurityAnnotations() {
        RouteRegistry registry = mock(RouteRegistry.class);
        Router router = mock(Router.class);
        when(router.getRegistry()).thenReturn(registry);

        when(registry.getNavigationRouteTarget("alias"))
                .thenReturn(new NavigationRouteTarget(
                        "alias", new RouteTarget(UnannotatedView.class, List.of(AdminLayout.class)), Map.of()));

        AccessCheckResult result = new QuarkusAnnotatedViewAccessChecker()
                .check(new NavigationContext(
                        router,
                        UnannotatedView.class,
                        new Location("alias"),
                        RouteParameters.empty(),
                        () -> "user",
                        ignored -> false,
                        false,
                        true));

        assertEquals(AccessCheckDecision.DENY, result.decision());
    }

    @Test
    void check_unannotatedRouteWithoutSecuredLayoutsStaysNeutral() {
        RouteRegistry registry = mock(RouteRegistry.class);
        Router router = mock(Router.class);
        when(router.getRegistry()).thenReturn(registry);
        when(registry.getNavigationRouteTarget("plain"))
                .thenReturn(new NavigationRouteTarget(
                        "plain", new RouteTarget(UnannotatedView.class, List.of()), Map.of()));

        AccessCheckResult result = new QuarkusAnnotatedViewAccessChecker()
                .check(new NavigationContext(
                        router,
                        UnannotatedView.class,
                        new Location("/plain"),
                        RouteParameters.empty(),
                        null,
                        ignored -> false,
                        false,
                        true));

        assertEquals(AccessCheckDecision.NEUTRAL, result.decision());
    }

    @Test
    void check_routeResolutionFailureDenies() {
        RouteRegistry registry = mock(RouteRegistry.class);
        Router router = mock(Router.class);
        when(router.getRegistry()).thenReturn(registry);
        when(registry.getNavigationRouteTarget("broken")).thenThrow(new IllegalStateException("broken registry"));

        AccessCheckResult result = new QuarkusAnnotatedViewAccessChecker()
                .check(new NavigationContext(
                        router,
                        UnannotatedView.class,
                        new Location("broken"),
                        RouteParameters.empty(),
                        null,
                        ignored -> false,
                        false,
                        true));

        assertEquals(AccessCheckDecision.DENY, result.decision());
    }

    @Test
    void check_permissiveTargetCannotOverrideSecuredAliasLayout() {
        RouteRegistry registry = mock(RouteRegistry.class);
        Router router = mock(Router.class);
        when(router.getRegistry()).thenReturn(registry);
        when(registry.getNavigationRouteTarget("public-alias"))
                .thenReturn(new NavigationRouteTarget(
                        "public-alias", new RouteTarget(PublicView.class, List.of(AdminLayout.class)), Map.of()));
        QuarkusAnnotatedViewAccessChecker checker = new QuarkusAnnotatedViewAccessChecker();

        AccessCheckResult guest = checker.check(new NavigationContext(
                router,
                PublicView.class,
                new Location("public-alias"),
                RouteParameters.empty(),
                () -> "guest",
                ignored -> false,
                false,
                true));
        AccessCheckResult admin = checker.check(new NavigationContext(
                router,
                PublicView.class,
                new Location("public-alias"),
                RouteParameters.empty(),
                () -> "admin",
                "ADMIN"::equals,
                false,
                true));

        assertEquals(AccessCheckDecision.DENY, guest.decision());
        assertEquals(AccessCheckDecision.ALLOW, admin.decision());
    }

    static class UnannotatedView extends Component {}

    @AnonymousAllowed
    static class PublicView extends Component {}

    @RolesAllowed("ADMIN")
    static class AdminLayout extends Component implements RouterLayout {}
}
