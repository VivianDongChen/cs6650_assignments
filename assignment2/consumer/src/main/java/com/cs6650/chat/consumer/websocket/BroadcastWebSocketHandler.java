package com.cs6650.chat.consumer.websocket;

import com.cs6650.chat.consumer.broadcast.RoomManager;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WebSocket handler for receiving connections from clients.
 * Clients connect here to receive broadcast messages from the Consumer.
 */
public class BroadcastWebSocketHandler extends WebSocketAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(BroadcastWebSocketHandler.class);

    private final RoomManager roomManager;
    private String roomId;

    public BroadcastWebSocketHandler(RoomManager roomManager) {
        this.roomManager = roomManager;
    }

    @Override
    public void onWebSocketConnect(Session session) {
        super.onWebSocketConnect(session);

        // Extract roomId from URI path: /broadcast/{roomId}
        String path = session.getUpgradeRequest().getRequestURI().getPath();
        String[] parts = path.split("/");
        if (parts.length >= 3) {
            this.roomId = parts[2];
        } else {
            this.roomId = "1"; // Default room
        }

        // Convert Jetty Session to javax.websocket.Session wrapper
        javax.websocket.Session javaxSession = new JettySessionAdapter(session);

        roomManager.addSession(roomId, javaxSession);
        LOGGER.info("Client connected to broadcast endpoint. Session: {}, Room: {}",
                session.hashCode(), roomId);
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason) {
        super.onWebSocketClose(statusCode, reason);

        if (getSession() != null) {
            javax.websocket.Session javaxSession = new JettySessionAdapter(getSession());
            roomManager.removeSession(javaxSession);
            LOGGER.info("Client disconnected from broadcast endpoint. Session: {}, Room: {}, Reason: {}",
                    getSession().hashCode(), roomId, reason);
        }
    }

    @Override
    public void onWebSocketError(Throwable cause) {
        super.onWebSocketError(cause);
        LOGGER.error("WebSocket error on broadcast endpoint. Session: {}, Room: {}",
                getSession() != null ? getSession().hashCode() : "null", roomId, cause);
    }

    @Override
    public void onWebSocketText(String message) {
        // Clients only receive messages on this endpoint, they don't send
        LOGGER.debug("Received unexpected message on broadcast endpoint from session {}: {}",
                getSession().hashCode(), message);
    }
}
