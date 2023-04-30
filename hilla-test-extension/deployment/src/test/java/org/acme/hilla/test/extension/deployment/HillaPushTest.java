package org.acme.hilla.test.extension.deployment;

import javax.enterprise.context.control.ActivateRequestContext;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.ContainerProvider;
import javax.websocket.Session;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.security.TestSecurity;
import org.acme.hilla.test.extension.deployment.endpoints.ReactiveEndpoint;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class HillaPushTest {

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
    void testWebsocketChat() throws Exception {
        URI connectURI = createPUSHConnectURI();
        HillaPushClient client = new HillaPushClient("ReactiveEndpoint",
                "count", "TEST");
        ClientEndpointConfig cfg = ClientEndpointConfig.Builder.create()
                .build();
        cfg.getUserProperties().put("io.undertow.websocket.CONNECT_TIMEOUT",
                300);
        try (Session session = ContainerProvider.getWebSocketContainer()
                .connectToServer(client, cfg, connectURI)) {
            String message = client.messages.poll(10, TimeUnit.SECONDS);
            Assertions.assertEquals("CONNECT", message);
            for (int i = 1; i < 10; i++) {
                String expectedMessage = String.format(
                        "{\"@type\":\"update\",\"id\":\"%s\",\"item\":%s}",
                        client.id, i);
                message = client.messages.poll(3, TimeUnit.SECONDS);
                Assertions.assertNotNull(message,
                        "Expecting message " + i + " but got null");
                Assertions.assertEquals(expectedMessage,
                        message.replaceFirst("\\d+\\|", ""));
            }
            client.disconnect();
            message = client.messages.poll(2, TimeUnit.SECONDS);
            Assertions.assertTrue(message.startsWith("CLOSED: "),
                    "Expecting CLOSED message but got " + message);
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
