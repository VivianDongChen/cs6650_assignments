package com.cs6650.chat.consumer.websocket;

import org.eclipse.jetty.websocket.api.Session;

import javax.websocket.CloseReason;
import javax.websocket.Extension;
import javax.websocket.MessageHandler;
import javax.websocket.RemoteEndpoint;
import javax.websocket.WebSocketContainer;
import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adapter: wraps Jetty WebSocket Session as javax.websocket.Session.
 * RoomManager expects javax.websocket.Session, so we adapt Jetty sessions.
 */
public class JettySessionAdapter implements javax.websocket.Session {
    private final Session jettySession;

    public JettySessionAdapter(Session jettySession) {
        this.jettySession = jettySession;
    }

    @Override
    public String getId() {
        return String.valueOf(jettySession.hashCode());
    }

    @Override
    public boolean isOpen() {
        return jettySession.isOpen();
    }

    @Override
    public RemoteEndpoint.Basic getBasicRemote() {
        return new RemoteEndpoint.Basic() {
            @Override
            public void sendText(String text) throws IOException {
                if (jettySession.isOpen()) {
                    jettySession.getRemote().sendString(text);
                }
            }

            @Override
            public void sendBinary(java.nio.ByteBuffer data) throws IOException {
                if (jettySession.isOpen()) {
                    jettySession.getRemote().sendBytes(data);
                }
            }

            @Override
            public void sendText(String partialMessage, boolean isLast) throws IOException {
                sendText(partialMessage);
            }

            @Override
            public void sendBinary(java.nio.ByteBuffer partialByte, boolean isLast) throws IOException {
                sendBinary(partialByte);
            }

            @Override
            public void sendObject(Object data) throws IOException {
                sendText(data.toString());
            }

            @Override
            public void setBatchingAllowed(boolean allowed) throws IOException {
                // Not supported
            }

            @Override
            public boolean getBatchingAllowed() {
                return false;
            }

            @Override
            public void flushBatch() throws IOException {
                // Not supported
            }

            @Override
            public void sendPing(java.nio.ByteBuffer applicationData) throws IOException {
                // Not supported
            }

            @Override
            public void sendPong(java.nio.ByteBuffer applicationData) throws IOException {
                // Not supported
            }

            @Override
            public java.io.OutputStream getSendStream() throws IOException {
                throw new UnsupportedOperationException("getSendStream not supported");
            }

            @Override
            public java.io.Writer getSendWriter() throws IOException {
                throw new UnsupportedOperationException("getSendWriter not supported");
            }
        };
    }

    @Override
    public void close() throws IOException {
        jettySession.close();
    }

    @Override
    public void close(CloseReason closeReason) throws IOException {
        jettySession.close(closeReason.getCloseCode().getCode(), closeReason.getReasonPhrase());
    }

    // Remaining methods - minimal implementation for compatibility

    @Override
    public WebSocketContainer getContainer() {
        return null;
    }

    @Override
    public void addMessageHandler(MessageHandler handler) {
        // Not needed for broadcast-only endpoint
    }

    @Override
    public Set<MessageHandler> getMessageHandlers() {
        return Collections.emptySet();
    }

    @Override
    public void removeMessageHandler(MessageHandler handler) {
        // Not needed
    }

    @Override
    public String getProtocolVersion() {
        return "13";
    }

    @Override
    public String getNegotiatedSubprotocol() {
        return null;
    }

    @Override
    public List<Extension> getNegotiatedExtensions() {
        return Collections.emptyList();
    }

    @Override
    public boolean isSecure() {
        return jettySession.isSecure();
    }

    @Override
    public URI getRequestURI() {
        return jettySession.getUpgradeRequest().getRequestURI();
    }

    @Override
    public Map<String, List<String>> getRequestParameterMap() {
        return jettySession.getUpgradeRequest().getParameterMap();
    }

    @Override
    public String getQueryString() {
        return jettySession.getUpgradeRequest().getRequestURI().getQuery();
    }

    @Override
    public Map<String, String> getPathParameters() {
        return Collections.emptyMap();
    }

    @Override
    public Map<String, Object> getUserProperties() {
        return Collections.emptyMap();
    }

    @Override
    public Principal getUserPrincipal() {
        return null;
    }

    @Override
    public void setMaxIdleTimeout(long milliseconds) {
        jettySession.setIdleTimeout(java.time.Duration.ofMillis(milliseconds));
    }

    @Override
    public long getMaxIdleTimeout() {
        return jettySession.getIdleTimeout().toMillis();
    }

    @Override
    public void setMaxBinaryMessageBufferSize(int length) {
        jettySession.setMaxBinaryMessageSize(length);
    }

    @Override
    public int getMaxBinaryMessageBufferSize() {
        return (int) jettySession.getMaxBinaryMessageSize();
    }

    @Override
    public void setMaxTextMessageBufferSize(int length) {
        jettySession.setMaxTextMessageSize(length);
    }

    @Override
    public int getMaxTextMessageBufferSize() {
        return (int) jettySession.getMaxTextMessageSize();
    }

    @Override
    public RemoteEndpoint.Async getAsyncRemote() {
        throw new UnsupportedOperationException("Async remote not supported");
    }

    @Override
    public Set<javax.websocket.Session> getOpenSessions() {
        return Collections.emptySet();
    }

    @Override
    public <T> void addMessageHandler(Class<T> clazz, MessageHandler.Whole<T> handler) {
        // Not needed
    }

    @Override
    public <T> void addMessageHandler(Class<T> clazz, MessageHandler.Partial<T> handler) {
        // Not needed
    }
}
