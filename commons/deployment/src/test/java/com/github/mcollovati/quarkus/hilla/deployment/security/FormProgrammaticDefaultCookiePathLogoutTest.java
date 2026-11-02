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
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/** A form cookie without Path defaults to the directory of the login POST request. */
class FormProgrammaticDefaultCookiePathLogoutTest {

    private static final String COOKIE = "quarkus-credential";

    @RegisterExtension
    // Quarkus rejects quarkus.http.auth.form.* properties next to a mechanism set up in code.
    static final QuarkusExtensionTest config = FormProgrammaticTestSupport.extension(true, Configuration.class);

    @BeforeAll
    static void setupUsers() {
        FormProgrammaticTestSupport.setupUsers();
    }

    @Test
    void logout_implicitCookiePath_expiresPostDirectoryAndRoot() {
        Response login = RestAssured.given()
                .redirects()
                .follow(false)
                .formParam("j_username", FormProgrammaticTestSupport.USERNAME)
                .formParam("j_password", FormProgrammaticTestSupport.PASSWORD)
                .post("/app/j_security_check");
        assertThat(login.statusCode()).isEqualTo(302);
        String credential = login.getCookie(COOKIE);
        assertThat(credential).isNotBlank();
        assertThat(HttpCookie.parse(FormProgrammaticTestSupport.setCookies(login).stream()
                                .filter(header -> header.startsWith(COOKIE + "="))
                                .findFirst()
                                .orElseThrow())
                        .get(0)
                        .getPath())
                .isNull();

        Response response = FormProgrammaticTestSupport.logout(COOKIE, credential);

        List<HttpCookie> expired = FormProgrammaticTestSupport.setCookies(response).stream()
                .filter(header -> header.startsWith(COOKIE + "="))
                .map(header -> HttpCookie.parse(header).get(0))
                .toList();
        assertThat(expired)
                .as("logout Set-Cookie headers: %s", FormProgrammaticTestSupport.setCookies(response))
                .isNotEmpty()
                .allSatisfy(cookie -> {
                    assertThat(cookie.getValue()).isEmpty();
                    assertThat(cookie.getMaxAge()).isZero();
                    assertThat(cookie.getDomain()).isNull();
                });
        assertThat(expired)
                .extracting(HttpCookie::getPath)
                .containsOnly("/", "/app")
                .contains("/", "/app");
    }

    @ApplicationScoped
    static class Configuration {
        void configure(@Observes HttpSecurity http) {
            http.mechanism(Form.builder()
                    .loginPage("/custom-login")
                    .postLocation("/app/j_security_check")
                    .cookieName(COOKIE)
                    .cookiePath(null)
                    .build());
        }
    }
}
