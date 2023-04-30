package org.acme.hilla.test.extension.deployment;

import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.LogManager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HillaPushClient extends Endpoint
        implements MessageHandler.Whole<String> {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(HillaPushClient.class);
    private static final AtomicInteger CLIENT_ID_GEN = new AtomicInteger();

    final LinkedBlockingDeque<String> messages = new LinkedBlockingDeque<>();

    final String id;
    private final String endpointName;
    private final String methodName;
    private final List<Object> parameters = new ArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Session session;

    public HillaPushClient(String endpointName, String methodName,
            Object... parameters) {
        this.id = Integer.toString(CLIENT_ID_GEN.getAndIncrement());
        this.endpointName = endpointName;
        this.methodName = methodName;
        this.parameters.addAll(Arrays.asList(parameters));
    }

    @Override
    public void onOpen(Session session, EndpointConfig config) {
        LOGGER.trace("Client {} connected", id);
        this.session = session;
        messages.add("CONNECT");
        session.addMessageHandler(this);
        session.getAsyncRemote().sendText(createSubscribeMessage());
    }

    @Override
    public void onClose(Session session, CloseReason closeReason) {
        LOGGER.trace("Session closed for client {} with reson {}", id,
                closeReason);
        messages.add("CLOSED: " + closeReason.toString());
        session.removeMessageHandler(this);
        this.session = null;
    }

    @Override
    public void onError(Session session, Throwable throwable) {
        LOGGER.trace("Got error for client {}", id, throwable);
        messages.add("ERROR: " + throwable.getMessage());
    }

    public void onMessage(String msg) {
        if (msg != null && !msg.isBlank()) {
            LOGGER.trace("Message received for client {} :: {}", id, msg);
            messages.add(msg);
        } else {
            LOGGER.trace("Ignored empty message for client {} :: {}", id, msg);
        }
    }

    public void cancel() {
        LOGGER.trace("Canceling subscription for client {}", id);
        if (session != null) {
            try {
                session.getBasicRemote().sendText(createUnsubscribeMessage());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else {
            throw new IllegalStateException("Not connected");
        }
    }

    public void subscribe() {
        LOGGER.trace("Subscribing client {} :: {}/{} ({})", id, endpointName,
                methodName, parameters);
        if (session != null) {
            try {
                session.getBasicRemote().sendText(createSubscribeMessage());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else {
            throw new IllegalStateException("Not connected");
        }
    }

    public void disconnect() {
        LOGGER.trace("Disconnecting client {}", id);
        if (session != null) {
            try {
                session.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else {
            throw new IllegalStateException("Not connected");
        }
    }

    private String createSubscribeMessage() {
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("@type", "subscribe");
        params.put("id", id);
        params.put("endpointName", endpointName);
        params.put("methodName", methodName);
        params.put("params", this.parameters);
        try {
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private String createUnsubscribeMessage() {
        ObjectNode params = objectMapper.createObjectNode()
                .put("@type", "unsubscribe").put("id", id);
        try {
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
