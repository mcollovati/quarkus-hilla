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

import com.vaadin.flow.server.auth.NavigationAccessControl;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusExtensionTest;
import io.quarkus.vertx.http.runtime.security.FormAuthenticationMechanism;
import io.quarkus.vertx.http.security.Form;
import io.quarkus.vertx.http.security.HttpSecurity;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.mcollovati.quarkus.hilla.security.HillaSecurityPolicy;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Form login configured only programmatically, without {@code quarkus.http.auth.form.enabled}.
 */
class FormProgrammaticOnlyTest {

    private static final String COOKIE = "quarkus-credential";

    @RegisterExtension
    static final QuarkusExtensionTest config = FormProgrammaticTestSupport.extension(false, Configuration.class);

    @BeforeAll
    static void setupUsers() {
        FormProgrammaticTestSupport.setupUsers();
    }

    @Test
    void quarkus_formLoginWorks() throws ReflectiveOperationException {
        System.out.println("HttpAuthenticator mechanisms: "
                + FormProgrammaticTestSupport.describe(FormProgrammaticTestSupport.authenticatorMechanisms()));
        System.out.println("FormAuthenticationMechanism CDI bean available: "
                + Arc.container().instance(FormAuthenticationMechanism.class).isAvailable());
        assertThat(FormProgrammaticTestSupport.login(COOKIE)).isNotBlank();
    }

    @Test
    void navigationAccessControl_isActive() {
        assertThat(Arc.container().instance(NavigationAccessControl.class).isAvailable())
                .as("NavigationAccessControl bean available")
                .isTrue();
    }

    @Test
    void securityPolicy_isActive() {
        assertThat(Arc.container().instance(HillaSecurityPolicy.class).isAvailable())
                .as("HillaSecurityPolicy bean available")
                .isTrue();
    }

    @Test
    void hillaMechanism_isActive() {
        assertThat(FormProgrammaticTestSupport.hillaMechanism())
                .as("HillaFormAuthenticationMechanism registered")
                .isNotNull();
    }

    @Test
    void logout_expiresAuthenticationCookie() {
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
            http.mechanism(Form.builder().loginPage("/custom-login").build());
        }
    }
}
