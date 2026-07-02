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
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import com.example.application.views.FlowLowercaseMethodView;
import com.example.application.views.FlowNamedPolicyView;
import com.example.application.views.FlowPermissionOnlyView;
import com.example.application.views.FlowProgrammaticView;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.server.auth.AccessCheckDecision;
import com.vaadin.flow.server.auth.NavigationAccessControl;
import com.vaadin.flow.server.auth.NavigationContext;
import io.quarkus.arc.All;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import com.github.mcollovati.quarkus.hilla.security.HillaFormAuthenticationMechanism;
import com.github.mcollovati.quarkus.hilla.security.HillaSecurityPolicy;
import com.github.mcollovati.quarkus.hilla.security.QuarkusHttpPermissionNavigationAccessChecker;
import com.github.mcollovati.quarkus.hilla.security.QuarkusNavigationAccessControl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;

@QuarkusTest
class OidcSecurityTest {

    private static final String SECURE_ENDPOINT = "SecureEndpoint";

    private final Map<String, String> tokens = new ConcurrentHashMap<>();

    @ConfigProperty(name = "quarkus.oidc.auth-server-url")
    String authServerUrl;

    @ConfigProperty(name = "quarkus.oidc.client-id")
    String clientId;

    @ConfigProperty(name = "quarkus.oidc.credentials.secret")
    String clientSecret;

    @Inject
    Instance<HillaSecurityPolicy> hillaSecurityPolicy;

    @Inject
    @All
    List<HttpAuthenticationMechanism> authenticationMechanisms;

    @Inject
    NavigationAccessControl navigationAccessControl;

    @Inject
    Instance<QuarkusHttpPermissionNavigationAccessChecker> httpPermissionAccessChecker;

    @Test
    void oidcSecurityModel_registersGenericHillaSecurityBeansOnly() {
        assertThat(hillaSecurityPolicy.isResolvable()).isTrue();
        assertThat(navigationAccessControl).isInstanceOf(QuarkusNavigationAccessControl.class);
        assertThat(httpPermissionAccessChecker.isResolvable()).isTrue();
        assertThat(authenticationMechanisms).noneMatch(HillaFormAuthenticationMechanism.class::isInstance);
    }

    @Test
    void anonymousEndpoint_withoutToken_allowed() {
        endpointRequest("anonymous").then().assertThat().statusCode(200).body(equalTo("\"ANONYMOUS\""));
    }

    @Test
    void authenticatedEndpoint_requiresOidcToken() {
        endpointRequest("authenticated")
                .then()
                .assertThat()
                .statusCode(401)
                .body("message", containsString(SECURE_ENDPOINT))
                .body("message", containsString("reason: 'Access denied"));

        endpointRequest("authenticated", bearer("user"))
                .then()
                .assertThat()
                .statusCode(200)
                .body(equalTo("\"AUTHENTICATED:user\""));
    }

    @Test
    void roleProtectedEndpoint_enforcesOidcRoles() {
        endpointRequest("userOnly", bearer("user"))
                .then()
                .assertThat()
                .statusCode(200)
                .body(equalTo("\"USER\""));
        endpointRequest("userOnly", bearer("admin"))
                .then()
                .assertThat()
                .statusCode(200)
                .body(equalTo("\"USER\""));
        endpointRequest("userOnly", bearer("guest")).then().assertThat().statusCode(403);

        endpointRequest("adminOnly", bearer("admin"))
                .then()
                .assertThat()
                .statusCode(200)
                .body(equalTo("\"ADMIN\""));
        endpointRequest("adminOnly", bearer("user")).then().assertThat().statusCode(403);

        endpointRequest("denied", bearer("admin")).then().assertThat().statusCode(403);
        endpointRequest("denyByDefault", bearer("admin")).then().assertThat().statusCode(403);
    }

    @Test
    void flowRoutes_honorOidcHttpPermissionPolicies() {
        RestAssured.given().when().get("/flow-public").then().assertThat().statusCode(200);

        RestAssured.given().when().get("/flow-protected").then().assertThat().statusCode(401);
        RestAssured.given()
                .auth()
                .oauth2(token("user"))
                .when()
                .get("/flow-protected")
                .then()
                .assertThat()
                .statusCode(200);

        RestAssured.given()
                .auth()
                .oauth2(token("user"))
                .when()
                .get("/flow-user")
                .then()
                .assertThat()
                .statusCode(200);
        RestAssured.given()
                .auth()
                .oauth2(token("guest"))
                .when()
                .get("/flow-user")
                .then()
                .assertThat()
                .statusCode(403);

        RestAssured.given()
                .auth()
                .oauth2(token("admin"))
                .when()
                .get("/flow-admin")
                .then()
                .assertThat()
                .statusCode(200);
        RestAssured.given()
                .auth()
                .oauth2(token("user"))
                .when()
                .get("/flow-admin")
                .then()
                .assertThat()
                .statusCode(403);

        RestAssured.given().when().get("/flow-programmatic").then().assertThat().statusCode(401);
        RestAssured.given()
                .auth()
                .oauth2(token("user"))
                .when()
                .get("/flow-programmatic")
                .then()
                .assertThat()
                .statusCode(200);
        RestAssured.given()
                .auth()
                .oauth2(token("guest"))
                .when()
                .get("/flow-programmatic")
                .then()
                .assertThat()
                .statusCode(403);

        RestAssured.given()
                .auth()
                .oauth2(token("user"))
                .when()
                .get("/flow-named-policy")
                .then()
                .assertThat()
                .statusCode(200);
        RestAssured.given()
                .auth()
                .oauth2(token("guest"))
                .when()
                .get("/flow-named-policy")
                .then()
                .assertThat()
                .statusCode(403);

        RestAssured.given()
                .auth()
                .oauth2(token("user"))
                .when()
                .get("/flow-lowercase-method")
                .then()
                .assertThat()
                .statusCode(403);
    }

    @Test
    void permissionOnlyFlowRoute_navigationCheckerHonorsHttpPermissionPolicy() {
        assertNavigationDecision(FlowPermissionOnlyView.class, "flow-permission-only", null, role -> false,
                AccessCheckDecision.DENY);
        assertNavigationDecision(FlowPermissionOnlyView.class, "flow-permission-only", principal("user"), "USER"::equals,
                AccessCheckDecision.ALLOW);
        assertNavigationDecision(FlowPermissionOnlyView.class, "flow-permission-only", principal("guest"), "GUEST"::equals,
                AccessCheckDecision.DENY);
    }

    @Test
    void programmaticFlowRoute_navigationCheckerHonorsHttpSecurityObserverPermission() {
        assertNavigationDecision(
                FlowProgrammaticView.class, "flow-programmatic", null, role -> false, AccessCheckDecision.DENY);
        assertNavigationDecision(
                FlowProgrammaticView.class, "flow-programmatic", principal("user"), "USER"::equals,
                AccessCheckDecision.ALLOW);
        assertNavigationDecision(
                FlowProgrammaticView.class, "flow-programmatic", principal("guest"), "GUEST"::equals,
                AccessCheckDecision.DENY);
    }

    @Test
    void namedHttpSecurityPolicy_navigationCheckerInvokesNamedPolicyBean() {
        assertNavigationDecision(FlowNamedPolicyView.class, "flow-named-policy", null, role -> false,
                AccessCheckDecision.DENY);
        assertNavigationDecision(FlowNamedPolicyView.class, "flow-named-policy", principal("user"), "USER"::equals,
                AccessCheckDecision.ALLOW);
        assertNavigationDecision(FlowNamedPolicyView.class, "flow-named-policy", principal("guest"), "GUEST"::equals,
                AccessCheckDecision.DENY);
    }

    @Test
    void lowercaseMethodPermission_navigationCheckerDeniesLikeQuarkusHttp() {
        assertNavigationDecision(FlowLowercaseMethodView.class, "flow-lowercase-method", principal("user"), "USER"::equals,
                AccessCheckDecision.DENY);
    }

    private Response endpointRequest(String methodName) {
        return endpointRequest(methodName, UnaryOperator.identity());
    }

    private Response endpointRequest(String methodName, UnaryOperator<RequestSpecification> customizer) {
        RequestSpecification request = RestAssured.given()
                .contentType(ContentType.JSON)
                .cookie("csrfToken", "CSRF_TOKEN")
                .header("X-CSRF-Token", "CSRF_TOKEN")
                .body("{}")
                .basePath("/connect");
        return customizer.apply(request).when().post("{endpointName}/{methodName}", SECURE_ENDPOINT, methodName);
    }

    private UnaryOperator<RequestSpecification> bearer(String username) {
        return request -> request.auth().oauth2(token(username));
    }

    private String token(String username) {
        return tokens.computeIfAbsent(username, user -> RestAssured.given()
                .contentType(ContentType.URLENC)
                .formParam("grant_type", "password")
                .formParam("client_id", clientId)
                .formParam("client_secret", clientSecret)
                .formParam("username", user)
                .formParam("password", user)
                .when()
                .post(authServerUrl + "/protocol/openid-connect/token")
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .path("access_token"));
    }

    private void assertNavigationDecision(Class<?> route, String path, Principal principal, Predicate<String> rolesChecker,
            AccessCheckDecision expectedDecision) {
        NavigationContext context = new NavigationContext(
                null,
                route,
                new Location(path),
                RouteParameters.empty(),
                principal,
                rolesChecker,
                false);

        assertThat(httpPermissionAccessChecker.get().check(context).decision()).isEqualTo(expectedDecision);
    }

    private static Principal principal(String name) {
        return () -> name;
    }
}
