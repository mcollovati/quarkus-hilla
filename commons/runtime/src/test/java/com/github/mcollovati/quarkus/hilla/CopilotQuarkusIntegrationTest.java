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
package com.github.mcollovati.quarkus.hilla;

import java.util.List;

import com.vaadin.flow.server.auth.AccessCheckDecisionResolver;
import com.vaadin.flow.server.auth.AnnotatedViewAccessChecker;
import com.vaadin.flow.server.auth.NavigationAccessChecker;
import com.vaadin.flow.server.auth.NavigationAccessControl;
import org.junit.jupiter.api.Test;

import com.github.mcollovati.quarkus.hilla.security.QuarkusHttpPermissionNavigationAccessChecker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class CopilotQuarkusIntegrationTest {

    @Test
    void hasViewSecurity_recognizesQuarkusCompositeChecker() {
        NavigationAccessControl accessControl = accessControl(mock(QuarkusHttpPermissionNavigationAccessChecker.class));

        assertTrue(CopilotQuarkusIntegration.hasViewSecurity(accessControl));
    }

    @Test
    void hasViewSecurity_retainsStockAnnotatedCheckerSupport() {
        NavigationAccessControl accessControl = accessControl(mock(AnnotatedViewAccessChecker.class));

        assertTrue(CopilotQuarkusIntegration.hasViewSecurity(accessControl));
        accessControl.setEnabled(false);
        assertFalse(CopilotQuarkusIntegration.hasViewSecurity(accessControl));
    }

    private static NavigationAccessControl accessControl(NavigationAccessChecker checker) {
        NavigationAccessControl accessControl =
                new NavigationAccessControl(List.of(checker), mock(AccessCheckDecisionResolver.class));
        accessControl.setEnabled(true);
        return accessControl;
    }
}
