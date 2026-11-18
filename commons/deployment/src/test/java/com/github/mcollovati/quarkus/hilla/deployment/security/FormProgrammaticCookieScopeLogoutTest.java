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
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import io.quarkus.test.QuarkusExtensionTest;
import io.quarkus.vertx.http.security.Form;
import io.quarkus.vertx.http.security.HttpSecurity;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A programmatic form mechanism with a custom cookie name, path and domain. The logout path is
 * outside the cookie path, so the logout request carries no cookie.
 */
class FormProgrammaticCookieScopeLogoutTest {

    private static final String COOKIE = "hilla-auth";

    @RegisterExtension
    static final QuarkusExtensionTest config = FormProgrammaticTestSupport.extension(true, Configuration.class)
            .overrideConfigKey("quarkus.http.auth.permission.app.paths", "/app/*")
            .overrideConfigKey("quarkus.http.auth.permission.app.policy", "authenticated");

    @BeforeAll
    static void setupUsers() {
        FormProgrammaticTestSupport.setupUsers();
    }

    @Test
    void logout_outsideCookiePath_expiresProgrammaticCookieScope() throws Exception {
        URI base = URI.create("http://localhost:" + RestAssured.port);
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<Void> login = client.send(
                HttpRequest.newBuilder(base.resolve("/j_security_check"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString("j_username=user&j_password=user"))
                        .build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(login.statusCode()).isEqualTo(302);
        assertThat(cookies.getCookieStore().getCookies())
                .extracting(HttpCookie::getName)
                .contains(COOKIE);
        assertThat(isRedirectToLogin(get(client, base.resolve("/app/secured"))))
                .as("authenticated before logout")
                .isFalse();

        HttpResponse<Void> logout = client.send(
                HttpRequest.newBuilder(base.resolve("/logout"))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.discarding());

        List<String> setCookies = logout.headers().allValues("Set-Cookie");
        HttpCookie expired = setCookies.stream()
                .filter(header -> header.startsWith(COOKIE + "="))
                .map(header -> HttpCookie.parse(header).get(0))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no Set-Cookie for " + COOKIE + ": " + setCookies));
        assertThat(expired.getValue()).isEmpty();
        assertThat(expired.getMaxAge()).isZero();
        assertThat(expired.getPath()).isEqualTo("/app");
        assertThat(expired.getDomain()).isEqualTo("localhost");
        assertThat(expired.getSecure()).as("Secure over plain HTTP").isFalse();
        assertThat(cookies.getCookieStore().getCookies())
                .extracting(HttpCookie::getName)
                .doesNotContain(COOKIE);
        assertThat(isRedirectToLogin(get(client, base.resolve("/app/secured"))))
                .as("authenticated after logout")
                .isTrue();
    }

    private static HttpResponse<Void> get(HttpClient client, URI uri) throws Exception {
        return client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.discarding());
    }

    private static boolean isRedirectToLogin(HttpResponse<Void> response) {
        return response.statusCode() == 302
                && response.headers()
                        .firstValue("Location")
                        .map(location -> location.endsWith("/custom-login"))
                        .orElse(false);
    }

    @ApplicationScoped
    static class Configuration {
        void configure(@Observes HttpSecurity http) {
            http.mechanism(Form.builder()
                    .loginPage("/custom-login")
                    .cookieName(COOKIE)
                    .cookiePath("/app")
                    .cookieDomain("localhost")
                    .build());
        }
    }
}
