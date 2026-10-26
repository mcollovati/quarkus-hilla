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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.util.List;

import com.vaadin.flow.server.auth.AccessCheckDecision;
import com.vaadin.flow.server.auth.AccessCheckResult;
import com.vaadin.flow.server.auth.AnnotatedViewAccessChecker;
import com.vaadin.flow.server.auth.NavigationAccessChecker;
import com.vaadin.flow.server.auth.NavigationAccessControl;
import com.vaadin.flow.server.auth.NavigationContext;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.mcollovati.quarkus.hilla.security.NavigationAccessControlConfigurer;

import static org.assertj.core.api.Assertions.assertThat;

class NavigationAccessControlConfigurerSelectionTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = NavigationAccessControlTestSupport.extension(
            Configuration.class, SelectedChecker.class, UnselectedChecker.class);

    @Test
    void applicationConfigurer_selectsOnlyMatchingProxiedChecker() throws ReflectiveOperationException {
        var accessControl = NavigationAccessControlTestSupport.accessControl();
        var checker = Arc.container().instance(SelectedChecker.class).get();
        assertThat(NavigationAccessControlTestSupport.field(accessControl, NavigationAccessControl.class, "loginUrl"))
                .isEqualTo("/login");
        assertThat(checker).isInstanceOf(ClientProxy.class);
        assertThat(accessControl.hasAccessChecker(SelectedChecker.class)).isTrue();
        assertThat(accessControl.hasAccessChecker(UnselectedChecker.class)).isFalse();
        assertThat(accessControl.hasAccessChecker(AnnotatedViewAccessChecker.class))
                .isFalse();
        assertThat(accessControl
                        .checkAccess(NavigationAccessControlTestSupport.context(), false)
                        .decision())
                .isEqualTo(AccessCheckDecision.ALLOW);
        assertThat(checker.calls()).isEqualTo(1);
        assertThat(Arc.container().instance(UnselectedChecker.class).get().calls())
                .isZero();
    }

    static class Configuration {
        @Produces
        @Singleton
        NavigationAccessControlConfigurer configurer() {
            NavigationAccessChecker explicit = NavigationContext::neutral;
            return new NavigationAccessControlConfigurer()
                    .withAvailableNavigationAccessCheckers(SelectedChecker.class::isInstance)
                    .withNavigationAccessChecker(explicit)
                    .withNavigationAccessCheckers(List.of(explicit));
        }
    }

    @ApplicationScoped
    static class SelectedChecker implements NavigationAccessChecker {
        private int calls;

        @Override
        public AccessCheckResult check(NavigationContext context) {
            calls++;
            return context.allow();
        }

        int calls() {
            return calls;
        }
    }

    @ApplicationScoped
    static class UnselectedChecker implements NavigationAccessChecker {
        private int calls;

        @Override
        public AccessCheckResult check(NavigationContext context) {
            calls++;
            return context.deny("Unselected checker must not run");
        }

        int calls() {
            return calls;
        }
    }
}
