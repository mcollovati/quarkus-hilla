package org.acme.hilla.test.extension.deployment;

import javax.enterprise.context.control.ActivateRequestContext;
import javax.websocket.ContainerProvider;
import javax.websocket.Session;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.acme.hilla.test.extension.deployment.endpoints.ReactiveEndpoint;
import org.hamcrest.Matchers;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ReactiveEndpointTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ReactiveEndpoint.class, HillaPushClient.class)
                    .add(new StringAsset(
                            "com.vaadin.experimental.hillaPush=true"),
                            "vaadin-featureflags.properties"));

    @TestHTTPResource("/HILLA/push")
    URI uri;

    @Test
    @TestSecurity(authorizationEnabled = false)
    @ActivateRequestContext
    void testWebsocketReactiveEndpoint() throws Exception {
        URI connectURI = createPUSHConnectURI();
        String counterName = UUID.randomUUID().toString();
        HillaPushClient client = new HillaPushClient("ReactiveEndpoint",
                "count", counterName);
        try (Session session = ContainerProvider.getWebSocketContainer()
                .connectToServer(client, null, connectURI)) {
            String message = client.messages.poll(10, TimeUnit.SECONDS);
            Assertions.assertEquals("CONNECT", message);
            for (int i = 1; i < 10; i++) {
                String expectedMessage = String.format(
                        "{\"@type\":\"update\",\"id\":\"%s\",\"item\":%s}",
                        client.id, i);
                message = client.messages.poll(1, TimeUnit.SECONDS);
                Assertions.assertNotNull(message,
                        "Expecting message " + i + " but got null");
                Assertions.assertEquals(expectedMessage,
                        message.replaceFirst("\\d+\\|", ""));
            }
        }
        String message = client.messages.poll(1, TimeUnit.SECONDS);
        Assertions.assertNotNull(message,
                "Expecting CLOSE message but got null");
        Assertions.assertTrue(message.startsWith("CLOSED: "),
                "Expecting CLOSED message but got " + message);

        assertCounterValue(counterName, 9);
    }

    @Test
    @TestSecurity(authorizationEnabled = false)
    @ActivateRequestContext
    void testWebsocketCancelReactiveEndpoint() throws Exception {
        URI connectURI = createPUSHConnectURI();
        String counterName = UUID.randomUUID().toString();
        HillaPushClient client = new HillaPushClient("ReactiveEndpoint",
                "cancelableCount", counterName);
        try (Session session = ContainerProvider.getWebSocketContainer()
                .connectToServer(client, null, connectURI)) {
            String message = client.messages.poll(10, TimeUnit.SECONDS);
            Assertions.assertEquals("CONNECT", message);
            for (int i = 1; i < 10; i++) {
                String expectedMessage = String.format(
                        "{\"@type\":\"update\",\"id\":\"%s\",\"item\":%s}",
                        client.id, i);
                message = client.messages.poll(1, TimeUnit.SECONDS);
                Assertions.assertNotNull(message,
                        "Expecting message " + i + " but got null");
                Assertions.assertEquals(expectedMessage,
                        message.replaceFirst("\\d+\\|", ""));
            }
            client.cancel();
            assertCounterValue(counterName, -1);
        }
        String message = client.messages.poll(1, TimeUnit.SECONDS);
        Assertions.assertNotNull(message,
                "Expecting CLOSE message but got null");
        Assertions.assertTrue(message.startsWith("CLOSED: "),
                "Expecting CLOSED message but got " + message);
    }

    @Test
    @TestSecurity(authorizationEnabled = false)
    @ActivateRequestContext
    void testWebsocketDisconnectReactiveEndpoint() throws Exception {
        URI connectURI = createPUSHConnectURI();
        String counterName = UUID.randomUUID().toString();
        HillaPushClient client = new HillaPushClient("ReactiveEndpoint",
                "cancelableCount", counterName);
        try (Session session = ContainerProvider.getWebSocketContainer()
                .connectToServer(client, null, connectURI)) {
            String message = client.messages.poll(10, TimeUnit.SECONDS);
            Assertions.assertEquals("CONNECT", message);
            for (int i = 1; i < 10; i++) {
                String expectedMessage = String.format(
                        "{\"@type\":\"update\",\"id\":\"%s\",\"item\":%s}",
                        client.id, i);
                message = client.messages.poll(1, TimeUnit.SECONDS);
                Assertions.assertNotNull(message,
                        "Expecting message " + i + " but got null");
                Assertions.assertEquals(expectedMessage,
                        message.replaceFirst("\\d+\\|", ""));
            }
        }
        String message = client.messages.poll(1, TimeUnit.SECONDS);
        Assertions.assertNotNull(message,
                "Expecting CLOSE message but got null");
        Assertions.assertTrue(message.startsWith("CLOSED: "),
                "Expecting CLOSED message but got " + message);

        assertCounterValue(counterName, -1);

    }


    @Test
    @TestSecurity(authorizationEnabled = false)
    @ActivateRequestContext
    void testWebsocketSubscribeAfterCancel() throws Exception {
        URI connectURI = createPUSHConnectURI();
        String counterName = UUID.randomUUID().toString();
        HillaPushClient client = new HillaPushClient("ReactiveEndpoint",
                "cancelableCount", counterName);
        try (Session session = ContainerProvider.getWebSocketContainer()
                .connectToServer(client, null, connectURI)) {
            String message = client.messages.poll(10, TimeUnit.SECONDS);
            Assertions.assertEquals("CONNECT", message);
            for (int i = 1; i < 5; i++) {
                String expectedMessage = String.format(
                        "{\"@type\":\"update\",\"id\":\"%s\",\"item\":%s}",
                        client.id, i);
                message = client.messages.poll(1, TimeUnit.SECONDS);
                Assertions.assertNotNull(message,
                        "Expecting message " + i + " but got null");
                Assertions.assertEquals(expectedMessage,
                        message.replaceFirst("\\d+\\|", ""));
            }
            client.cancel();
            assertCounterValue(counterName, -1);

            client.subscribe();
            for (int i = 0; i < 3; i++) {
                String expectedMessage = String.format(
                        "{\"@type\":\"update\",\"id\":\"%s\",\"item\":%s}",
                        client.id, i);
                message = client.messages.poll(1, TimeUnit.SECONDS);
                Assertions.assertNotNull(message,
                        "Expecting message " + i + " but got null");
                Assertions.assertEquals(expectedMessage,
                        message.replaceFirst("\\d+\\|", ""));
            }
            client.cancel();
            assertCounterValue(counterName, -1);

        }
        String message = client.messages.poll(1, TimeUnit.SECONDS);
        Assertions.assertNotNull(message,
                "Expecting CLOSE message but got null");
        Assertions.assertTrue(message.startsWith("CLOSED: "),
                "Expecting CLOSED message but got " + message);
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

    private static void assertCounterValue(String counterName, int expected) {
        LinkedHashMap<String, Object> orderedParams = new LinkedHashMap<>();
        orderedParams.put("counterName", counterName);
        RestAssured.given().contentType(ContentType.JSON)
                .cookie("csrfToken", "CSRF_TOKEN")
                .header("X-CSRF-Token", "CSRF_TOKEN").body(orderedParams)
                .basePath("/connect").when()
                .post("/ReactiveEndpoint/counterValue").then()
                .body(Matchers.equalTo(Integer.toString(expected)));
    }

}
