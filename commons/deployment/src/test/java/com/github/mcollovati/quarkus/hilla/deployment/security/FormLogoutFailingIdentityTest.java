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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.TrustedAuthenticationRequest;
import io.quarkus.security.test.utils.TestIdentityController;
import io.quarkus.security.test.utils.TestIdentityProvider;
import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.mcollovati.quarkus.hilla.deployment.TestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Logout with proactive authentication when the identity cannot be restored from the cookie, for
 * example because the account was deleted.
 */
class FormLogoutFailingIdentityTest {

    private static final String COOKIE = "quarkus-credential";

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .withConfigurationResource(
                    TestUtils.class.getPackageName().replace('.', '/') + "/test-application.properties")
            .overrideConfigKey("quarkus.http.test-port", "0")
            .overrideConfigKey("quarkus.http.auth.form.enabled", "true")
            .overrideConfigKey("quarkus.http.auth.proactive", "true")
            .overrideConfigKey("vaadin.security.post-logout-redirect-uri", "/goodbye")
            .overrideConfigKey("quarkus.http.auth.permission.secured.paths", "/secured")
            .overrideConfigKey("quarkus.http.auth.permission.secured.policy", "authenticated")
            .setArchiveProducer(() -> NavigationAccessControlTestSupport.archive()
                    // Without FormProgrammaticTestSupport, whose nested provider restores every identity.
                    .addClasses(
                            FailingTrustedIdentityProvider.class,
                            TestIdentityProvider.class,
                            TestIdentityController.class));

    @BeforeAll
    static void setupUsers() {
        TestIdentityController.resetRoles().add("user", "user", "USER");
    }

    @Test
    void protectedRequest_identityNotRestored() {
        String cookie = login();
        Response response = RestAssured.given()
                .redirects()
                .follow(false)
                .cookie(COOKIE, cookie)
                .get("/secured");
        assertThat(FailingTrustedIdentityProvider.INVOCATIONS.get())
                .as("provider invocations")
                .isPositive();
        assertThat(response.statusCode())
                .as("headers: %s, body: %s", response.headers(), response.body().asString())
                .isNotEqualTo(200)
                .isNotEqualTo(404);
    }

    @Test
    void logout_identityNotRestored_expiresCookieAndRedirects() {
        String cookie = login();
        Response response = RestAssured.given()
                .redirects()
                .follow(false)
                .cookie(COOKIE, cookie)
                .post("/logout");
        List<String> setCookies = response.headers().getValues("Set-Cookie");
        assertThat(setCookies)
                .as("status %s", response.statusCode())
                .anyMatch(header -> header.startsWith(COOKIE + "=;") && header.contains("Max-Age=0"));
        assertThat(response.statusCode()).as("Set-Cookie: %s", setCookies).isEqualTo(302);
        assertThat(response.header("Location")).endsWith("/goodbye");
    }

    private static String login() {
        Response response = RestAssured.given()
                .redirects()
                .follow(false)
                .formParam("j_username", "user")
                .formParam("j_password", "user")
                .post("/j_security_check");
        assertThat(response.statusCode()).isEqualTo(302);
        return response.getCookie(COOKIE);
    }

    /** Fails to restore any identity from the form authentication cookie. */
    @Singleton
    static class FailingTrustedIdentityProvider implements IdentityProvider<TrustedAuthenticationRequest> {

        static final AtomicInteger INVOCATIONS = new AtomicInteger();

        @Override
        public Class<TrustedAuthenticationRequest> getRequestType() {
            return TrustedAuthenticationRequest.class;
        }

        @Override
        public Uni<SecurityIdentity> authenticate(
                TrustedAuthenticationRequest request, AuthenticationRequestContext context) {
            INVOCATIONS.incrementAndGet();
            return Uni.createFrom().failure(new AuthenticationFailedException("Account no longer exists"));
        }
    }
}
