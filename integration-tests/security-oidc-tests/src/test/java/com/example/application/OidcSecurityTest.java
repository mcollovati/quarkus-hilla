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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

import com.example.application.security.TestHeaderAuthenticationMechanism;
import com.vaadin.flow.server.auth.AccessCheckDecision;
import com.vaadin.flow.server.auth.NavigationAccessChecker;
import com.vaadin.flow.server.auth.NavigationAccessControl;
import io.quarkus.arc.All;
import io.quarkus.arc.ClientProxy;
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
import com.github.mcollovati.quarkus.hilla.security.QuarkusNavigationAccessControl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;

@QuarkusTest
class OidcSecurityTest {

    private static final String SECURE_ENDPOINT = "SecureEndpoint";
    private static final String HTTP_PERMISSION_NAVIGATION_ACCESS_CHECKER =
            "com.github.mcollovati.quarkus.hilla.security.QuarkusHttpPermissionNavigationAccessChecker";

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
    @All
    List<NavigationAccessChecker> navigationAccessCheckers;

    @Test
    void oidcSecurityModel_registersGenericHillaSecurityBeansOnly() {
        assertThat(hillaSecurityPolicy.isResolvable()).isTrue();
        assertThat(navigationAccessControl).isInstanceOf(QuarkusNavigationAccessControl.class);
        assertThat(navigationAccessCheckers)
                .extracting(OidcSecurityTest::beanClassName)
                .containsExactly(HTTP_PERMISSION_NAVIGATION_ACCESS_CHECKER);
        assertThat(authenticationMechanisms).noneMatch(HillaFormAuthenticationMechanism.class::isInstance);
    }

    @Test
    void anonymousEndpoint_withoutToken_allowed() {
        endpointRequest("anonymous").then().assertThat().statusCode(200).body(equalTo("\"ANONYMOUS\""));
    }

    @Test
    void anonymousEndpoint_withInvalidBearerToken_rejectsAuthenticationFailure() {
        endpointRequest("anonymous", request -> request.header("Authorization", "Bearer invalid"))
                .then()
                .assertThat()
                .statusCode(401);
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
        String tokenPayload =
                new String(Base64.getUrlDecoder().decode(token("user").split("\\.")[1]), StandardCharsets.UTF_8);
        assertThat(tokenPayload).contains("\"groups\":[\"USER\"");

        RestAssured.given()
                .auth()
                .oauth2(token("user"))
                .when()
                .post("/connect/security/probe/roles")
                .then()
                .assertThat()
                .statusCode(200)
                .body("$", org.hamcrest.Matchers.hasItem("USER"));

        endpointRequest("effectiveRoles", bearer("user"))
                .then()
                .assertThat()
                .statusCode(200)
                .body("$", org.hamcrest.Matchers.hasItem("USER"));

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
    void httpPermissionRules_matchNavigationCheckerDecisions() {
        assertHttpAndNavigation("/flow-public", null, 200);
        assertHttpAndNavigation("/flow-protected", null, 401);
        assertHttpAndNavigation("/flow-protected", "user", 200);

        assertHttpAndNavigation("/flow-user", "user", 200);
        assertHttpAndNavigation("/flow-user", "guest", 403);

        assertHttpAndNavigation("/flow-admin", "admin", 200);
        assertHttpAndNavigation("/flow-admin", "user", 403);

        assertHttpAndNavigation("/flow-annotation-stricter", null, 401);
        assertHttpAndNavigation("/flow-annotation-stricter", "user", 200);
        assertHttpAndNavigation("/flow-annotation-stricter", "guest", 403);

        assertHttpAndNavigation("/flow-config-stricter", null, 401);
        assertHttpAndNavigation("/flow-config-stricter", "user", 403);
        assertHttpAndNavigation("/flow-config-stricter", "admin", 200);

        assertHttpAndNavigation("/flow-combined-roles", "user", 403);
        assertHttpAndNavigation("/flow-combined-roles", "admin", 200);
        assertHttpAndNavigation("/flow-combined-roles", "guest", 403);

        assertHttpAndNavigation("/flow-denied", null, 401);
        assertHttpAndNavigation("/flow-denied", "admin", 403);

        assertHttpAndNavigation("/flow-global-denied", null, 401);
        assertHttpAndNavigation("/flow-global-denied", "user", 403);

        assertHttpAndNavigation("/flow-global-role", "mapped", 200);
        assertHttpAndNavigation("/flow-global-role", "guest", 403);

        assertHttpAndNavigation("/flow-permission-only", null, 401);
        assertHttpAndNavigation("/flow-permission-only", "user", 200);
        assertHttpAndNavigation("/flow-permission-only", "guest", 403);

        assertHttpAndNavigation("/flow-programmatic", null, 401);
        assertHttpAndNavigation("/flow-programmatic", "user", 200);
        assertHttpAndNavigation("/flow-programmatic", "guest", 403);

        assertHttpAndNavigation("/flow-named-policy", null, 401);
        assertHttpAndNavigation("/flow-named-policy", "user", 200);
        assertHttpAndNavigation("/flow-named-policy", "guest", 403);

        assertHttpAndNavigation("/flow-lowercase-method", "user", 403);
    }

    @Test
    void connectPathRoleAugmentation_doesNotLeakIntoTargetNavigation() {
        assertHttpAndNavigation("/flow-transport-role", "transport", 403);

        endpointRequest("transportNavigationDecision", "{\"path\":\"/flow-transport-role\"}", bearer("transport"))
                .then()
                .assertThat()
                .statusCode(200)
                .body(equalTo("\"DENY\""));
    }

    @Test
    void invalidBearerToken_onAnonymousFlowRoute_rejectsAuthenticationFailure() {
        RestAssured.given()
                .header("Authorization", "Bearer invalid")
                .when()
                .get("/flow-public")
                .then()
                .assertThat()
                .statusCode(401);
    }

    @Test
    void bearerOnlyTarget_doesNotReuseIdentityFromAnotherAuthenticationMechanism() {
        assertHttpAndNavigation("/flow-bearer-only", "user", 200);

        RestAssured.given()
                .header(TestHeaderAuthenticationMechanism.HEADER, "header-user")
                .when()
                .get("/flow-bearer-only")
                .then()
                .assertThat()
                .statusCode(401);

        endpointRequest(
                        "navigationDecision",
                        "{\"path\":\"/flow-bearer-only\"}",
                        request -> request.header(TestHeaderAuthenticationMechanism.HEADER, "header-user"))
                .then()
                .assertThat()
                .statusCode(200)
                .body(equalTo("\"DENY\""));
    }

    @Test
    void targetPath_reauthenticatesWithoutExplicitMechanismConstraint() {
        RestAssured.given()
                .header(
                        TestHeaderAuthenticationMechanism.HEADER,
                        TestHeaderAuthenticationMechanism.TRANSPORT_ONLY_ADMIN)
                .when()
                .get("/flow-target-reauthentication")
                .then()
                .assertThat()
                .statusCode(401);

        endpointRequest(
                        "navigationDecision",
                        "{\"path\":\"/flow-target-reauthentication\"}",
                        request -> request.header(
                                TestHeaderAuthenticationMechanism.HEADER,
                                TestHeaderAuthenticationMechanism.TRANSPORT_ONLY_ADMIN))
                .then()
                .assertThat()
                .statusCode(200)
                .body(equalTo("\"DENY\""));
    }

    @Test
    void hillaClientRoutes_enforceRouteMetadata() {
        RestAssured.given().when().get("/hilla-protected").then().statusCode(401);
        RestAssured.given()
                .auth()
                .oauth2(token("user"))
                .when()
                .get("/hilla-protected")
                .then()
                .statusCode(200);
        RestAssured.given()
                .auth()
                .oauth2(token("user"))
                .when()
                .get("/hilla-user")
                .then()
                .statusCode(200);
        RestAssured.given()
                .auth()
                .oauth2(token("guest"))
                .when()
                .get("/hilla-user")
                .then()
                .statusCode(403);
        RestAssured.given()
                .auth()
                .oauth2(token("admin"))
                .when()
                .get("/hilla-admin")
                .then()
                .statusCode(200);
        RestAssured.given()
                .auth()
                .oauth2(token("user"))
                .when()
                .get("/hilla-admin")
                .then()
                .statusCode(403);
        RestAssured.given()
                .auth()
                .oauth2(token("admin"))
                .when()
                .get("/hilla-layout-admin")
                .then()
                .statusCode(200);
        RestAssured.given()
                .auth()
                .oauth2(token("user"))
                .when()
                .get("/hilla-layout-admin")
                .then()
                .statusCode(403);
        RestAssured.given()
                .auth()
                .oauth2(token("admin"))
                .when()
                .get("/hilla-absolute-admin/users")
                .then()
                .statusCode(200);
        RestAssured.given()
                .auth()
                .oauth2(token("user"))
                .when()
                .get("/hilla-absolute-admin/users")
                .then()
                .statusCode(403);
        RestAssured.given()
                .auth()
                .oauth2(token("admin"))
                .when()
                .get("/hilla-items/42")
                .then()
                .statusCode(200);
        RestAssured.given()
                .auth()
                .oauth2(token("user"))
                .when()
                .get("/hilla-items/42")
                .then()
                .statusCode(403);
        RestAssured.given()
                .urlEncodingEnabled(false)
                .auth()
                .oauth2(token("admin"))
                .when()
                .get("/hilla-items/a%2Fb")
                .then()
                .statusCode(200);
        RestAssured.given()
                .urlEncodingEnabled(false)
                .auth()
                .oauth2(token("user"))
                .when()
                .get("/hilla-items/a%2Fb")
                .then()
                .statusCode(403);
        RestAssured.given()
                .urlEncodingEnabled(false)
                .auth()
                .oauth2(token("admin"))
                .when()
                .get("/hilla%2Dadmin")
                .then()
                .statusCode(200);
        RestAssured.given()
                .urlEncodingEnabled(false)
                .auth()
                .oauth2(token("user"))
                .when()
                .get("/hilla%2Dadmin")
                .then()
                .statusCode(403);
        RestAssured.given()
                .auth()
                .oauth2(token("admin"))
                .when()
                .get("/hilla-wildcard/a/b")
                .then()
                .statusCode(200);
        RestAssured.given()
                .auth()
                .oauth2(token("user"))
                .when()
                .get("/hilla-wildcard/a/b")
                .then()
                .statusCode(403);
    }

    @Test
    void encodedPaths_matchNavigationCheckerDecisions() {
        assertHttpAndNavigation("/rest/encoded%3Bv=1", "user", 200);
        assertHttpAndNavigation("/rest/encoded%3Bv=1", "guest", 403);
        assertHttpAndNavigation("/rest/dot/%2e%2e/dot", "user", 200);
        assertHttpAndNavigation("/rest/dot/%2e%2e/dot", "guest", 403);
    }

    private Response endpointRequest(String methodName) {
        return endpointRequest(methodName, UnaryOperator.identity());
    }

    private Response endpointRequest(String methodName, UnaryOperator<RequestSpecification> customizer) {
        return endpointRequest(methodName, "{}", customizer);
    }

    private Response endpointRequest(String methodName, String body, UnaryOperator<RequestSpecification> customizer) {
        RequestSpecification request = RestAssured.given()
                .contentType(ContentType.JSON)
                .cookie("csrfToken", "CSRF_TOKEN")
                .header("X-CSRF-Token", "CSRF_TOKEN")
                .body(body)
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

    private void assertHttpAndNavigation(String path, String username, int expectedStatus) {
        RequestSpecification directRequest = RestAssured.given().urlEncodingEnabled(false);
        if (username != null) {
            directRequest.auth().oauth2(token(username));
        }
        directRequest.when().get(path).then().assertThat().statusCode(expectedStatus);

        AccessCheckDecision expectedDecision =
                expectedStatus == 200 ? AccessCheckDecision.ALLOW : AccessCheckDecision.DENY;
        assertThat(navigationDecision(path, username)).isEqualTo(expectedDecision);
    }

    private AccessCheckDecision navigationDecision(String path, String username) {
        UnaryOperator<RequestSpecification> customizer = username == null ? UnaryOperator.identity() : bearer(username);
        String response = endpointRequest(
                        "navigationDecision",
                        "{\"path\":\"" + path.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}",
                        customizer)
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .asString();
        return AccessCheckDecision.valueOf(response.replace("\"", ""));
    }

    private static String beanClassName(Object bean) {
        Object unwrapped = bean instanceof ClientProxy ? ClientProxy.unwrap(bean) : bean;
        return unwrapped.getClass().getName();
    }
}
