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
package com.example.application;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.List;

import com.example.application.views.FlowAdminView;
import com.example.application.views.FlowAuthenticatedView;
import com.example.application.views.FlowPublicView;
import com.vaadin.flow.server.auth.AccessCheckDecision;
import com.vaadin.flow.server.auth.NavigationAccessControl;
import com.vaadin.flow.server.auth.NavigationContext;
import io.quarkus.arc.All;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.OidcSecurity;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import com.github.mcollovati.quarkus.hilla.security.HillaFormAuthenticationMechanism;
import com.github.mcollovati.quarkus.hilla.security.HillaSecurityPolicy;
import com.github.mcollovati.quarkus.hilla.security.QuarkusNavigationAccessControl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;

@QuarkusTest
class OidcSecurityTest {

    private static final String ENDPOINT = "OidcSecurityService";

    @Inject
    Instance<HillaSecurityPolicy> hillaSecurityPolicy;

    @Inject
    NavigationAccessControl navigationAccessControl;

    @Inject
    VaadinServiceHolder vaadinServiceHolder;

    @Inject
    @All
    List<HttpAuthenticationMechanism> authenticationMechanisms;

    @Test
    void oidcSecurityModel_registersSharedHillaSecurityWithoutFormMechanism() {
        assertThat(hillaSecurityPolicy.isResolvable()).isTrue();
        assertThat(navigationAccessControl).isInstanceOf(QuarkusNavigationAccessControl.class);
        assertThat(authenticationMechanisms).noneMatch(HillaFormAuthenticationMechanism.class::isInstance);
    }

    @Test
    void browserCallable_anonymousAllowed_withoutIdentity() {
        endpointRequest("anonymous").then().statusCode(200).body(equalTo("\"ANONYMOUS\""));
    }

    @Test
    void browserCallable_permitAll_withoutIdentityIsDenied() {
        endpointRequest("authenticated").then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "user", roles = "USER")
    @OidcSecurity
    void browserCallable_permitAll_usesQuarkusIdentity() {
        endpointRequest("authenticated").then().statusCode(200).body(equalTo("\"AUTHENTICATED:user\""));
    }

    @Test
    @TestSecurity(user = "user", roles = "USER")
    @OidcSecurity
    void browserCallable_rolesAllowed_matchingRoleIsAllowed() {
        endpointRequest("userOnly").then().statusCode(200).body(equalTo("\"USER\""));
    }

    @Test
    @TestSecurity(user = "user", roles = "USER")
    @OidcSecurity
    void browserCallable_rolesAllowed_wrongRoleIsDenied() {
        endpointRequest("adminOnly").then().statusCode(403);
    }

    @Test
    @TestSecurity(
            user = "admin",
            roles = {"USER", "ADMIN"})
    @OidcSecurity
    void browserCallable_rolesAllowed_adminRoleIsAllowed() {
        endpointRequest("adminOnly").then().statusCode(200).body(equalTo("\"ADMIN\""));
    }

    @Test
    void navigation_anonymousAllowed_withoutIdentity() {
        assertNavigationDecision(FlowPublicView.class, "flow-public", AccessCheckDecision.ALLOW);
    }

    @Test
    void navigation_permitAll_withoutIdentityIsDenied() {
        assertNavigationDecision(FlowAuthenticatedView.class, "flow-authenticated", AccessCheckDecision.DENY);
    }

    @Test
    @TestSecurity(user = "user", roles = "USER")
    @OidcSecurity
    void navigation_permitAll_usesQuarkusIdentity() {
        assertNavigationDecision(FlowAuthenticatedView.class, "flow-authenticated", AccessCheckDecision.ALLOW);
    }

    @Test
    @TestSecurity(user = "user", roles = "USER")
    @OidcSecurity
    void navigation_rolesAllowed_wrongRoleIsDenied() {
        assertNavigationDecision(FlowAdminView.class, "flow-admin", AccessCheckDecision.DENY);
    }

    @Test
    @TestSecurity(
            user = "admin",
            roles = {"USER", "ADMIN"})
    @OidcSecurity
    void navigation_rolesAllowed_adminRoleIsAllowed() {
        assertNavigationDecision(FlowAdminView.class, "flow-admin", AccessCheckDecision.ALLOW);
    }

    private Response endpointRequest(String methodName) {
        return RestAssured.given()
                .contentType(ContentType.JSON)
                .cookie("csrfToken", "CSRF_TOKEN")
                .header("X-CSRF-Token", "CSRF_TOKEN")
                .body("{}")
                .basePath("/connect")
                .when()
                .post("{endpointName}/{methodName}", ENDPOINT, methodName);
    }

    private void assertNavigationDecision(Class<?> route, String path, AccessCheckDecision expectedDecision) {
        NavigationContext context =
                navigationAccessControl.createNavigationContext(route, path, vaadinServiceHolder.get(), null);

        assertThat(navigationAccessControl.checkAccess(context, false).decision())
                .isEqualTo(expectedDecision);
    }
}
