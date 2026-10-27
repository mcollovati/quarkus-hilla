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
package com.github.mcollovati.quarkus.hilla.deployment.security;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import com.vaadin.flow.server.auth.AccessCheckDecision;
import com.vaadin.flow.server.auth.AccessCheckResult;
import com.vaadin.flow.server.auth.AnnotatedViewAccessChecker;
import com.vaadin.flow.server.auth.NavigationContext;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusExtensionTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.mcollovati.quarkus.hilla.security.NavigationAccessControlConfigurer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An application subclass of {@link AnnotatedViewAccessChecker} replaces the
 * built-in checker, so {@code withAnnotatedViewAccessChecker()} selects the
 * subclass and its restrictions apply. The low priority would place the subclass
 * after a built-in checker in the list of available checkers.
 */
class NavigationAccessControlConfigurerAnnotatedSubclassTest {

    @RegisterExtension
    static final QuarkusExtensionTest config =
            NavigationAccessControlTestSupport.extension(Configuration.class, StricterChecker.class);

    @Test
    void annotatedViewAccessChecker_subclassReplacesBuiltIn() {
        var accessControl = NavigationAccessControlTestSupport.accessControl();
        var checker = Arc.container().instance(StricterChecker.class).get();

        assertThat(Arc.container().select(AnnotatedViewAccessChecker.class).stream())
                .singleElement()
                .isSameAs(checker);

        assertThat(accessControl
                        .checkAccess(NavigationAccessControlTestSupport.context(), false)
                        .decision())
                .isEqualTo(AccessCheckDecision.DENY);
        assertThat(checker.calls()).isEqualTo(1);
    }

    static class Configuration {
        @Produces
        @Singleton
        NavigationAccessControlConfigurer configurer() {
            return new NavigationAccessControlConfigurer().withAnnotatedViewAccessChecker();
        }
    }

    @Singleton
    @Priority(-1)
    static class StricterChecker extends AnnotatedViewAccessChecker {
        private int calls;

        @Override
        public AccessCheckResult check(NavigationContext context) {
            calls++;
            return context.deny("Stricter checker denies");
        }

        int calls() {
            return calls;
        }
    }
}
