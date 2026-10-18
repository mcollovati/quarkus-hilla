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
import jakarta.enterprise.event.Observes;

import io.quarkus.test.QuarkusExtensionTest;
import io.quarkus.vertx.http.security.Form;
import io.quarkus.vertx.http.security.HttpSecurity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A runtime form property together with a programmatic Form mechanism: Quarkus refuses to start.
 */
class FormProgrammaticPropertyConflictTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = FormProgrammaticTestSupport.extension(true, Configuration.class)
            .overrideConfigKey("quarkus.http.auth.form.login-page", "/property-login")
            .assertException(throwable -> assertThat(throwable)
                    .hasStackTraceContaining("Cannot configure form-based authentication programmatically"));

    @Test
    void propertyAndProgrammaticForm_startupFails() {
        // Startup failure is checked by the extension.
    }

    @ApplicationScoped
    static class Configuration {
        void configure(@Observes HttpSecurity http) {
            http.mechanism(Form.builder().loginPage("/custom-login").build());
        }
    }
}
