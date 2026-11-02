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
import io.quarkus.test.QuarkusExtensionTest;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.security.Form;
import io.quarkus.vertx.http.security.HttpSecurity;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.mcollovati.quarkus.hilla.security.HillaFormAuthenticationMechanism;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Form login enabled by {@code quarkus.http.auth.form.enabled=true}, but login page and landing page configured only
 * through the programmatic {@code HttpSecurity} API.
 */
class FormProgrammaticLoginPageTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = FormProgrammaticTestSupport.extension(true, Configuration.class)
            .overrideConfigKey("quarkus.http.auth.permission.secured.paths", "/secured")
            .overrideConfigKey("quarkus.http.auth.permission.secured.policy", "authenticated");

    @BeforeAll
    static void setupUsers() {
        FormProgrammaticTestSupport.setupUsers();
    }

    @Test
    void quarkus_challengeRedirectsToProgrammaticLoginPage() {
        Response response = RestAssured.given().redirects().follow(false).get("/secured");
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.header("Location")).endsWith("/custom-login");
    }

    @Test
    void navigationAccessControl_usesProgrammaticLoginPage() throws ReflectiveOperationException {
        NavigationAccessControl accessControl = NavigationAccessControlTestSupport.accessControl();
        assertThat(NavigationAccessControlTestSupport.field(accessControl, NavigationAccessControl.class, "loginUrl"))
                .as("login view applied to NavigationAccessControl")
                .isEqualTo("/custom-login");
    }

    @Test
    void securityPolicy_permitsProgrammaticLoginPage() throws ReflectiveOperationException {
        System.out.println("HillaSecurityPolicy permits /login.html (property default): "
                + FormProgrammaticTestSupport.policyPermits("/login.html"));
        assertThat(FormProgrammaticTestSupport.policyPermits("/custom-login"))
                .as("HillaSecurityPolicy permits the programmatic /custom-login")
                .isTrue();
    }

    @Test
    void hillaMechanism_wrapsProgrammaticMechanism() throws ReflectiveOperationException {
        HillaFormAuthenticationMechanism hilla = FormProgrammaticTestSupport.hillaMechanism();
        assertThat(hilla).isNotNull();
        assertThat(FormProgrammaticTestSupport.hillaDelegate(hilla))
                .as("Hilla wrapper delegate is the programmatic Form instance")
                .isSameAs(Configuration.mechanism);
        var mechanisms = FormProgrammaticTestSupport.authenticatorMechanisms();
        System.out.println("HttpAuthenticator mechanisms: " + FormProgrammaticTestSupport.describe(mechanisms)
                + ", programmatic Form: " + System.identityHashCode(Configuration.mechanism));
        assertThat(mechanisms).contains(hilla);
    }

    @Test
    void hillaMechanism_usesProgrammaticLandingPage() throws ReflectiveOperationException {
        HillaFormAuthenticationMechanism hilla = FormProgrammaticTestSupport.hillaMechanism();
        assertThat(FormProgrammaticTestSupport.hillaConfig(hilla).landingPage())
                .as("Default-url header sent to TypeScript login clients")
                .isEqualTo("/custom-home");
    }

    @ApplicationScoped
    static class Configuration {
        static volatile HttpAuthenticationMechanism mechanism;

        void configure(@Observes HttpSecurity http) {
            mechanism = Form.builder()
                    .loginPage("/custom-login")
                    .landingPage("/custom-home")
                    .build();
            http.mechanism(mechanism);
        }
    }
}
