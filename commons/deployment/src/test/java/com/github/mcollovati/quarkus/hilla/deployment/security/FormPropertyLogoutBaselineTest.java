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

import com.vaadin.flow.server.auth.NavigationAccessControl;
import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Baseline: form login configured only with properties, including a custom cookie name.
 */
class FormPropertyLogoutBaselineTest {

    private static final String COOKIE = "hilla-credential";

    @RegisterExtension
    static final QuarkusExtensionTest config = FormProgrammaticTestSupport.extension(true)
            .overrideConfigKey("quarkus.http.auth.form.login-page", "/custom-login")
            .overrideConfigKey("quarkus.http.auth.form.cookie-name", COOKIE);

    @BeforeAll
    static void setupUsers() {
        FormProgrammaticTestSupport.setupUsers();
    }

    @Test
    void logout_properties_expiresAuthenticationCookie() throws ReflectiveOperationException {
        System.out.println("HttpAuthenticator mechanisms: "
                + FormProgrammaticTestSupport.describe(FormProgrammaticTestSupport.authenticatorMechanisms()));
        String cookie = FormProgrammaticTestSupport.login(COOKIE);
        Response response = FormProgrammaticTestSupport.logout(COOKIE, cookie);
        assertThat(FormProgrammaticTestSupport.expiresCookie(response, COOKIE))
                .as(
                        "logout expires %s, status %s, Set-Cookie: %s",
                        COOKIE, response.statusCode(), FormProgrammaticTestSupport.setCookies(response))
                .isTrue();
    }

    @Test
    void typescriptLogin_wrongPassword_returns401() {
        Response response = FormProgrammaticTestSupport.typescriptLogin("wrong");
        assertThat(response.statusCode()).as("headers: %s", response.headers()).isEqualTo(401);
    }

    @Test
    void properties_loginPageAppliedEverywhere() throws ReflectiveOperationException {
        assertThat(NavigationAccessControlTestSupport.field(
                        NavigationAccessControlTestSupport.accessControl(), NavigationAccessControl.class, "loginUrl"))
                .isEqualTo("/custom-login");
        assertThat(FormProgrammaticTestSupport.policyPermits("/custom-login")).isTrue();
    }
}
