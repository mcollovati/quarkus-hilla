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

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;

@QuarkusTest
@TestProfile(RootPathSecurityTestProfile.class)
class OidcRootPathSecurityTest {

    private final Map<String, String> tokens = new ConcurrentHashMap<>();

    @ConfigProperty(name = "quarkus.oidc.auth-server-url")
    String authServerUrl;

    @ConfigProperty(name = "quarkus.oidc.client-id")
    String clientId;

    @ConfigProperty(name = "quarkus.oidc.credentials.secret")
    String clientSecret;

    @Test
    void relativeHttpPermission_matchesDirectAndSyntheticFlowNavigation() {
        RestAssured.given()
                .auth()
                .oauth2(token("user"))
                .when()
                .get("/flow-config-stricter")
                .then()
                .statusCode(403);
        RestAssured.given()
                .auth()
                .oauth2(token("admin"))
                .when()
                .get("/flow-config-stricter")
                .then()
                .statusCode(200);

        navigationDecision("/flow-config-stricter", "user", "DENY");
        navigationDecision("/flow-config-stricter", "admin", "ALLOW");
    }

    @Test
    void hillaRouteMetadata_remainsEnforcedBelowRootPath() throws InterruptedException {
        assertHillaRouteStatus(
                () -> RestAssured.given().auth().oauth2(token("user")).when().get("/hilla-admin"), 403);
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
                .get("/hilla-items/42")
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
                .get("/hilla-layout-admin")
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
                .get("/hilla-absolute-admin/users")
                .then()
                .statusCode(403);
        RestAssured.given()
                .auth()
                .oauth2(token("admin"))
                .when()
                .get("/hilla-absolute-admin/users")
                .then()
                .statusCode(200);
    }

    @Test
    void flowAnnotations_remainEnforcedBelowRootPath() {
        RestAssured.given()
                .auth()
                .oauth2(token("guest"))
                .when()
                .get("/flow-annotation-stricter")
                .then()
                .statusCode(403);
        RestAssured.given()
                .auth()
                .oauth2(token("user"))
                .when()
                .get("/flow-annotation-stricter")
                .then()
                .statusCode(200);

        navigationDecision("/flow-annotation-stricter", "guest", "DENY");
        navigationDecision("/flow-annotation-stricter", "user", "ALLOW");
    }

    private void navigationDecision(String path, String username, String decision) {
        RestAssured.given()
                .auth()
                .oauth2(token(username))
                .contentType(ContentType.JSON)
                .cookie("csrfToken", "CSRF_TOKEN")
                .header("X-CSRF-Token", "CSRF_TOKEN")
                .body("{\"path\":\"" + path + "\"}")
                .when()
                .post("/connect/SecureEndpoint/navigationDecision")
                .then()
                .statusCode(200)
                .body(equalTo("\"" + decision + "\""));
    }

    private static void assertHillaRouteStatus(Supplier<Response> request, int expectedStatus)
            throws InterruptedException {
        if (!"development".equals(System.getProperty("quarkus-hilla.test-mode"))) {
            request.get().then().statusCode(expectedStatus);
            return;
        }
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        int actualStatus;
        do {
            Response response = request.get();
            actualStatus = response.statusCode();
            assertThat(actualStatus).isIn(200, expectedStatus);
            if (actualStatus == expectedStatus) {
                return;
            }
            assertThat(response.header("X-DevModePending")).isEqualTo("true");
            Thread.sleep(100);
        } while (System.nanoTime() < deadline);
        assertThat(actualStatus).isEqualTo(expectedStatus);
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
                .statusCode(200)
                .extract()
                .path("access_token"));
    }
}
