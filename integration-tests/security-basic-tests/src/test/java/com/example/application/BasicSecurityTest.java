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

import com.vaadin.flow.server.auth.NavigationAccessControl;
import io.quarkus.arc.All;
import io.quarkus.test.junit.QuarkusTest;
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
class BasicSecurityTest {

    private static final String ENDPOINT = "BasicSecurityService";

    @Inject
    Instance<HillaSecurityPolicy> hillaSecurityPolicy;

    @Inject
    NavigationAccessControl navigationAccessControl;

    @Inject
    @All
    List<HttpAuthenticationMechanism> authenticationMechanisms;

    @Test
    void basicSecurityModel_registersSharedHillaSecurityWithoutFormMechanism() {
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
    void browserCallable_permitAll_usesBasicIdentity() {
        endpointRequest("authenticated", "user", "user-password")
                .then()
                .statusCode(200)
                .body(equalTo("\"AUTHENTICATED:user\""));
    }

    @Test
    void browserCallable_invalidBasicCredentialsAreDenied() {
        endpointRequest("authenticated", "user", "wrong-password").then().statusCode(401);
    }

    @Test
    void browserCallable_rolesAllowed_matchingRoleIsAllowed() {
        endpointRequest("userOnly", "user", "user-password")
                .then()
                .statusCode(200)
                .body(equalTo("\"USER\""));
    }

    @Test
    void browserCallable_rolesAllowed_wrongRoleIsDenied() {
        endpointRequest("adminOnly", "user", "user-password").then().statusCode(403);
    }

    @Test
    void browserCallable_rolesAllowed_adminRoleIsAllowed() {
        endpointRequest("adminOnly", "admin", "admin-password")
                .then()
                .statusCode(200)
                .body(equalTo("\"ADMIN\""));
    }

    @Test
    void navigation_rolesAllowed_wrongRoleIsDenied() {
        endpointRequest("adminNavigationDecision", "user", "user-password")
                .then()
                .statusCode(200)
                .body(equalTo("\"DENY\""));
    }

    @Test
    void navigation_rolesAllowed_adminRoleIsAllowed() {
        endpointRequest("adminNavigationDecision", "admin", "admin-password")
                .then()
                .statusCode(200)
                .body(equalTo("\"ALLOW\""));
    }

    private Response endpointRequest(String methodName) {
        return request().when().post("{endpointName}/{methodName}", ENDPOINT, methodName);
    }

    private Response endpointRequest(String methodName, String username, String password) {
        return request()
                .auth()
                .preemptive()
                .basic(username, password)
                .when()
                .post("{endpointName}/{methodName}", ENDPOINT, methodName);
    }

    private io.restassured.specification.RequestSpecification request() {
        return RestAssured.given()
                .contentType(ContentType.JSON)
                .cookie("csrfToken", "CSRF_TOKEN")
                .header("X-CSRF-Token", "CSRF_TOKEN")
                .body("{}")
                .basePath("/connect");
    }
}
