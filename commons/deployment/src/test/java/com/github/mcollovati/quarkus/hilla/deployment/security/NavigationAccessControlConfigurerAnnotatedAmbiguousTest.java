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

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import com.vaadin.flow.server.auth.AnnotatedViewAccessChecker;
import io.quarkus.test.QuarkusExtensionTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.mcollovati.quarkus.hilla.security.NavigationAccessControlConfigurer;

import static org.assertj.core.api.Assertions.assertThat;

class NavigationAccessControlConfigurerAnnotatedAmbiguousTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = NavigationAccessControlTestSupport.extension(
                    Configuration.class, FirstChecker.class, SecondChecker.class)
            .assertException(throwable -> assertThat(throwable)
                    .hasStackTraceContaining("found more than one AnnotatedViewAccessChecker")
                    .hasStackTraceContaining(FirstChecker.class.getName())
                    .hasStackTraceContaining(SecondChecker.class.getName()));

    @Test
    void annotatedViewAccessChecker_twoApplicationCheckers_failsStartup() {
        // The startup must fail before this test runs.
    }

    static class Configuration {
        @Produces
        @Singleton
        NavigationAccessControlConfigurer configurer() {
            return new NavigationAccessControlConfigurer().withAnnotatedViewAccessChecker();
        }
    }

    @Singleton
    static class FirstChecker extends AnnotatedViewAccessChecker {}

    @Singleton
    static class SecondChecker extends AnnotatedViewAccessChecker {}
}
