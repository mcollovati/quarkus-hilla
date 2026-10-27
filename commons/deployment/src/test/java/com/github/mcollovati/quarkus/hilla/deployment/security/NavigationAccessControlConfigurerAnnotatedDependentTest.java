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

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.util.concurrent.atomic.AtomicInteger;

import com.vaadin.flow.server.auth.AccessCheckDecision;
import com.vaadin.flow.server.auth.AccessCheckResult;
import com.vaadin.flow.server.auth.AnnotatedViewAccessChecker;
import com.vaadin.flow.server.auth.NavigationContext;
import io.quarkus.test.QuarkusExtensionTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.mcollovati.quarkus.hilla.security.NavigationAccessControlConfigurer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A {@code @Dependent} replacement of the annotated checker is found as
 * {@link AnnotatedViewAccessChecker} and as navigation access checker, but must
 * be instantiated and selected once.
 */
class NavigationAccessControlConfigurerAnnotatedDependentTest {

    @RegisterExtension
    static final QuarkusExtensionTest config =
            NavigationAccessControlTestSupport.extension(Configuration.class, DependentChecker.class);

    @Test
    void annotatedViewAccessChecker_dependentReplacement_selectedOnce() {
        var accessControl = NavigationAccessControlTestSupport.accessControl();

        assertThat(DependentChecker.INSTANCES.get()).isEqualTo(1);
        assertThat(accessControl
                        .checkAccess(NavigationAccessControlTestSupport.context(), false)
                        .decision())
                .isEqualTo(AccessCheckDecision.DENY);
        assertThat(DependentChecker.CALLS.get()).isEqualTo(1);
    }

    static class Configuration {
        @Produces
        @Singleton
        NavigationAccessControlConfigurer configurer() {
            return new NavigationAccessControlConfigurer().withAnnotatedViewAccessChecker();
        }
    }

    @Dependent
    static class DependentChecker extends AnnotatedViewAccessChecker {
        static final AtomicInteger INSTANCES = new AtomicInteger();
        static final AtomicInteger CALLS = new AtomicInteger();

        DependentChecker() {
            INSTANCES.incrementAndGet();
        }

        @Override
        public AccessCheckResult check(NavigationContext context) {
            CALLS.incrementAndGet();
            return context.deny("Dependent checker denies");
        }
    }
}
