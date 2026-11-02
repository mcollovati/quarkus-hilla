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

import com.vaadin.flow.internal.CurrentInstance;
import com.vaadin.flow.internal.menu.MenuRegistry;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.server.startup.ApplicationConfiguration;
import com.vaadin.quarkus.annotation.VaadinServiceEnabled;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.TrustedAuthenticationRequest;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.security.test.utils.TestIdentityController;
import io.quarkus.security.test.utils.TestIdentityProvider;
import io.quarkus.test.QuarkusExtensionTest;
import io.smallrye.mutiny.Uni;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.mcollovati.quarkus.hilla.deployment.TestUtils;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.endsWith;

class HillaRouteSecurityTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .withConfigurationResource(
                    TestUtils.class.getPackageName().replace('.', '/') + "/test-application.properties")
            .overrideConfigKey("quarkus.http.auth.form.enabled", "true")
            .overrideConfigKey("quarkus.http.auth.form.login-page", "/login")
            .overrideConfigKey("quarkus.http.test-port", "0")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            TestUtils.class,
                            TestIdentityController.class,
                            TestIdentityProvider.class,
                            TrustedIdentityProvider.class,
                            ClientOnlyApplication.class)
                    .addAsResource(
                            new StringAsset("{\"productionMode\":true}"), "META-INF/VAADIN/config/flow-build-info.json")
                    .addAsResource(
                            new StringAsset("<!DOCTYPE html><html><head></head><body>Hilla app shell</body></html>"),
                            "META-INF/VAADIN/webapp/index.html")
                    .addAsResource(new StringAsset("""
                            [{"route":"protected", "loginRequired":true},
                             {"route":"public"},
                             {"route":"public/*"},
                             {"route":"admin", "rolesAllowed":["ADMIN"]},
                             {"route":"products/:id", "loginRequired":true},
                             {"route":"optional/:id?", "loginRequired":true},
                             {"route":"café", "loginRequired":true},
                             {"route":"public-products/:id"},
                             {"route":"protected-wildcard/*", "loginRequired":true},
                             {"route":":lang?", "children":[
                               {"route":":id/foo", "loginRequired":true}, {"route":"foo"}]},
                             {"route":":id"},
                             {"route":":name", "loginRequired":true, "skipLayouts":true}]
                            """), "META-INF/VAADIN/file-routes.json"));

    @BeforeAll
    static void setupUsers() {
        TestIdentityController.resetRoles().add("user", "user", "USER");
    }

    @Test
    void anonymousGet_loginRequired_challenged() {
        given().redirects()
                .follow(false)
                .accept("text/html")
                .get("/protected")
                .then()
                .statusCode(302)
                .header("Location", endsWith("/login"));
    }

    @Test
    void anonymousHead_loginRequired_challenged() {
        given().redirects()
                .follow(false)
                .accept("text/html")
                .head("/protected")
                .then()
                .statusCode(302)
                .header("Location", endsWith("/login"));
    }

    @Test
    void anonymousGet_parameterizedSecuredRoute_challenged() {
        given().redirects()
                .follow(false)
                .accept("text/html")
                .get("/products/123")
                .then()
                .statusCode(302)
                .header("Location", endsWith("/login"));
    }

    @Test
    void anonymousGet_optionalParentExpansionTie_challenged() {
        given().redirects()
                .follow(false)
                .accept("text/html")
                .get("/en/foo")
                .then()
                .statusCode(302)
                .header("Location", endsWith("/login"));
    }

    @Test
    void anonymousHead_optionalParentExpansionTie_challenged() {
        given().redirects()
                .follow(false)
                .accept("text/html")
                .head("/en/foo")
                .then()
                .statusCode(302)
                .header("Location", endsWith("/login"));
    }

    @Test
    void anonymousGet_skipLayoutsTie_challenged() {
        given().redirects()
                .follow(false)
                .accept("text/html")
                .get("/42")
                .then()
                .statusCode(302)
                .header("Location", endsWith("/login"));
    }

    @Test
    void anonymousHead_skipLayoutsTie_challenged() {
        given().redirects()
                .follow(false)
                .accept("text/html")
                .head("/42")
                .then()
                .statusCode(302)
                .header("Location", endsWith("/login"));
    }

    @Test
    void anonymousGet_optionalSecuredRoute_challenged() {
        given().redirects()
                .follow(false)
                .accept("text/html")
                .get("/optional")
                .then()
                .statusCode(302)
                .header("Location", endsWith("/login"));
    }

    @Test
    void anonymousGet_mixedCaseSecuredRoute_challenged() {
        given().redirects()
                .follow(false)
                .accept("text/html")
                .get("/PROTECTED")
                .then()
                .statusCode(302)
                .header("Location", endsWith("/login"));
    }

    @Test
    void anonymousGet_encodedSecuredRoute_challenged() {
        given().urlEncodingEnabled(false)
                .redirects()
                .follow(false)
                .accept("text/html")
                .get("/caf%C3%A9")
                .then()
                .statusCode(302)
                .header("Location", endsWith("/login"));
    }

    @Test
    void anonymousGet_encodedDotSegmentsToSecuredRoute_challenged() {
        // Encoded dot segments preserve the traversal path through the HTTP client's URI handling.
        given().urlEncodingEnabled(false)
                .redirects()
                .follow(false)
                .accept("text/html")
                .get("/public/%2e%2e/protected")
                .then()
                .statusCode(302)
                .header("Location", endsWith("/login"));
    }

    @Test
    void anonymousGet_publicParameterizedRoute_permitted() {
        given().redirects()
                .follow(false)
                .accept("text/html")
                .get("/public-products/123")
                .then()
                .statusCode(200);
    }

    @Test
    void anonymousGet_rolesAllowed_challenged() {
        given().redirects()
                .follow(false)
                .accept("text/html")
                .get("/admin")
                .then()
                .statusCode(302)
                .header("Location", endsWith("/login"));
    }

    @Test
    void anonymousGet_publicRoute_permitted() {
        given().redirects()
                .follow(false)
                .accept("text/html")
                .get("/public")
                .then()
                .statusCode(200);
    }

    @Test
    void anonymousGet_unknownPath_permitted() {
        given().redirects()
                .follow(false)
                .accept("text/html")
                .get("/unknown/path")
                .then()
                .statusCode(200);
    }

    @Test
    void anonymousGet_nonHtmlProtectedRoute_permitted() {
        given().redirects()
                .follow(false)
                .accept("application/json")
                .get("/protected")
                .then()
                .statusCode(200);
    }

    @Test
    void anonymousGet_nonHtmlProtectedWildcard_permitted() {
        given().redirects()
                .follow(false)
                .accept("application/json")
                .get("/protected-wildcard/api")
                .then()
                .statusCode(200);
    }

    @Test
    void authenticatedGet_missingRole_permitted() {
        var cookies = given().redirects()
                .follow(false)
                .formParam("j_username", "user")
                .formParam("j_password", "user")
                .post("/j_security_check")
                .then()
                .statusCode(302)
                .extract()
                .cookies();
        given().redirects()
                .follow(false)
                .cookies(cookies)
                .accept("text/html")
                .get("/admin")
                .then()
                .statusCode(200);
    }

    @ApplicationScoped
    @VaadinServiceEnabled
    static class ClientOnlyApplication implements VaadinServiceInitListener {
        @Override
        public void serviceInit(ServiceInitEvent event) {
            var service = event.getSource();
            var configuration = ApplicationConfiguration.get(service.getContext());
            assertThat(configuration.isProductionMode()).isTrue();
            var oldInstances = CurrentInstance.getInstances();
            VaadinService.setCurrent(service);
            try {
                var routes = MenuRegistry.collectClientMenuItems(false, configuration, null);
                assertThat(routes).containsKeys("/protected", "/public", "/admin", "/protected-wildcard/*");
                assertThat(routes.get("/protected").loginRequired()).isTrue();
                assertThat(routes.get("/admin").rolesAllowed()).containsExactly("ADMIN");
            } finally {
                CurrentInstance.clearAll();
                CurrentInstance.restoreInstances(oldInstances);
            }
            // Serve the client app shell without initializing a Flow UI or building a frontend bundle.
            service.setBootstrapInitialPredicate(request -> false);
            service.setBootstrapUrlPredicate(request -> true);
        }
    }

    @ApplicationScoped
    static class TrustedIdentityProvider implements IdentityProvider<TrustedAuthenticationRequest> {
        @Override
        public Class<TrustedAuthenticationRequest> getRequestType() {
            return TrustedAuthenticationRequest.class;
        }

        @Override
        public Uni<SecurityIdentity> authenticate(
                TrustedAuthenticationRequest request, AuthenticationRequestContext context) {
            var user = TestIdentityController.identities.get(request.getPrincipal());
            return Uni.createFrom()
                    .item(
                            user == null
                                    ? null
                                    : QuarkusSecurityIdentity.builder()
                                            .setPrincipal(new QuarkusPrincipal(user.username))
                                            .addRoles(user.roles)
                                            .build());
        }
    }
}
