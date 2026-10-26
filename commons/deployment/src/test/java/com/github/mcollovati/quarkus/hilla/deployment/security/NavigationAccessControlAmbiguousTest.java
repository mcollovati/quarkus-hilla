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

import jakarta.inject.Singleton;

import com.vaadin.flow.server.auth.NavigationAccessControl;
import io.quarkus.test.QuarkusExtensionTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

class NavigationAccessControlAmbiguousTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = NavigationAccessControlTestSupport.extension(
                    FirstAccessControl.class, SecondAccessControl.class)
            .overrideConfigKey("quarkus.http.auth.form.enabled", "false")
            .assertException(throwable -> assertThat(throwable)
                    .hasStackTraceContaining("AmbiguousResolutionException")
                    .hasStackTraceContaining(FirstAccessControl.class.getName())
                    .hasStackTraceContaining(SecondAccessControl.class.getName())
                    .hasStackTraceContaining("routeUnifyingServiceInitListener"));

    @Test
    void routeUnifyingListener_ambiguousAccessControlFailsStartup() {
        // With form login disabled, only the route unifying listener resolves access control.
    }

    @Singleton
    static class FirstAccessControl extends NavigationAccessControl {}

    @Singleton
    static class SecondAccessControl extends NavigationAccessControl {}
}
