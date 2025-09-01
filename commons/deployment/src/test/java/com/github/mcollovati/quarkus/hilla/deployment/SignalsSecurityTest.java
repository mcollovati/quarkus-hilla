/*
 * Copyright 2024 Marco Collovati, Dario Götze
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

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.Session;
import java.io.StringReader;
import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import com.vaadin.hilla.signals.handler.SignalsHandler;
import com.vaadin.signals.Id;
import io.quarkus.security.test.utils.TestIdentityController;
import io.quarkus.security.test.utils.TestIdentityProvider;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.common.http.TestHTTPResource;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.http.HttpHeaders;
import org.assertj.core.api.AbstractStringAssert;
import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.mcollovati.quarkus.hilla.deployment.signals.SecureNumberSignalService;

import static java.nio.charset.StandardCharsets.UTF_8;

import static org.assertj.core.api.Assertions.assertThat;

import static com.github.mcollovati.quarkus.hilla.deployment.TestUtils.ADMIN;
import static com.github.mcollovati.quarkus.hilla.deployment.TestUtils.ANONYMOUS;
import static com.github.mcollovati.quarkus.hilla.deployment.TestUtils.GUEST;
import static com.github.mcollovati.quarkus.hilla.deployment.TestUtils.USER;
import static com.github.mcollovati.quarkus.hilla.deployment.TestUtils.givenEndpointRequest;

class SignalsSecurityTest {

    @TestHTTPResource("/HILLA/push")
    URI pushURI;

    @Inject
    SecureNumberSignalService secureNumberSignalService;

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withConfigurationResource(testResource("test-application.properties"))
            .overrideRuntimeConfigKey("quarkus.http.auth.basic", "true")
            .overrideRuntimeConfigKey("quarkus.http.auth.proactive", "true")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            TestUtils.class,
                            TestIdentityProvider.class,
                            TestIdentityController.class,
                            SecureNumberSignalService.class,
                            HillaPushClient.class));

    @BeforeAll
    public static void setupUsers() {
        TestIdentityController.resetRoles()
                .add(ADMIN.username, ADMIN.pwd, "ADMIN")
                .add(USER.username, USER.pwd, "USER")
                .add(GUEST.username, GUEST.pwd, "GUEST");
    }

    @BeforeEach
    void resetSignals() {
        secureNumberSignalService.resetCounters();
    }

    @Test
    @ActivateRequestContext
    void subscribeSecuredSignal_permitAll_authenticatedUsersAllowed() {
        Stream.of(ADMIN, USER, GUEST).forEach(user -> subscribeSignal(user, "userCounter")
                .accept(msg -> msg.satisfies(json -> assertSnapshotReceived(json, 20))));
        subscribeSignal(ANONYMOUS, "userCounter").accept(assertAccessDenied("userCounter"));
    }

    @Test
    @ActivateRequestContext
    void subscribeSecuredSignal_adminOnly_onlyAdminAllowed() {
        subscribeSignal(ADMIN, "adminCounter").accept(msg -> msg.satisfies(json -> assertSnapshotReceived(json, 30)));
        Stream.of(ANONYMOUS, USER, GUEST)
                .forEach(user -> subscribeSignal(user, "adminCounter").accept(assertAccessDenied("adminCounter")));
    }

    @Test
    @ActivateRequestContext
    void updateSecuredSignal_adminOnly_onlyAdminAllowed() {
        AtomicInteger value = new AtomicInteger(0);
        updateSignalValue(ADMIN, "adminCounter", value.addAndGet(50))
                .accept(msg -> msg.satisfies(json -> assertUpdateReceived(json, value.get())));
        updateSignalValueNotAuthenticated(ANONYMOUS, "adminCounter", value.addAndGet(7))
                .accept(assertAccessDenied("adminCounter"));
        Stream.of(USER, GUEST).forEach(user -> updateSignalValueNotAllowed(user, "adminCounter", value.addAndGet(7))
                .accept(assertAccessDenied("adminCounter")));
    }

    @Test
    @ActivateRequestContext
    void updateSecuredSignal_permitAll_authenticatedUsersAllowed() {
        AtomicInteger value = new AtomicInteger(0);
        Stream.of(ADMIN, USER, GUEST).forEach(user -> updateSignalValue(user, "userCounter", value.addAndGet(7))
                .accept(msg -> msg.satisfies(json -> assertUpdateReceived(json, value.get()))));
        updateSignalValueNotAuthenticated(ANONYMOUS, "userCounter", 70).accept(assertAccessDenied("userCounter"));
    }

    private Consumer<Consumer<AbstractStringAssert<?>>> subscribeSignal(TestUtils.User user, String signal) {
        return asserter -> {
            doWithClient(user, signal, (client, unused) -> client.assertMessageReceived(1, TimeUnit.SECONDS, asserter));
        };
    }

    private Consumer<Consumer<AbstractStringAssert<?>>> updateSignalValue(
            TestUtils.User user, String signal, int newValue) {
        return updateSignalValue(user, signal, newValue, 200);
    }

    private Consumer<Consumer<AbstractStringAssert<?>>> updateSignalValueNotAuthenticated(
            TestUtils.User user, String signal, int newValue) {
        return updateSignalValue(user, signal, newValue, 401);
    }

    private Consumer<Consumer<AbstractStringAssert<?>>> updateSignalValueNotAllowed(
            TestUtils.User user, String signal, int newValue) {
        return updateSignalValue(user, signal, newValue, 403);
    }

    private Consumer<Consumer<AbstractStringAssert<?>>> updateSignalValue(
            TestUtils.User user, String signal, int newValue, int expectedHttpStatusCode) {
        return asserter -> {
            // Create shared signal
            doWithClient(ADMIN, signal, (client, clientSignalId) -> {
                client.pollMessage(1, TimeUnit.SECONDS);

                // Update with a different user
                givenEndpointRequest(
                                SignalsHandler.class.getSimpleName(),
                                "update",
                                TestUtils.Parameters.param("clientSignalId", clientSignalId)
                                        .add(
                                                "command",
                                                TestUtils.Parameters.param(
                                                                "commandId",
                                                                Id.random().asBase64())
                                                        .add("@type", "set")
                                                        .add("targetNodeId", "")
                                                        .add("value", newValue)),
                                authenticate(user))
                        .then()
                        .assertThat()
                        .statusCode(expectedHttpStatusCode);
                if (expectedHttpStatusCode == 200) {
                    client.assertMessageReceived(1, TimeUnit.SECONDS, asserter);
                }
            });
        };
    }

    private interface Operation {
        void accept(HillaPushClient client, String clientSignalId) throws Exception;
    }

    private void doWithClient(TestUtils.User user, String methodName, Operation consumer) {
        URI connectURI = HillaPushClient.createPUSHConnectURI(pushURI);
        String clientSignalId = UUID.randomUUID().toString();
        HillaPushClient client = new HillaPushClient(
                "SignalsHandler",
                "subscribe",
                SecureNumberSignalService.class.getSimpleName(),
                methodName,
                clientSignalId,
                null);
        ClientEndpointConfig cec = ClientEndpointConfig.Builder.create()
                .configurator(new BasicAuthConfigurator(user))
                .build();
        try (Session ignored = ContainerProvider.getWebSocketContainer().connectToServer(client, cec, connectURI)) {
            client.assertMessageReceived(10, TimeUnit.SECONDS, "CONNECT");
            consumer.accept(client, clientSignalId);
        } catch (Exception e) {
            Assertions.fail("PUSH communication failed", e);
        }
    }

    private Consumer<AbstractStringAssert<?>> assertAccessDenied(String method) {
        return msg -> {
            msg.satisfies(jsonStr -> {
                try (var reader = Json.createReader(new StringReader(jsonStr))) {
                    JsonObject json = reader.readObject();
                    assertThat(json.getString("@type")).isEqualTo("error");
                    assertThat(json.getString("message")).isEqualTo("Exception in Flux");
                }
            });
        };
    }

    private static void assertSnapshotReceived(String message, int expectedValue) {
        try (var reader = Json.createReader(new StringReader(message))) {
            JsonObject json = reader.readObject();
            assertThat(json.getString("@type")).isEqualTo("update");
            JsonObject item = json.getJsonObject("item");
            assertThat(item).isNotNull();
            assertThat(item.getString("@type")).isEqualTo("snapshot");
            assertThat(item.getString("commandId")).isNotEmpty();
            assertThat(item.getJsonObject("nodes")).containsKey("");
            JsonObject node = item.getJsonObject("nodes").getJsonObject("");
            assertThat(node).isNotNull();
            assertThat(node.getString("@type")).isEqualTo("d");
            assertThat(node.getInt("value")).isEqualTo(expectedValue);
        }
    }

    private static void assertUpdateReceived(String message, int expectedValue) {
        try (var reader = Json.createReader(new StringReader(message))) {
            JsonObject json = reader.readObject();
            assertThat(json.getString("@type")).isEqualTo("update");
            JsonObject item = json.getJsonObject("item");
            assertThat(item).isNotNull();
            assertThat(item.getString("@type")).isEqualTo("set");
            assertThat(item.getString("commandId")).isNotEmpty();
            assertThat(item.getString("targetNodeId")).isEmpty();
            assertThat(item.getInt("value")).isEqualTo(expectedValue);
        }
    }

    private static UnaryOperator<RequestSpecification> authenticate(TestUtils.User user) {
        if (user == ANONYMOUS) {
            return UnaryOperator.identity();
        }
        return spec -> spec.auth().preemptive().basic(user.username, user.pwd);
    }

    private static String testResource(String name) {
        return SignalsSecurityTest.class.getPackageName().replace('.', '/') + '/' + name;
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
