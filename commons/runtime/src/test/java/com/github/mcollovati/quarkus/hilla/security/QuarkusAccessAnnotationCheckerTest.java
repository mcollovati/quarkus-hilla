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
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;

import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuarkusAccessAnnotationCheckerTest {

    @Test
    void securityTargetCache_preservesHierarchyAndAccessSemantics() {
        QuarkusAccessAnnotationChecker checker = new QuarkusAccessAnnotationChecker(true);

        AnnotatedElement securityTarget = checker.getSecurityTarget(AdminView.class);

        assertSame(SecuredView.class, securityTarget);
        assertSame(securityTarget, checker.getSecurityTarget(AdminView.class));
        assertFalse(checker.hasAccess(AdminView.class, () -> "user", ignored -> false));
        assertTrue(checker.hasAccess(AdminView.class, () -> "admin", "ADMIN"::equals));
    }

    @Test
    void developmentModeBypass_preservesHierarchyAndAccessSemantics() {
        QuarkusAccessAnnotationChecker checker = new QuarkusAccessAnnotationChecker(false);

        assertSame(SecuredView.class, checker.getSecurityTarget(AdminView.class));
        assertFalse(checker.hasAccess(AdminView.class, () -> "user", ignored -> false));
        assertTrue(checker.hasAccess(AdminView.class, () -> "admin", "ADMIN"::equals));
    }

    @Test
    void methodTargetCache_preservesMethodAndClassAnnotationPrecedence() throws Exception {
        QuarkusAccessAnnotationChecker checker = new QuarkusAccessAnnotationChecker(true);
        Method publicEndpoint = SecuredView.class.getMethod("publicEndpoint");
        Method securedEndpoint = SecuredView.class.getMethod("securedEndpoint");

        assertSame(publicEndpoint, checker.getSecurityTarget(publicEndpoint));
        assertSame(SecuredView.class, checker.getSecurityTarget(securedEndpoint));
        assertTrue(checker.hasAccess(publicEndpoint, null, ignored -> false));
        assertFalse(checker.hasAccess(securedEndpoint, () -> "user", ignored -> false));
        assertTrue(checker.hasAccess(securedEndpoint, () -> "admin", "ADMIN"::equals));
    }

    @RolesAllowed("ADMIN")
    static class SecuredView {

        @AnonymousAllowed
        public void publicEndpoint() {}

        public void securedEndpoint() {}
    }

    static class AdminView extends SecuredView {}
}
