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
 * Form login enabled by {@code quarkus.http.auth.form.enabled=true}, with a cookie name set only programmatically.
 */
class FormProgrammaticCookieNameLogoutTest {

    private static final String COOKIE = "hilla-credential";

    @RegisterExtension
    static final QuarkusExtensionTest config = FormProgrammaticTestSupport.extension(true, Configuration.class);

    @BeforeAll
    static void setupUsers() {
        FormProgrammaticTestSupport.setupUsers();
    }

    @Test
    void logout_programmaticCookieName_expiresAuthenticationCookie() {
        String cookie = FormProgrammaticTestSupport.login(COOKIE);
        Response response = FormProgrammaticTestSupport.logout(COOKIE, cookie);
        assertThat(FormProgrammaticTestSupport.expiresCookie(response, COOKIE))
                .as(
                        "logout expires %s, status %s, Set-Cookie: %s",
                        COOKIE, response.statusCode(), FormProgrammaticTestSupport.setCookies(response))
                .isTrue();
    }

    @ApplicationScoped
    static class Configuration {
        void configure(@Observes HttpSecurity http) {
            http.mechanism(
                    Form.builder().loginPage("/custom-login").cookieName(COOKIE).build());
        }
    }
}
