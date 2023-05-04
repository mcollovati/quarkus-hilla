package org.acme.hilla.test.extension.deployment;

import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import io.quarkus.security.test.utils.AuthData;
import io.quarkus.security.test.utils.IdentityMock;
import io.quarkus.security.test.utils.SecurityTestUtils;
import io.quarkus.security.test.utils.TestIdentityController;
import io.quarkus.security.test.utils.TestIdentityProvider;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.specification.RequestSpecification;
import org.acme.hilla.test.extension.deployment.endpoints.SecureEndpoint;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.acme.hilla.test.extension.deployment.TestUtils.givenEndpointRequest;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;

class EndpointSecurityTest {

    private static final User ADMIN = new User("admin", "admin");
    private static final User USER = new User("user", "user");
    private static final User GUEST = new User("guest", "guest");

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(TestIdentityProvider.class,
                            TestIdentityController.class, TestUtils.class,
                            SecureEndpoint.class)
                    .addAsResource(new StringAsset(
                            "quarkus.http.auth.basic=true\nquarkus.http.auth.proactive=true\n"),
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
                .forEach(user -> givenEndpointRequest(SECURE_ENDPOINT,
                        "authenticated", authenticate(user)).then().assertThat()
                        .statusCode(200).and()
                        .body(equalTo("\"AUTHENTICATED\"")));

        givenEndpointRequest(SECURE_ENDPOINT, "authenticated").then()
                .assertThat().statusCode(401).and()
                .body("message", containsString(SECURE_ENDPOINT))
                .body("message", containsString("reason: 'Access denied'"));
    }

    @Test
    void securedEndpoint_adminOnly_onlyAdminAllowed() {
        givenEndpointRequest(SECURE_ENDPOINT, "adminOnly", authenticate(ADMIN))
                .then().assertThat().statusCode(200).and()
                .body(equalTo("\"ADMIN\""));

        Stream.of(USER, GUEST)
                .forEach(user -> givenEndpointRequest(SECURE_ENDPOINT,
                        "adminOnly", authenticate(user)).then().assertThat()
                        .statusCode(401).and()
                        .body("message", containsString(SECURE_ENDPOINT))
                        .body("message",
                                containsString("reason: 'Access denied'")));

        givenEndpointRequest(SECURE_ENDPOINT, "adminOnly").then().assertThat()
                .statusCode(401).and()
                .body("message", containsString(SECURE_ENDPOINT))
                .body("message", containsString("reason: 'Access denied'"));
    }

    @Test
    void securedEndpoint_userOnly_onlyUserAllowed() {
        givenEndpointRequest(SECURE_ENDPOINT, "userOnly", authenticate(USER))
                .then().assertThat().statusCode(200).and()
                .body(equalTo("\"USER\""));

        Stream.of(ADMIN, GUEST)
                .forEach(user -> givenEndpointRequest(SECURE_ENDPOINT,
                        "userOnly", authenticate(user)).then().assertThat()
                        .statusCode(401).and()
                        .body("message", containsString(SECURE_ENDPOINT))
                        .body("message",
                                containsString("reason: 'Access denied'")));

        givenEndpointRequest(SECURE_ENDPOINT, "userOnly").then().assertThat()
                .statusCode(401).and()
                .body("message", containsString(SECURE_ENDPOINT))
                .body("message", containsString("reason: 'Access denied'"));
    }

    @Test
    void securedEndpoint_adminAndUserOnly_onlyAdminAndUserAllowed() {
        Stream.of(ADMIN, USER)
                .forEach(user -> givenEndpointRequest(SECURE_ENDPOINT,
                        "userAndAdmin", authenticate(user)).then().assertThat()
                        .statusCode(200).and()
                        .body(equalTo("\"USER AND ADMIN\"")));

        givenEndpointRequest(SECURE_ENDPOINT, "userAndAdmin", authenticate(GUEST))
                .then().assertThat().statusCode(401).and()
                .body("message", containsString(SECURE_ENDPOINT))
                .body("message", containsString("reason: 'Access denied'"));

        givenEndpointRequest(SECURE_ENDPOINT, "userAndAdmin").then().assertThat()
                .statusCode(401).and()
                .body("message", containsString(SECURE_ENDPOINT))
                .body("message", containsString("reason: 'Access denied'"));
    }

    @Test
    void securedEndpoint_deny_notAllowed() {
        Stream.of(ADMIN, USER, GUEST)
                .forEach(user -> givenEndpointRequest(SECURE_ENDPOINT, "deny",
                        authenticate(user)).then().assertThat().statusCode(401)
                        .and().body("message", containsString(SECURE_ENDPOINT))
                        .body("message",
                                containsString("reason: 'Access denied'")));

        givenEndpointRequest(SECURE_ENDPOINT, "deny").then().assertThat()
                .statusCode(401).and()
                .body("message", containsString(SECURE_ENDPOINT))
                .body("message", containsString("reason: 'Access denied'"));
    }

    @Test
    void securedEndpoint_notAnnotatedMethod_denyAll() {
        Stream.of(ADMIN, USER, GUEST)
                .forEach(user -> givenEndpointRequest(SECURE_ENDPOINT,
                        "denyByDefault", authenticate(user)).then().assertThat()
                        .statusCode(401).and()
                        .body("message", containsString(SECURE_ENDPOINT))
                        .body("message",
                                containsString("reason: 'Access denied'")));

        givenEndpointRequest(SECURE_ENDPOINT, "denyByDefault").then()
                .assertThat().statusCode(401).and()
                .body("message", containsString(SECURE_ENDPOINT))
                .body("message", containsString("reason: 'Access denied'"));
    }

    private static UnaryOperator<RequestSpecification> authenticate(User user) {
        return spec -> spec.auth().preemptive().basic(user.username, user.pwd);
    }

    private static final class User {
        private final String username;
        private final String pwd;

        User(String username, String pwd) {
            this.username = username;
            this.pwd = pwd;
        }
    }
}
