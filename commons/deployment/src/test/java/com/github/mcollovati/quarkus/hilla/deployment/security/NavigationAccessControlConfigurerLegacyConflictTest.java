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

import io.quarkus.test.QuarkusExtensionTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.mcollovati.quarkus.hilla.security.NavigationAccessControlConfigurer;

import static org.assertj.core.api.Assertions.assertThat;

class NavigationAccessControlConfigurerLegacyConflictTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = NavigationAccessControlTestSupport.extension(
                    Configuration.class, CustomNavigationAccessControl.class)
            .assertException(throwable -> assertThat(throwable)
                    .hasStackTraceContaining(
                            "NavigationAccessControl bean and an application NavigationAccessControlConfigurer bean")
                    .hasStackTraceContaining("Keep only the configurer"));

    @Test
    void legacyBeanAndApplicationConfigurer_startupFails() {
        // Startup failure is checked by the extension.
    }

    static class Configuration {
        @Produces
        @Singleton
        NavigationAccessControlConfigurer configurer() {
            return new NavigationAccessControlConfigurer().withAnnotatedViewAccessChecker();
        }
    }
}
