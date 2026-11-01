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
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.util.concurrent.atomic.AtomicInteger;

import com.vaadin.flow.server.auth.AccessCheckResult;
import com.vaadin.flow.server.auth.AnnotatedViewAccessChecker;
import com.vaadin.flow.server.auth.NavigationContext;
import io.quarkus.arc.Unremovable;
import io.quarkus.test.QuarkusExtensionTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.mcollovati.quarkus.hilla.security.NavigationAccessControlConfigurer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An application scoped producer declares {@link AnnotatedViewAccessChecker}
 * but returns a subclass, and is ordered before the built-in checker.
 * {@code withAnnotatedViewAccessChecker()} still selects the built-in checker.
 */
class NavigationAccessControlConfigurerAnnotatedProducerTest {

    @RegisterExtension
    static final QuarkusExtensionTest config =
            NavigationAccessControlTestSupport.extension(Configuration.class, StricterChecker.class);

    @Test
    void annotatedViewAccessChecker_producerOfSubclass_notSelected() {
        var accessControl = NavigationAccessControlTestSupport.accessControl();

        assertThat(accessControl.hasAccessChecker(AnnotatedViewAccessChecker.class))
                .isTrue();
        accessControl.checkAccess(NavigationAccessControlTestSupport.context(), false);
        assertThat(StricterChecker.CALLS).hasValue(0);
    }

    static class Configuration {
        @Produces
        @Singleton
        NavigationAccessControlConfigurer configurer() {
            return new NavigationAccessControlConfigurer().withAnnotatedViewAccessChecker();
        }

        @Produces
        @ApplicationScoped
        @Priority(10)
        @Unremovable
        AnnotatedViewAccessChecker stricterChecker() {
            return new StricterChecker();
        }
    }

    static class StricterChecker extends AnnotatedViewAccessChecker {
        static final AtomicInteger CALLS = new AtomicInteger();

        @Override
        public AccessCheckResult check(NavigationContext context) {
            CALLS.incrementAndGet();
            return context.deny("Stricter checker denies");
        }
    }
}
