package com.cs6650.chat.server.ws;

import com.cs6650.chat.server.config.ObjectMapperProvider;
import com.cs6650.chat.server.handler.EchoMessageHandler;
import com.cs6650.chat.server.model.ChatMessage;
import com.cs6650.chat.server.queue.MessagePublisher;
import com.cs6650.chat.server.validation.MessageValidator;
import com.cs6650.chat.server.validation.MessageValidator.ValidationResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WebSocket endpoint that validates messages and publishes them to RabbitMQ.
 * <p>
 * Processing flow (Assignment 2):
 * <ol>
 *   <li>{@link #onOpen(Session, EndpointConfig, String)} – register the session and remember its room.</li>
 *   <li>{@link #onMessage(Session, String)} – parse JSON to {@link ChatMessage}, validate it, and publish to RabbitMQ.</li>
 *   <li>{@link #onClose(Session)} – remove the session from the active list.</li>
 *   <li>{@link #onError(Session, Throwable)} – log unexpected errors and attempt to notify the client.</li>
 * </ol>
 * Messages are published to RabbitMQ and consumed by a separate consumer application for broadcasting.
 */
@ServerEndpoint("/chat/{roomId}")
public class ChatWebSocketEndpoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatWebSocketEndpoint.class);

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperProvider.get();
    private static final MessageValidator VALIDATOR = new MessageValidator();
    private static final EchoMessageHandler HANDLER = new EchoMessageHandler(OBJECT_MAPPER);

    // Tracks active sessions (keyed by session ID) mainly for debugging/future broadcast usage.
    private static final Map<String, Session> ACTIVE_SESSIONS = new ConcurrentHashMap<>();

    // Message publisher for RabbitMQ (singleton)
    private static MessagePublisher messagePublisher;

    static {
        try {
            messagePublisher = new MessagePublisher();
            LOGGER.info("MessagePublisher initialized successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize MessagePublisher", e);
            throw new RuntimeException("Failed to initialize MessagePublisher", e);
        }
    }

    @OnOpen
    public void onOpen(Session session, EndpointConfig config, @PathParam("roomId") String roomId) {
        // Persist the room so future events (close/error) know which room the session belongs to.
        session.getUserProperties().put("roomId", roomId);
        ACTIVE_SESSIONS.put(session.getId(), session);
        LOGGER.info("Session {} joined room {}", session.getId(), roomId);
    }

    @OnMessage
    public void onMessage(Session session, String payload) {
        // Capture receive time immediately for accurate server-side latency.
        Instant receivedAt = Instant.now();
        String roomId = (String) session.getUserProperties().get("roomId");

        try {
            ChatMessage message = OBJECT_MAPPER.readValue(payload, ChatMessage.class);
            ValidationResult validationResult = VALIDATOR.validate(message);
            if (!validationResult.isValid()) {
                session.getBasicRemote().sendText(HANDLER.buildValidationErrorResponse(validationResult).toString());
                LOGGER.debug("Validation failed for session {}: {}", session.getId(), validationResult.getErrors());
                return;
            }

            // Publish message to RabbitMQ instead of echoing back
            String clientIp = getClientIp(session);
            messagePublisher.publishMessage(message, roomId, clientIp);

            // Send acknowledgment back to client
            session.getBasicRemote().sendText(HANDLER.buildSuccessResponse(message, receivedAt).toString());

            LOGGER.debug("Message published to room {} from session {}", roomId, session.getId());
        } catch (JsonProcessingException ex) {
            LOGGER.warn("Failed to parse message from session {}", session.getId(), ex);
            sendJsonError(session, "Invalid JSON payload.");
        } catch (IOException ex) {
            LOGGER.error("Failed to send response to session {}", session.getId(), ex);
            closeSilently(session);
        }
    }

    /**
     * Extract client IP address from session.
     */
    private String getClientIp(Session session) {
        try {
            Map<String, Object> userProperties = session.getUserProperties();
            Object remoteAddr = userProperties.get("javax.websocket.endpoint.remoteAddress");
            if (remoteAddr != null) {
                return remoteAddr.toString();
            }
        } catch (Exception e) {
            LOGGER.debug("Could not extract client IP", e);
        }
        return "unknown";
    }

    @OnClose
    public void onClose(Session session) {
        ACTIVE_SESSIONS.remove(session.getId());
        LOGGER.info("Session {} closed", session.getId());
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        LOGGER.error("WebSocket error on session {}", session != null ? session.getId() : "unknown", throwable);
        if (session != null && session.isOpen()) {
            sendJsonError(session, "Internal server error.");
        }
    }

    private void sendJsonError(Session session, String message) {
        try {
            // Minimal JSON to avoid introducing extra dependencies for simple error reporting.
            session.getBasicRemote().sendText("{\"status\":\"error\",\"errors\":[\"" + message + "\"]}");
        } catch (IOException ex) {
            LOGGER.error("Failed to send error response to session {}", session.getId(), ex);
            closeSilently(session);
        }
    }

    private void closeSilently(Session session) {
        try {
            session.close();
        } catch (IOException suppressed) {
            LOGGER.debug("Ignoring close failure for session {}", session.getId(), suppressed);
        }
    }
}
