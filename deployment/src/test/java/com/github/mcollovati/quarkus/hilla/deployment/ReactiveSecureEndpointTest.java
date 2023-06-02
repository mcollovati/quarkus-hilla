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

import static com.github.mcollovati.quarkus.hilla.deployment.TestUtils.ADMIN;
import static com.github.mcollovati.quarkus.hilla.deployment.TestUtils.ANONYMOUS;
import static com.github.mcollovati.quarkus.hilla.deployment.TestUtils.GUEST;
import static com.github.mcollovati.quarkus.hilla.deployment.TestUtils.USER;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.github.mcollovati.quarkus.hilla.deployment.endpoints.ReactiveSecureEndpoint;
import io.quarkus.security.test.utils.TestIdentityController;
import io.quarkus.security.test.utils.TestIdentityProvider;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.common.http.TestHTTPResource;
import io.vertx.core.http.HttpHeaders;
import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.enterprise.context.control.ActivateRequestContext;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.ContainerProvider;
import javax.websocket.Session;
import org.assertj.core.api.AbstractStringAssert;
import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ReactiveSecureEndpointTest {
    private static final String ENDPOINT_NAME = ReactiveSecureEndpoint.class.getSimpleName();

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            TestIdentityProvider.class,
                            TestIdentityController.class,
                            TestUtils.class,
                            ReactiveSecureEndpoint.class,
                            HillaPushClient.class)
                    .addAsResource(
                            new StringAsset("quarkus.http.auth.basic=true\nquarkus.http.auth.proactive=true\n"),
                            "application.properties")
                    .add(new StringAsset("com.vaadin.experimental.hillaPush=true"), "vaadin-featureflags.properties"));

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
        Stream.of(ADMIN, USER, GUEST).forEach(user -> pushConnection(user, "authenticated")
                .accept(msg -> msg.contains("\"item\":\"AUTHENTICATED\"")));
        pushConnection(ANONYMOUS, "authenticated").accept(assertAccessDenied("authenticated"));
    }

    @Test
    @ActivateRequestContext
    void securedEndpoint_adminOnly_onlyAdminAllowed() {
        pushConnection(ADMIN, "adminOnly").accept(msg -> msg.contains("\"item\":\"ADMIN\""));
        Stream.of(ANONYMOUS, USER, GUEST)
                .forEach(user -> pushConnection(user, "adminOnly").accept(assertAccessDenied("adminOnly")));
    }

    @Test
    @ActivateRequestContext
    void securedEndpoint_userOnly_onlyUserAllowed() {
        pushConnection(USER, "userOnly").accept(msg -> msg.contains("\"item\":\"USER\""));
        Stream.of(ANONYMOUS, ADMIN, GUEST)
                .forEach(user -> pushConnection(user, "userOnly").accept(assertAccessDenied("userOnly")));
    }

    @Test
    @ActivateRequestContext
    void securedEndpoint_adminAndUserOnly_onlyAdminAndUserAllowed() {
        Stream.of(ADMIN, USER).forEach(user -> pushConnection(user, "userAndAdmin")
                .accept(msg -> msg.contains("\"item\":\"USER AND ADMIN\"")));
        Stream.of(ANONYMOUS, GUEST)
                .forEach(user -> pushConnection(user, "userAndAdmin").accept(assertAccessDenied("userAndAdmin")));
    }

    @Test
    @ActivateRequestContext
    void securedEndpoint_deny_notAllowed() {
        Stream.of(ANONYMOUS, ADMIN, USER, GUEST)
                .forEach(user -> pushConnection(user, "deny").accept(assertAccessDenied("deny")));
    }

    @Test
    @ActivateRequestContext
    void securedEndpoint_notAnnotatedMethod_denyAll() {
        Stream.of(ANONYMOUS, ADMIN, USER, GUEST)
                .forEach(user -> pushConnection(user, "denyByDefault").accept(assertAccessDenied("denyByDefault")));
    }

    private Consumer<Consumer<AbstractStringAssert<?>>> pushConnection(TestUtils.User user, String methodName) {
        return asserter -> {
            URI connectURI = HillaPushClient.createPUSHConnectURI(uri);
            HillaPushClient client = new HillaPushClient(ENDPOINT_NAME, methodName);
            ClientEndpointConfig cec = ClientEndpointConfig.Builder.create()
                    .configurator(new BasicAuthConfigurator(user))
                    .build();
            try (Session ignored = ContainerProvider.getWebSocketContainer().connectToServer(client, cec, connectURI)) {
                client.assertMessageReceived(10, TimeUnit.SECONDS, "CONNECT");
                client.assertMessageReceived(1, TimeUnit.SECONDS, asserter);
            } catch (Exception e) {
                Assertions.fail("PUSH communication failed", e);
            }
        };
    }

    private Consumer<AbstractStringAssert<?>> assertAccessDenied(String method) {
        return msg -> msg.contains("Access denied")
                .containsSequence("Endpoint '", ENDPOINT_NAME, "'")
                .contains(String.format("method '%s'", method));
    }

    private static class BasicAuthConfigurator extends ClientEndpointConfig.Configurator {

        private final TestUtils.User user;

        public BasicAuthConfigurator(TestUtils.User user) {
            this.user = user;
        }

        @Override
        public void beforeRequest(Map<String, List<String>> headers) {
            if (user.username != null && user.pwd != null) {
                String credentials = user.username + ":" + user.pwd;
                String authHeader = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(UTF_8));
                headers.put(HttpHeaders.AUTHORIZATION.toString(), List.of(authHeader));
            }
        }
    }
}
