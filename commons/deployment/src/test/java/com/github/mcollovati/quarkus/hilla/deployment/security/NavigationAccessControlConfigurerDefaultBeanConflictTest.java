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

import io.quarkus.arc.DefaultBean;
import io.quarkus.test.QuarkusExtensionTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.mcollovati.quarkus.hilla.security.NavigationAccessControlConfigurer;

import static org.assertj.core.api.Assertions.assertThat;

class NavigationAccessControlConfigurerDefaultBeanConflictTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = NavigationAccessControlTestSupport.extension(
                    Configuration.class, CustomNavigationAccessControl.class)
            .assertException(throwable -> assertThat(throwable)
                    .hasStackTraceContaining(
                            "NavigationAccessControl bean and an application NavigationAccessControlConfigurer bean")
                    .hasStackTraceContaining("Keep only the configurer"));

    @Test
    void legacyBeanAndPrioritizedDefaultConfigurer_startupFails() {
        // Startup failure is checked by the extension.
    }

    // An application default bean with a priority replaces the extension's
    // default bean, so it must count as an application configurer.
    static class Configuration {
        @Produces
        @Singleton
        @DefaultBean
        @Priority(1)
        NavigationAccessControlConfigurer configurer() {
            return new NavigationAccessControlConfigurer().withAnnotatedViewAccessChecker();
        }
    }
}
