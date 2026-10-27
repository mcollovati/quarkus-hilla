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
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TypeScript client login with a programmatic Form mechanism. The programmatic instance is both the Hilla wrapper's
 * delegate and a separate entry in the Quarkus authenticator chain.
 */
class FormProgrammaticTypescriptLoginTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = FormProgrammaticTestSupport.extension(true, Configuration.class);

    @BeforeAll
    static void setupUsers() {
        FormProgrammaticTestSupport.setupUsers();
    }

    @Test
    void typescriptLogin_success_returnsHillaResult() {
        Response response = FormProgrammaticTestSupport.typescriptLogin(FormProgrammaticTestSupport.PASSWORD);
        assertThat(response.statusCode()).as("headers: %s", response.headers()).isEqualTo(200);
        assertThat(response.header("Result")).isEqualTo("success");
        assertThat(response.header("Default-url")).isEqualTo("/custom-home");
    }

    @Test
    void typescriptLogin_wrongPassword_returns401() {
        Response response = FormProgrammaticTestSupport.typescriptLogin("wrong");
        assertThat(response.statusCode()).as("headers: %s", response.headers()).isEqualTo(401);
    }

    @ApplicationScoped
    static class Configuration {
        void configure(@Observes HttpSecurity http) {
            http.mechanism(Form.builder()
                    .loginPage("/custom-login")
                    .landingPage("/custom-home")
                    .build());
        }
    }
}
