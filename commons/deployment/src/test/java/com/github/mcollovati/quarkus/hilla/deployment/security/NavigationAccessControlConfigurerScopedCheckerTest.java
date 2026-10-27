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

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.util.concurrent.atomic.AtomicInteger;

import com.vaadin.flow.server.auth.AccessCheckResult;
import com.vaadin.flow.server.auth.AnnotatedViewAccessChecker;
import com.vaadin.flow.server.auth.NavigationAccessChecker;
import com.vaadin.flow.server.auth.NavigationContext;
import io.quarkus.test.QuarkusExtensionTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.mcollovati.quarkus.hilla.security.NavigationAccessControlConfigurer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A request scoped checker that is not selected and comes before the built-in
 * checker. Selecting the built-in checker at startup must not create it.
 */
class NavigationAccessControlConfigurerScopedCheckerTest {

    @RegisterExtension
    static final QuarkusExtensionTest config =
            NavigationAccessControlTestSupport.extension(Configuration.class, RequestScopedChecker.class);

    @Test
    void annotatedViewAccessChecker_unselectedRequestScopedChecker_notCreated() {
        var accessControl = NavigationAccessControlTestSupport.accessControl();

        assertThat(accessControl.hasAccessChecker(AnnotatedViewAccessChecker.class))
                .isTrue();
        assertThat(RequestScopedChecker.INSTANCES.get()).isZero();
    }

    static class Configuration {
        @Produces
        @Singleton
        NavigationAccessControlConfigurer configurer() {
            return new NavigationAccessControlConfigurer().withAnnotatedViewAccessChecker();
        }
    }

    @RequestScoped
    @Priority(10)
    static class RequestScopedChecker implements NavigationAccessChecker {
        static final AtomicInteger INSTANCES = new AtomicInteger();

        // Counted after creation of the contextual instance; the client proxy
        // also calls the constructor.
        @PostConstruct
        void created() {
            INSTANCES.incrementAndGet();
        }

        @Override
        public AccessCheckResult check(NavigationContext context) {
            return context.neutral();
        }
    }
}
