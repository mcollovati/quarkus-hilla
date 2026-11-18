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
import java.net.HttpCookie;
import java.util.List;

import io.quarkus.test.QuarkusExtensionTest;
import io.quarkus.vertx.http.security.Form;
import io.quarkus.vertx.http.security.HttpSecurity;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/** Property and programmatic form mechanisms share a cookie name but use different paths. */
class FormProgrammaticSharedCookieNameLogoutTest {

    private static final String COOKIE = "quarkus-credential";

    @RegisterExtension
    static final QuarkusExtensionTest config = FormProgrammaticTestSupport.extension(true, Configuration.class)
            .overrideConfigKey("quarkus.http.auth.form.cookie-name", COOKIE)
            .overrideConfigKey("quarkus.http.auth.form.cookie-path", "/");

    @BeforeAll
    static void setupUsers() {
        FormProgrammaticTestSupport.setupUsers();
    }

    @Test
    void logout_sharedCookieName_expiresPropertyAndProgrammaticPaths() {
        String credential = FormProgrammaticTestSupport.login(COOKIE);

        Response response = FormProgrammaticTestSupport.logout(COOKIE, credential);

        List<HttpCookie> expired = FormProgrammaticTestSupport.setCookies(response).stream()
                .filter(header -> header.startsWith(COOKIE + "="))
                .map(header -> HttpCookie.parse(header).get(0))
                .toList();
        assertThat(expired)
                .as("logout Set-Cookie headers: %s", FormProgrammaticTestSupport.setCookies(response))
                .hasSize(2)
                .allSatisfy(cookie -> {
                    assertThat(cookie.getValue()).isEmpty();
                    assertThat(cookie.getMaxAge()).isZero();
                    assertThat(cookie.getDomain()).isNull();
                });
        assertThat(expired).extracting(HttpCookie::getPath).containsExactlyInAnyOrder("/", "/app");
    }

    @ApplicationScoped
    static class Configuration {
        void configure(@Observes HttpSecurity http) {
            http.mechanism(Form.builder()
                    .loginPage("/custom-login")
                    .cookieName(COOKIE)
                    .cookiePath("/app")
                    .build());
        }
    }
}
