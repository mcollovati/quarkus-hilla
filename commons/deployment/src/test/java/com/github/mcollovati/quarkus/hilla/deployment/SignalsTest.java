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
import com.vaadin.signals.Id;
import com.vaadin.signals.NumberSignal;
import io.quarkus.logging.Log;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.common.http.TestHTTPResource;
import org.hamcrest.CoreMatchers;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.mcollovati.quarkus.hilla.deployment.signals.NumberSignalService;

import static org.assertj.core.api.Assertions.assertThat;

import static com.github.mcollovati.quarkus.hilla.deployment.TestUtils.givenEndpointRequest;

class SignalsTest {

    @TestHTTPResource("/HILLA/push")
    URI pushURI;

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withConfigurationResource(testResource("test-application.properties"))
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .add(
                            new StringAsset(
                                    """
                    com.vaadin.experimental.fullstackSignals=true
                    com.vaadin.experimental.flowFullstackSignals=true
                    """),
                            "vaadin-featureflags.properties")
                    .addClasses(TestUtils.class, NumberSignalService.class, NumberSignal.class, HillaPushClient.class));

    @Test
    @ActivateRequestContext
    void subscribeSignal_signalWorking() throws Exception {
        URI connectURI = HillaPushClient.createPUSHConnectURI(pushURI);
        String clientSignalId = UUID.randomUUID().toString();
        HillaPushClient client = new HillaPushClient(
                "SignalsHandler", "subscribe", "NumberSignalService", "counter", clientSignalId, null);
        try (Session ignored = ContainerProvider.getWebSocketContainer().connectToServer(client, null, connectURI)) {
            assertThatClientIsConnected(client);
            String serverSignalId = assertUpdateReceived(client, 3);
            assertThatSignalHasValue(3);
        }
        assertThatConnectionHasBeenClosed(client);
    }

    @Test
    @ActivateRequestContext
    void updateSignal_signalWorking() throws Exception {
        URI connectURI = HillaPushClient.createPUSHConnectURI(pushURI);
        String clientSignalId = UUID.randomUUID().toString();
        HillaPushClient client = new HillaPushClient(
                "SignalsHandler", "subscribe", "NumberSignalService", "counter", clientSignalId, null);
        try (Session ignored = ContainerProvider.getWebSocketContainer().connectToServer(client, null, connectURI)) {
            assertThatClientIsConnected(client);
            AtomicReference<String> serverSignalId = new AtomicReference<>();
            client.assertJsonMessageReceived(3, TimeUnit.SECONDS, json -> {
                assertThat(json.getJsonObject("item").getString("commandId")).isNotNull();
                serverSignalId.set(json.getJsonObject("item").getString("commandId"));
            });
            assertThatSignalHasValue(3);

            updateSignalValue(clientSignalId, 12);
            assertUpdateReceived(client, 12);
            assertThatSignalHasValue(12);

            updateSignalValue(clientSignalId, 5);
            assertUpdateReceived(client, 5);
            assertThatSignalHasValue(5);
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
            assertThat(item.getString("commandId")).isNotEmpty();
            String type = item.getString("@type");
            assertThat(type).isNotNull();
            if ("snapshot".equals(type)) {
                assertThat(item.getJsonObject("nodes")).containsKey("");
                JsonObject node = item.getJsonObject("nodes").getJsonObject("");
                assertThat(node).isNotNull();
                assertThat(node.getString("@type")).isEqualTo("d");
                assertThat(node.getInt("value")).isEqualTo(expectedValue);
            } else if ("set".equals(type)) {
                assertThat(item.getString("targetNodeId")).isEmpty();
                assertThat(item.getInt("value")).isEqualTo(expectedValue);
            } else {
                Assertions.fail("Unexpected item type " + type);
            }
            serverSignalId.set(item.getString("commandId"));
        });
        return serverSignalId.get();
    }

    private void updateSignalValue(String clientSignalId, int newValue) {
        givenEndpointRequest(
                        SignalsHandler.class.getSimpleName(),
                        "update",
                        TestUtils.Parameters.param("clientSignalId", clientSignalId)
                                .add(
                                        "command",
                                        TestUtils.Parameters.param(
                                                        "commandId", Id.random().asBase64())
                                                .add("@type", "set")
                                                .add("targetNodeId", "")
                                                .add("value", newValue)))
                .then()
                .assertThat()
                .statusCode(200);
    }

    private void assertThatSignalHasValue(int expected) {
        givenEndpointRequest(NumberSignalService.class.getSimpleName(), "counterValue")
                .then()
                .assertThat()
                .statusCode(200)
                .and()
                .body(CoreMatchers.equalTo(Double.toString(expected)));
    }

    private static String testResource(String name) {
        return SignalsTest.class.getPackageName().replace('.', '/') + '/' + name;
    }
}
