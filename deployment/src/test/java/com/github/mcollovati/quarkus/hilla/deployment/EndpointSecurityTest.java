/*
 * Copyright 2023 Marco Collovati, Dario Götze
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
package com.github.mcollovati.quarkus.hilla.deployment;

import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import io.quarkus.security.test.utils.TestIdentityController;
import io.quarkus.security.test.utils.TestIdentityProvider;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.specification.RequestSpecification;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.mcollovati.quarkus.hilla.deployment.TestUtils.User;
import com.github.mcollovati.quarkus.hilla.deployment.endpoints.SecureEndpoint;

import static com.github.mcollovati.quarkus.hilla.deployment.TestUtils.ADMIN;
import static com.github.mcollovati.quarkus.hilla.deployment.TestUtils.GUEST;
import static com.github.mcollovati.quarkus.hilla.deployment.TestUtils.USER;
import static com.github.mcollovati.quarkus.hilla.deployment.TestUtils.givenEndpointRequest;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;

class EndpointSecurityTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest().setArchiveProducer(() -> ShrinkWrap.create(
                    JavaArchive.class)
            .addClasses(TestIdentityProvider.class, TestIdentityController.class, TestUtils.class, SecureEndpoint.class)
            .addAsResource(
                    new StringAsset("quarkus.http.auth.basic=true\nquarkus.http.auth.proactive=true\n"),
                    "application.properties"));

    public static final String SECURE_ENDPOINT = "SecureEndpoint";

    @BeforeAll
    public static void setupUsers() {
        TestIdentityController.resetRoles()
                .add(ADMIN.username, ADMIN.pwd, "ADMIN")
                .add(USER.username, USER.pwd, "USER")
                .add(GUEST.username, GUEST.pwd, "GUEST");
    }

    @Test
    void securedEndpoint_permitAll_authenticatedUsersAllowed() {
        Stream.of(USER, GUEST)
                .forEach(user -> givenEndpointRequest(SECURE_ENDPOINT, "authenticated", authenticate(user))
                        .then()
                        .assertThat()
                        .statusCode(200)
                        .and()
                        .body(equalTo("\"AUTHENTICATED\"")));

        givenEndpointRequest(SECURE_ENDPOINT, "authenticated")
                .then()
                .assertThat()
                .statusCode(401)
                .and()
                .body("message", containsString(SECURE_ENDPOINT))
                .body("message", containsString("reason: 'Access denied'"));
    }

    @Test
    void securedEndpoint_adminOnly_onlyAdminAllowed() {
        givenEndpointRequest(SECURE_ENDPOINT, "adminOnly", authenticate(ADMIN))
                .then()
                .assertThat()
                .statusCode(200)
                .and()
                .body(equalTo("\"ADMIN\""));

        Stream.of(USER, GUEST).forEach(user -> givenEndpointRequest(SECURE_ENDPOINT, "adminOnly", authenticate(user))
                .then()
                .assertThat()
                .statusCode(401)
                .and()
                .body("message", containsString(SECURE_ENDPOINT))
                .body("message", containsString("reason: 'Access denied'")));

        givenEndpointRequest(SECURE_ENDPOINT, "adminOnly")
                .then()
                .assertThat()
                .statusCode(401)
                .and()
                .body("message", containsString(SECURE_ENDPOINT))
                .body("message", containsString("reason: 'Access denied'"));
    }

    @Test
    void securedEndpoint_userOnly_onlyUserAllowed() {
        givenEndpointRequest(SECURE_ENDPOINT, "userOnly", authenticate(USER))
                .then()
                .assertThat()
                .statusCode(200)
                .and()
                .body(equalTo("\"USER\""));

        Stream.of(ADMIN, GUEST).forEach(user -> givenEndpointRequest(SECURE_ENDPOINT, "userOnly", authenticate(user))
                .then()
                .assertThat()
                .statusCode(401)
                .and()
                .body("message", containsString(SECURE_ENDPOINT))
                .body("message", containsString("reason: 'Access denied'")));

        givenEndpointRequest(SECURE_ENDPOINT, "userOnly")
                .then()
                .assertThat()
                .statusCode(401)
                .and()
                .body("message", containsString(SECURE_ENDPOINT))
                .body("message", containsString("reason: 'Access denied'"));
    }

    @Test
    void securedEndpoint_adminAndUserOnly_onlyAdminAndUserAllowed() {
        Stream.of(ADMIN, USER).forEach(user -> givenEndpointRequest(SECURE_ENDPOINT, "userAndAdmin", authenticate(user))
                .then()
                .assertThat()
                .statusCode(200)
                .and()
                .body(equalTo("\"USER AND ADMIN\"")));

        givenEndpointRequest(SECURE_ENDPOINT, "userAndAdmin", authenticate(GUEST))
                .then()
                .assertThat()
                .statusCode(401)
                .and()
                .body("message", containsString(SECURE_ENDPOINT))
                .body("message", containsString("reason: 'Access denied'"));

        givenEndpointRequest(SECURE_ENDPOINT, "userAndAdmin")
                .then()
                .assertThat()
                .statusCode(401)
                .and()
                .body("message", containsString(SECURE_ENDPOINT))
                .body("message", containsString("reason: 'Access denied'"));
    }

    @Test
    void securedEndpoint_deny_notAllowed() {
        Stream.of(ADMIN, USER, GUEST).forEach(user -> givenEndpointRequest(SECURE_ENDPOINT, "deny", authenticate(user))
                .then()
                .assertThat()
                .statusCode(401)
                .and()
                .body("message", containsString(SECURE_ENDPOINT))
                .body("message", containsString("reason: 'Access denied'")));

        givenEndpointRequest(SECURE_ENDPOINT, "deny")
                .then()
                .assertThat()
                .statusCode(401)
                .and()
                .body("message", containsString(SECURE_ENDPOINT))
                .body("message", containsString("reason: 'Access denied'"));
    }

    @Test
    void securedEndpoint_notAnnotatedMethod_denyAll() {
        Stream.of(ADMIN, USER, GUEST)
                .forEach(user -> givenEndpointRequest(SECURE_ENDPOINT, "denyByDefault", authenticate(user))
                        .then()
                        .assertThat()
                        .statusCode(401)
                        .and()
                        .body("message", containsString(SECURE_ENDPOINT))
                        .body("message", containsString("reason: 'Access denied'")));

        givenEndpointRequest(SECURE_ENDPOINT, "denyByDefault")
                .then()
                .assertThat()
                .statusCode(401)
                .and()
                .body("message", containsString(SECURE_ENDPOINT))
                .body("message", containsString("reason: 'Access denied'"));
    }

    private static UnaryOperator<RequestSpecification> authenticate(User user) {
        return spec -> spec.auth().preemptive().basic(user.username, user.pwd);
    }
}
