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

import com.github.mcollovati.quarkus.hilla.deployment.endpoints.ReactiveEndpoint;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.common.http.TestHTTPResource;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.enterprise.context.control.ActivateRequestContext;
import javax.websocket.ContainerProvider;
import javax.websocket.Session;
import org.awaitility.Awaitility;
import org.hamcrest.Matchers;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ReactiveEndpointTest {
    private static final String ENDPOINT_NAME = ReactiveEndpoint.class.getSimpleName();

    private static final Logger LOGGER = LoggerFactory.getLogger(ReactiveEndpointTest.class);

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ReactiveEndpoint.class, HillaPushClient.class)
                    .add(new StringAsset("com.vaadin.experimental.hillaPush=true"), "vaadin-featureflags.properties"));

    @TestHTTPResource("/HILLA/push")
    URI uri;

    @Test
    @ActivateRequestContext
    void reactiveEndpoint_messagesPushedToTheClient() throws Exception {
        URI connectURI = HillaPushClient.createPUSHConnectURI(uri);
        String counterName = UUID.randomUUID().toString();
        HillaPushClient client = new HillaPushClient(ENDPOINT_NAME, "count", counterName);
        try (Session ignored = ContainerProvider.getWebSocketContainer().connectToServer(client, null, connectURI)) {
            assertThatClientIsConnected(client);
            for (int i = 1; i < 10; i++) {
                assertThatPushUpdateHasBeenReceived(client, i);
            }
        }
        assertThatConnectionHasBeenClosed(client);

        assertCounterValue(counterName, 9);
    }

    @Test
    @ActivateRequestContext
    void cancelableReactiveEndpoint_clientCancel_serverUnsubscribeCallBackInvoked() throws Exception {
        URI connectURI = HillaPushClient.createPUSHConnectURI(uri);
        String counterName = UUID.randomUUID().toString();
        HillaPushClient client = new HillaPushClient(ENDPOINT_NAME, "cancelableCount", counterName);
        try (Session ignored = ContainerProvider.getWebSocketContainer().connectToServer(client, null, connectURI)) {
            assertThatClientIsConnected(client);
            for (int i = 1; i < 10; i++) {
                assertThatPushUpdateHasBeenReceived(client, i);
            }
            client.cancel();
            assertCounterValue(counterName, -1);
        }
        assertThatConnectionHasBeenClosed(client);
    }

    @Test
    @ActivateRequestContext
    void cancelableReactiveEndpoint_clientDisconnectWithoutCancel_serverUnsubscribeCallBackInvoked() throws Exception {
        URI connectURI = HillaPushClient.createPUSHConnectURI(uri);
        String counterName = UUID.randomUUID().toString();
        HillaPushClient client = new HillaPushClient(ENDPOINT_NAME, "cancelableCount", counterName);
        LOGGER.info(
                "================== cancelableReactiveEndpoint_clientDisconnectWithoutCancel_serverUnsubscribeCallBackInvoked"
                        + " --> start client {}",
                client.id);
        try (Session ignored = ContainerProvider.getWebSocketContainer().connectToServer(client, null, connectURI)) {
            assertThatClientIsConnected(client);
            for (int i = 1; i < 10; i++) {
                assertThatPushUpdateHasBeenReceived(client, i);
            }
        }
        LOGGER.info(
                "================== cancelableReactiveEndpoint_clientDisconnectWithoutCancel_serverUnsubscribeCallBackInvoked"
                        + " --> client {} closed",
                client.id);
        assertThatConnectionHasBeenClosed(client);

        Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertCounterValue(counterName, -1));
    }

    @Test
    @ActivateRequestContext
    void cancelableReactiveEndpoint_subscribeAfterCancel_connectionNotClosedAndMessagesPushed() throws Exception {
        URI connectURI = HillaPushClient.createPUSHConnectURI(uri);
        String counterName = UUID.randomUUID().toString();
        HillaPushClient client = new HillaPushClient(ENDPOINT_NAME, "cancelableCount", counterName);
        try (Session ignored = ContainerProvider.getWebSocketContainer().connectToServer(client, null, connectURI)) {
            assertThatClientIsConnected(client);
            for (int i = 1; i < 5; i++) {
                assertThatPushUpdateHasBeenReceived(client, i);
            }
            client.cancel();
            assertCounterValue(counterName, -1);

            client.subscribe();
            for (int i = 0; i < 3; i++) {
                assertThatPushUpdateHasBeenReceived(client, i);
            }
            client.cancel();
            Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertCounterValue(counterName, -1));
        }
        assertThatConnectionHasBeenClosed(client);
    }

    private static void assertThatClientIsConnected(HillaPushClient client) throws InterruptedException {
        client.assertMessageReceived(10, TimeUnit.SECONDS, "CONNECT");
    }

    private static void assertThatConnectionHasBeenClosed(HillaPushClient client) throws InterruptedException {
        client.assertMessageReceived(
                1, TimeUnit.SECONDS, message -> message.isNotNull().startsWith("CLOSED: "));
    }

    private static void assertThatPushUpdateHasBeenReceived(HillaPushClient client, int i) throws InterruptedException {
        LOGGER.info("================== Asserting message {} for client {}", i, client.id);
        client.assertMessageReceived(1, TimeUnit.SECONDS, message -> message.as("Message %d", i)
                .isEqualTo("{\"@type\":\"update\",\"id\":\"%s\",\"item\":%s}", client.id, i));
    }

    private static void assertCounterValue(String counterName, int expected) {
        LinkedHashMap<String, Object> orderedParams = new LinkedHashMap<>();
        orderedParams.put("counterName", counterName);
        RestAssured.given()
                .contentType(ContentType.JSON)
                .cookie("csrfToken", "CSRF_TOKEN")
                .header("X-CSRF-Token", "CSRF_TOKEN")
                .body(orderedParams)
                .basePath("/connect")
                .when()
                .post("/{endpointName}/counterValue", ENDPOINT_NAME)
                .then()
                .body(Matchers.equalTo(Integer.toString(expected)));
    }
}
