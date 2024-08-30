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
import jakarta.json.JsonObject;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.Session;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.vaadin.hilla.signals.handler.SignalsHandler;
import io.quarkus.logging.Log;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.common.http.TestHTTPResource;
import org.hamcrest.CoreMatchers;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.mcollovati.quarkus.hilla.deployment.signals.NumberSignalService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;

import static com.github.mcollovati.quarkus.hilla.deployment.TestUtils.givenEndpointRequest;

class SignalsTest {

    @TestHTTPResource("/HILLA/push")
    URI pushURI;

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withConfigurationResource(testResource("test-application.properties"))
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(TestUtils.class, NumberSignalService.class, HillaPushClient.class));

    @Test
    @ActivateRequestContext
    void subscribeSignal_signalWorking() throws Exception {
        URI connectURI = HillaPushClient.createPUSHConnectURI(pushURI);
        String clientSignalId = UUID.randomUUID().toString();
        HillaPushClient client =
                new HillaPushClient("SignalsHandler", "subscribe", "NumberSignalService", "counter", clientSignalId);
        try (Session ignored = ContainerProvider.getWebSocketContainer().connectToServer(client, null, connectURI)) {
            assertThatClientIsConnected(client);
            String serverSignalId = assertUpdateReceived(client, 3);
            assertThatSignalHasValue(serverSignalId, 3);
        }
        assertThatConnectionHasBeenClosed(client);
    }

    @Test
    @ActivateRequestContext
    void updateSignal_signalWorking() throws Exception {
        URI connectURI = HillaPushClient.createPUSHConnectURI(pushURI);
        String clientSignalId = UUID.randomUUID().toString();
        HillaPushClient client =
                new HillaPushClient("SignalsHandler", "subscribe", "NumberSignalService", "counter", clientSignalId);
        try (Session ignored = ContainerProvider.getWebSocketContainer().connectToServer(client, null, connectURI)) {
            assertThatClientIsConnected(client);
            AtomicReference<String> serverSignalId = new AtomicReference<>();
            client.assertJsonMessageReceived(3, TimeUnit.SECONDS, json -> {
                assertThat(json.getJsonObject("item").getString("id")).isNotNull();
                serverSignalId.set(json.getJsonObject("item").getString("id"));
            });
            assertThatSignalHasValue(serverSignalId.get(), 3);

            updateSignalValue(clientSignalId, 12);
            assertUpdateReceived(client, 12);
            assertThatSignalHasValue(serverSignalId.get(), 12);

            updateSignalValue(clientSignalId, 5);
            assertUpdateReceived(client, 5);
            assertThatSignalHasValue(serverSignalId.get(), 5);
        }
        assertThatConnectionHasBeenClosed(client);
    }

    private void assertThatClientIsConnected(HillaPushClient client) throws InterruptedException {
        client.assertMessageReceived(10, TimeUnit.SECONDS, "CONNECT");
    }

    private void assertThatConnectionHasBeenClosed(HillaPushClient client) throws InterruptedException {
        client.assertMessageReceived(
                3, TimeUnit.SECONDS, message -> message.isNotNull().startsWith("CLOSED: "));
    }

    private static String assertUpdateReceived(HillaPushClient client, int expectedValue) throws InterruptedException {
        AtomicReference<String> serverSignalId = new AtomicReference<>();
        client.assertJsonMessageReceived(3, TimeUnit.SECONDS, json -> {
            if ("error".equals(json.getString("@type"))) {
                Log.errorf("Unexpected error response %s", json);
            }

            assertThat(json.getString("@type")).isEqualTo("update");
            assertThat(json.getString("id")).isNotEmpty();
            JsonObject item = json.getJsonObject("item");
            assertThat(item).isNotNull();
            assertThat(item.getString("id")).isNotEmpty();
            assertThat(item.getInt("value")).isEqualTo(expectedValue);
            serverSignalId.set(item.getString("id"));
        });
        return serverSignalId.get();
    }

    private void updateSignalValue(String clientSignalId, int newValue) {
        givenEndpointRequest(
                        SignalsHandler.class.getSimpleName(),
                        "update",
                        TestUtils.Parameters.param("clientSignalId", clientSignalId)
                                .add(
                                        "event",
                                        TestUtils.Parameters.param(
                                                        "id", UUID.randomUUID().toString())
                                                .add("type", "set")
                                                .add("value", newValue)))
                .then()
                .assertThat()
                .statusCode(200);
    }

    private void assertThatSignalHasValue(String serverSignalId, int expected) {
        givenEndpointRequest(NumberSignalService.class.getSimpleName(), "counter")
                .then()
                .assertThat()
                .statusCode(200)
                .and()
                .body("id", equalTo(serverSignalId))
                .body("value", CoreMatchers.equalTo((float) expected));
    }

    private static String testResource(String name) {
        return SignalsTest.class.getPackageName().replace('.', '/') + '/' + name;
    }
}
