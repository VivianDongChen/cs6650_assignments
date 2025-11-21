package com.cs6650.chat.consumer.broadcast;

import com.cs6650.chat.consumer.model.QueueMessage;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.Session;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages WebSocket sessions for different chat rooms.
 * Thread-safe implementation using concurrent collections.
 */
public class RoomManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomManager.class);

    // Map: roomId -> Set of WebSocket sessions
    private final Map<String, Set<Session>> roomSessions;

    // Map: sessionId -> roomId for quick lookup
    private final Map<String, String> sessionToRoom;

    // Deduplication cache: messageId -> timestamp
    // Messages expire after 5 minutes to prevent unbounded memory growth
    private final Cache<String, Long> processedMessages;

    // Metrics
    private final AtomicLong messagesProcessed = new AtomicLong(0);
    private final AtomicLong broadcastsSucceeded = new AtomicLong(0);
    private final AtomicLong broadcastsFailed = new AtomicLong(0);
    private final AtomicLong duplicatesDetected = new AtomicLong(0);

    public RoomManager() {
        this.roomSessions = new ConcurrentHashMap<>();
        this.sessionToRoom = new ConcurrentHashMap<>();

        // Initialize deduplication cache with 5-minute TTL
        this.processedMessages = Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(100000)  // Limit to 100k messages
                .build();

        // Pre-initialize rooms 1-20
        for (int i = 1; i <= 20; i++) {
            roomSessions.put(String.valueOf(i), new CopyOnWriteArraySet<>());
        }

        LOGGER.info("RoomManager initialized with 20 rooms and deduplication cache");
    }

    /**
     * Add a session to a room.
     */
    public void addSession(String roomId, Session session) {
        Set<Session> sessions = roomSessions.computeIfAbsent(roomId, k -> new CopyOnWriteArraySet<>());
        sessions.add(session);
        sessionToRoom.put(session.getId(), roomId);
        LOGGER.debug("Added session {} to room {}. Total sessions in room: {}",
                session.getId(), roomId, sessions.size());
    }

    /**
     * Remove a session from its room.
     */
    public void removeSession(Session session) {
        String roomId = sessionToRoom.remove(session.getId());
        if (roomId != null) {
            Set<Session> sessions = roomSessions.get(roomId);
            if (sessions != null) {
                sessions.remove(session);
                LOGGER.debug("Removed session {} from room {}. Remaining sessions: {}",
                        session.getId(), roomId, sessions.size());
            }
        }
    }

    /**
     * Broadcast a message to all sessions in a room.
     * Implements duplicate detection to ensure at-most-once delivery.
     */
    public void broadcastToRoom(QueueMessage message) {
        String messageId = message.getMessageId();
        String roomId = message.getRoomId();

        // Check for duplicate message
        Long previousTimestamp = processedMessages.getIfPresent(messageId);
        if (previousTimestamp != null) {
            duplicatesDetected.incrementAndGet();
            LOGGER.warn("Duplicate message detected: {} in room {} (previously processed at {}). Skipping broadcast.",
                    messageId, roomId, previousTimestamp);
            return;
        }

        // Mark message as processed
        processedMessages.put(messageId, System.currentTimeMillis());

        Set<Session> sessions = roomSessions.get(roomId);

        if (sessions == null || sessions.isEmpty()) {
            LOGGER.debug("No sessions in room {} to broadcast message {}", roomId, messageId);
            messagesProcessed.incrementAndGet();
            return;
        }

        int successCount = 0;
        int failCount = 0;

        // Convert message to JSON string
        String messageJson = convertToJson(message);

        for (Session session : sessions) {
            if (session.isOpen()) {
                try {
                    session.getBasicRemote().sendText(messageJson);
                    successCount++;
                } catch (Exception e) {
                    LOGGER.warn("Failed to send message to session {} in room {}: {}",
                            session.getId(), roomId, e.getMessage());
                    failCount++;
                    // Remove dead sessions
                    removeSession(session);
                }
            } else {
                // Remove closed sessions
                removeSession(session);
                failCount++;
            }
        }

        messagesProcessed.incrementAndGet();
        broadcastsSucceeded.addAndGet(successCount);
        broadcastsFailed.addAndGet(failCount);

        LOGGER.debug("Broadcasted message {} to room {}: {} succeeded, {} failed",
                messageId, roomId, successCount, failCount);
    }

    /**
     * Convert QueueMessage to JSON string.
     * In a real implementation, use Jackson ObjectMapper.
     */
    private String convertToJson(QueueMessage message) {
        // For simplicity, manually construct JSON
        // In production, use ObjectMapper
        return String.format(
                "{\"messageId\":\"%s\",\"roomId\":\"%s\",\"userId\":\"%s\",\"username\":\"%s\"," +
                        "\"message\":\"%s\",\"timestamp\":\"%s\",\"messageType\":\"%s\"," +
                        "\"serverId\":\"%s\",\"clientIp\":\"%s\"}",
                message.getMessageId(),
                message.getRoomId(),
                message.getUserId(),
                message.getUsername(),
                escapeJson(message.getMessage()),
                message.getTimestamp(),
                message.getMessageType(),
                message.getServerId(),
                message.getClientIp()
        );
    }

    /**
     * Escape JSON special characters.
     */
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Get statistics for a room.
     */
    public int getRoomSize(String roomId) {
        Set<Session> sessions = roomSessions.get(roomId);
        return sessions != null ? sessions.size() : 0;
    }

    /**
     * Get total number of active sessions across all rooms.
     */
    public int getTotalSessions() {
        return roomSessions.values().stream()
                .mapToInt(Set::size)
                .sum();
    }

    public long getMessagesProcessed() {
        return messagesProcessed.get();
    }

    public long getBroadcastsSucceeded() {
        return broadcastsSucceeded.get();
    }

    public long getBroadcastsFailed() {
        return broadcastsFailed.get();
    }

    public long getDuplicatesDetected() {
        return duplicatesDetected.get();
    }

    /**
     * Print statistics.
     */
    public void printStats() {
        LOGGER.info("=== RoomManager Statistics ===");
        LOGGER.info("Total sessions: {}", getTotalSessions());
        LOGGER.info("Messages processed: {}", messagesProcessed.get());
        LOGGER.info("Duplicates detected: {}", duplicatesDetected.get());
        LOGGER.info("Broadcasts succeeded: {}", broadcastsSucceeded.get());
        LOGGER.info("Broadcasts failed: {}", broadcastsFailed.get());
        LOGGER.info("Cache size: {}", processedMessages.estimatedSize());

        for (int i = 1; i <= 20; i++) {
            String roomId = String.valueOf(i);
            int size = getRoomSize(roomId);
            if (size > 0) {
                LOGGER.info("Room {}: {} sessions", roomId, size);
            }
        }
    }
}
