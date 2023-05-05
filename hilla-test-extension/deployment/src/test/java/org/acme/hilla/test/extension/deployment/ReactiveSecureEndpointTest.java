package org.acme.hilla.test.extension.deployment;

import javax.enterprise.context.control.ActivateRequestContext;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.ContainerProvider;
import javax.websocket.Session;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

import io.quarkus.security.test.utils.TestIdentityController;
import io.quarkus.security.test.utils.TestIdentityProvider;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.common.http.TestHTTPResource;
import io.vertx.core.http.HttpHeaders;
import org.acme.hilla.test.extension.deployment.endpoints.ReactiveSecureEndpoint;
import org.assertj.core.api.AbstractStringAssert;
import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.acme.hilla.test.extension.deployment.TestUtils.ADMIN;
import static org.acme.hilla.test.extension.deployment.TestUtils.ANONYMOUS;
import static org.acme.hilla.test.extension.deployment.TestUtils.GUEST;
import static org.acme.hilla.test.extension.deployment.TestUtils.USER;

class ReactiveSecureEndpointTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(TestIdentityProvider.class,
                            TestIdentityController.class, TestUtils.class,
                            ReactiveSecureEndpoint.class, HillaPushClient.class)
                    .addAsResource(new StringAsset(
                            "quarkus.http.auth.basic=true\nquarkus.http.auth.proactive=true\n"),
                            "application.properties")
                    .add(new StringAsset(
                            "com.vaadin.experimental.hillaPush=true"),
                            "vaadin-featureflags.properties"));

    @BeforeAll
    public static void setupUsers() {
        TestIdentityController.resetRoles()
                .add(ADMIN.username, ADMIN.pwd, "ADMIN")
                .add(USER.username, USER.pwd, "USER")
                .add(GUEST.username, GUEST.pwd, "GUEST");
    }

    @TestHTTPResource("/HILLA/push")
    URI uri;

    @Test
    @ActivateRequestContext
    void securedEndpoint_permitAll_authenticatedUsersAllowed() {
        Stream.of(ADMIN, USER, GUEST)
                .forEach(user -> pushConnection(user, "authenticated").accept(
                        msg -> msg.contains("\"item\":\"AUTHENTICATED\"")));
        pushConnection(ANONYMOUS, "authenticated")
                .accept(assertAccessDenied("authenticated"));
    }

    @Test
    @ActivateRequestContext
    void securedEndpoint_adminOnly_onlyAdminAllowed() {
        pushConnection(ADMIN, "adminOnly")
                .accept(msg -> msg.contains("\"item\":\"ADMIN\""));
        Stream.of(ANONYMOUS, USER, GUEST)
                .forEach(user -> pushConnection(user, "adminOnly")
                        .accept(assertAccessDenied("adminOnly")));
    }

    @Test
    @ActivateRequestContext
    void securedEndpoint_userOnly_onlyUserAllowed() {
        pushConnection(USER, "userOnly")
                .accept(msg -> msg.contains("\"item\":\"USER\""));
        Stream.of(ANONYMOUS, ADMIN, GUEST)
                .forEach(user -> pushConnection(user, "userOnly")
                        .accept(assertAccessDenied("userOnly")));
    }

    @Test
    @ActivateRequestContext
    void securedEndpoint_adminAndUserOnly_onlyAdminAndUserAllowed() {
        Stream.of(ADMIN, USER).forEach(
                user -> pushConnection(user, "userAndAdmin").accept(
                        msg -> msg.contains("\"item\":\"USER AND ADMIN\"")));
        Stream.of(ANONYMOUS, GUEST)
                .forEach(user -> pushConnection(user, "userAndAdmin")
                        .accept(assertAccessDenied("userAndAdmin")));
    }

    @Test
    @ActivateRequestContext
    void securedEndpoint_deny_notAllowed() {
        Stream.of(ANONYMOUS, ADMIN, USER, GUEST)
                .forEach(user -> pushConnection(user, "deny")
                        .accept(assertAccessDenied("deny")));
    }

    @Test
    @ActivateRequestContext
    void securedEndpoint_notAnnotatedMethod_denyAll() {
        Stream.of(ANONYMOUS, ADMIN, USER, GUEST)
                .forEach(user -> pushConnection(user, "denyByDefault")
                        .accept(assertAccessDenied("denyByDefault")));
    }

    private Consumer<Consumer<AbstractStringAssert<?>>> pushConnection(
            TestUtils.User user, String methodName) {
        return asserter -> {
            URI connectURI = createPUSHConnectURI();
            HillaPushClient client = new HillaPushClient(
                    "ReactiveSecureEndpoint", methodName);
            ClientEndpointConfig cec = ClientEndpointConfig.Builder.create()
                    .configurator(new BasicAuthConfigurator(user)).build();
            try (Session ignored = ContainerProvider.getWebSocketContainer()
                    .connectToServer(client, cec, connectURI)) {
                client.assertMessageReceived(10, TimeUnit.SECONDS, "CONNECT");
                client.assertMessageReceived(1, TimeUnit.SECONDS, asserter);
            } catch (Exception e) {
                Assertions.fail("PUSH communication failed", e);
            }
        };
    }

    private Consumer<AbstractStringAssert<?>> assertAccessDenied(
            String method) {
        return msg -> msg.contains("Access denied")
                .contains("Endpoint 'ReactiveSecureEndpoint'")
                .contains(String.format("method '%s'", method));
    }

    private static class BasicAuthConfigurator
            extends ClientEndpointConfig.Configurator {

        private final TestUtils.User user;

        public BasicAuthConfigurator(TestUtils.User user) {
            this.user = user;
        }

        @Override
        public void beforeRequest(Map<String, List<String>> headers) {
            if (user.username != null && user.pwd != null) {
                headers.put(HttpHeaders.AUTHORIZATION.toString(),
                        Collections.singletonList("Basic " + Base64.getEncoder()
                                .encodeToString((user.username + ":" + user.pwd)
                                        .getBytes(StandardCharsets.UTF_8))));
            }
        }
    }

    private URI createPUSHConnectURI() {
        return URI.create(uri.toASCIIString() + "?X-Atmosphere-tracking-id="
                + UUID.randomUUID() + "&X-Atmosphere-Framework=3.1.4-javascript"
                + "&X-Atmosphere-Transport=websocket"
                + "&X-Atmosphere-TrackMessageSize=true" + "&Content-Type="
                + URLEncoder.encode("application/json; charset=UTF-8",
                        StandardCharsets.UTF_8)
                + "&X-atmo-protocol=true" + "&X-CSRF-Token="
                + UUID.randomUUID());
    }

}
