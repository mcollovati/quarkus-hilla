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
import java.util.Arrays;
import java.util.List;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.TrustedAuthenticationRequest;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.security.test.utils.TestIdentityController;
import io.quarkus.security.test.utils.TestIdentityProvider;
import io.quarkus.test.QuarkusExtensionTest;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticator;
import io.quarkus.vertx.http.runtime.security.ImmutablePathMatcher;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.smallrye.mutiny.Uni;
import org.jboss.shrinkwrap.api.spec.JavaArchive;

import com.github.mcollovati.quarkus.hilla.deployment.TestUtils;
import com.github.mcollovati.quarkus.hilla.security.HillaFormAuthenticationMechanism;
import com.github.mcollovati.quarkus.hilla.security.HillaSecurityPolicy;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Helpers for tests that configure form login through the programmatic {@code HttpSecurity} API.
 */
final class FormProgrammaticTestSupport {

    static final String USERNAME = "user";
    static final String PASSWORD = "user";

    private FormProgrammaticTestSupport() {}

    static QuarkusExtensionTest extension(boolean formEnabledProperty, Class<?>... classes) {
        QuarkusExtensionTest test = new QuarkusExtensionTest()
                .withConfigurationResource(
                        TestUtils.class.getPackageName().replace('.', '/') + "/test-application.properties")
                .overrideConfigKey("quarkus.http.test-port", "0")
                .setArchiveProducer(() -> archive(classes));
        if (formEnabledProperty) {
            test.overrideConfigKey("quarkus.http.auth.form.enabled", "true");
        }
        return test;
    }

    static JavaArchive archive(Class<?>... classes) {
        return NavigationAccessControlTestSupport.archive(classes)
                .addClasses(
                        FormProgrammaticTestSupport.class,
                        TrustedIdentityProvider.class,
                        TestIdentityProvider.class,
                        TestIdentityController.class);
    }

    static void setupUsers() {
        TestIdentityController.resetRoles().add(USERNAME, PASSWORD, "USER");
    }

    /** Logs in through the form post location and returns the authentication cookie value. */
    static String login(String cookieName) {
        Response response = RestAssured.given()
                .redirects()
                .follow(false)
                .formParam("j_username", USERNAME)
                .formParam("j_password", PASSWORD)
                .post("/j_security_check");
        assertThat(response.statusCode())
                .as("form login status, headers: %s", response.headers())
                .isEqualTo(302);
        String value = response.getCookie(cookieName);
        assertThat(value)
                .as("authentication cookie %s, headers: %s", cookieName, response.headers())
                .isNotBlank();
        return value;
    }

    /** Logs in like the Hilla TypeScript client does, with the {@code source: typescript} header. */
    static Response typescriptLogin(String password) {
        return RestAssured.given()
                .redirects()
                .follow(false)
                .header("source", "typescript")
                .formParam("j_username", USERNAME)
                .formParam("j_password", password)
                .post("/j_security_check");
    }

    static Response logout(String cookieName, String cookieValue) {
        return RestAssured.given()
                .redirects()
                .follow(false)
                .cookie(cookieName, cookieValue)
                .post("/logout");
    }

    /** Whether the response expires the given cookie (empty value, Max-Age=0). */
    static boolean expiresCookie(Response response, String cookieName) {
        return response.headers().getValues("Set-Cookie").stream()
                .anyMatch(header -> header.startsWith(cookieName + "=;") && header.contains("Max-Age=0"));
    }

    static List<String> setCookies(Response response) {
        return response.headers().getValues("Set-Cookie");
    }

    /** The mechanisms the Quarkus HTTP authenticator actually uses, in invocation order. */
    static List<HttpAuthenticationMechanism> authenticatorMechanisms() throws ReflectiveOperationException {
        Object authenticator = ClientProxy.unwrap(
                Arc.container().instance(HttpAuthenticator.class).get());
        HttpAuthenticationMechanism[] mechanisms = (HttpAuthenticationMechanism[])
                NavigationAccessControlTestSupport.field(authenticator, HttpAuthenticator.class, "mechanisms");
        return Arrays.stream(mechanisms).map(ClientProxy::unwrap).toList();
    }

    static List<String> describe(List<HttpAuthenticationMechanism> mechanisms) {
        return mechanisms.stream()
                .map(m -> m.getClass().getSimpleName() + "@" + System.identityHashCode(m) + "(prio " + m.getPriority()
                        + ")")
                .toList();
    }

    static HillaFormAuthenticationMechanism hillaMechanism() {
        return Arc.container()
                .select(HttpAuthenticationMechanism.class)
                .handlesStream()
                .map(InstanceHandle::get)
                .map(ClientProxy::unwrap)
                .filter(HillaFormAuthenticationMechanism.class::isInstance)
                .map(HillaFormAuthenticationMechanism.class::cast)
                .findFirst()
                .orElse(null);
    }

    static Object hillaDelegate(HillaFormAuthenticationMechanism mechanism) throws ReflectiveOperationException {
        return ClientProxy.unwrap(NavigationAccessControlTestSupport.field(
                mechanism, HillaFormAuthenticationMechanism.class, "delegate"));
    }

    static HillaFormAuthenticationMechanism.Config hillaConfig(HillaFormAuthenticationMechanism mechanism)
            throws ReflectiveOperationException {
        return (HillaFormAuthenticationMechanism.Config)
                NavigationAccessControlTestSupport.field(mechanism, HillaFormAuthenticationMechanism.class, "config");
    }

    /** Whether the Hilla security policy's permit-all matcher matches the path. */
    @SuppressWarnings("unchecked")
    static boolean policyPermits(String path) throws ReflectiveOperationException {
        Object policy = ClientProxy.unwrap(
                Arc.container().instance(HillaSecurityPolicy.class).get());
        ImmutablePathMatcher<Boolean> matcher = (ImmutablePathMatcher<Boolean>)
                NavigationAccessControlTestSupport.field(policy, HillaSecurityPolicy.class, "permitAllMatcher");
        Boolean value = matcher.match(path).getValue();
        return value != null && value;
    }

    /** Restores identities from the form authentication cookie. */
    @Singleton
    static class TrustedIdentityProvider implements IdentityProvider<TrustedAuthenticationRequest> {

        @Override
        public Class<TrustedAuthenticationRequest> getRequestType() {
            return TrustedAuthenticationRequest.class;
        }

        @Override
        public Uni<SecurityIdentity> authenticate(
                TrustedAuthenticationRequest request, AuthenticationRequestContext context) {
            return Uni.createFrom()
                    .item(QuarkusSecurityIdentity.builder()
                            .setPrincipal(new QuarkusPrincipal(request.getPrincipal()))
                            .addRole("USER")
                            .build());
        }
    }
}
