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

import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.Router;
import com.vaadin.flow.server.auth.AccessCheckDecision;
import com.vaadin.flow.server.auth.AccessCheckResult;
import com.vaadin.flow.server.auth.NavigationContext;
import io.quarkus.security.identity.SecurityIdentity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuarkusHttpPermissionNavigationAccessCheckerTest {

    @Test
    void check_configAllowCannotOverrideAnnotationDeny() {
        SecurityIdentity identity = TestSecurityIdentity.authenticated("user", "USER");
        QuarkusAccessPathChecker pathChecker = mock(QuarkusAccessPathChecker.class);
        QuarkusAnnotatedViewAccessChecker annotationChecker = mock(QuarkusAnnotatedViewAccessChecker.class);
        when(pathChecker.check(anyString(), any(), any()))
                .thenReturn(QuarkusAccessPathChecker.AccessCheck.allow("permit", identity));
        when(annotationChecker.check(any())).thenReturn(AccessCheckResult.deny("annotation"));

        AccessCheckResult result = new QuarkusHttpPermissionNavigationAccessChecker(pathChecker, annotationChecker)
                .check(navigationContext(identity, true));

        assertEquals(AccessCheckDecision.DENY, result.decision());
    }

    @Test
    void check_configDenyShortCircuitsAnnotations() {
        SecurityIdentity identity = TestSecurityIdentity.authenticated("user", "USER");
        QuarkusAccessPathChecker pathChecker = mock(QuarkusAccessPathChecker.class);
        QuarkusAnnotatedViewAccessChecker annotationChecker = mock(QuarkusAnnotatedViewAccessChecker.class);
        when(pathChecker.check(anyString(), any(), any()))
                .thenReturn(QuarkusAccessPathChecker.AccessCheck.deny("deny", identity));

        AccessCheckResult result = new QuarkusHttpPermissionNavigationAccessChecker(pathChecker, annotationChecker)
                .check(navigationContext(identity, true));

        assertEquals(AccessCheckDecision.DENY, result.decision());
        verify(annotationChecker, never()).check(any());
    }

    @Test
    void check_configOwnsUnannotatedRoute() {
        SecurityIdentity identity = TestSecurityIdentity.authenticated("user", "USER");
        QuarkusAccessPathChecker pathChecker = mock(QuarkusAccessPathChecker.class);
        QuarkusAnnotatedViewAccessChecker annotationChecker = mock(QuarkusAnnotatedViewAccessChecker.class);
        when(pathChecker.check(anyString(), any(), any()))
                .thenReturn(QuarkusAccessPathChecker.AccessCheck.allow("permit", identity));
        when(annotationChecker.check(any())).thenReturn(AccessCheckResult.neutral());

        AccessCheckResult result = new QuarkusHttpPermissionNavigationAccessChecker(pathChecker, annotationChecker)
                .check(navigationContext(identity, true));

        assertEquals(AccessCheckDecision.ALLOW, result.decision());
    }

    @Test
    void check_directClassificationUsesExistingQuarkusDecisionOnly() {
        SecurityIdentity identity = TestSecurityIdentity.anonymous();
        QuarkusAccessPathChecker pathChecker = mock(QuarkusAccessPathChecker.class);
        QuarkusAnnotatedViewAccessChecker annotationChecker = mock(QuarkusAnnotatedViewAccessChecker.class);
        when(pathChecker.checkCurrentRequest(anyString(), any(), any()))
                .thenReturn(QuarkusAccessPathChecker.AccessCheck.noMatch(identity));
        when(annotationChecker.check(any())).thenReturn(AccessCheckResult.allow());

        AccessCheckResult result = new QuarkusHttpPermissionNavigationAccessChecker(pathChecker, annotationChecker)
                .check(navigationContext(identity, false));

        assertEquals(AccessCheckDecision.ALLOW, result.decision());
        verify(pathChecker, never()).check(anyString(), any(), any());
    }

    private static NavigationContext navigationContext(SecurityIdentity identity, boolean navigating) {
        return new NavigationContext(
                mock(Router.class),
                Object.class,
                new Location("secure"),
                RouteParameters.empty(),
                identity.isAnonymous() ? null : identity.getPrincipal(),
                identity::hasRole,
                false,
                navigating);
    }
}
