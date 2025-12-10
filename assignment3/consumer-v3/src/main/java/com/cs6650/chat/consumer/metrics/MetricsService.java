package com.cs6650.chat.consumer.metrics;

import com.cs6650.chat.consumer.database.DatabaseConnectionPool;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * Service for querying and returning metrics from the PostgreSQL database.
 * Implements core queries and analytics queries as specified in Assignment 3.
 * 
 * Thread-safe service that executes database queries. Can be wrapped by MetricsCacheDecorator
 * for distributed Redis caching layer (cache-aside pattern).
 */
public class MetricsService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetricsService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final DatabaseConnectionPool connectionPool;

    public MetricsService(DatabaseConnectionPool connectionPool) {
        this.connectionPool = connectionPool;
    }

    /**
     * Get all metrics as a JSON string.
     * Includes both core queries and analytics queries.
     */
    public String getMetricsJson() {
        Map<String, Object> metrics = new HashMap<>();

        try {
            // Core Queries
            Map<String, Object> coreQueries = new HashMap<>();
            coreQueries.put("totalMessages", getTotalMessages());
            coreQueries.put("messagesPerRoom", getMessagesPerRoom());
            coreQueries.put("userParticipation", getUserParticipation());

            // Additional Core Queries (Assignment 3 requirements)
            // Demo queries with sample parameters
            coreQueries.put("sampleRoomMessages", getRoomMessagesInTimeRange(1, null, null, 10));
            coreQueries.put("sampleUserHistory", getUserMessageHistory("user_0", null, null, 10));
            coreQueries.put("activeUsersLast24h", getActiveUsersInTimeWindow(
                new Timestamp(System.currentTimeMillis() - 24*60*60*1000),
                new Timestamp(System.currentTimeMillis())
            ));
            coreQueries.put("sampleUserRooms", getRoomsUserParticipatedIn("user_0"));

            metrics.put("coreQueries", coreQueries);

            // Analytics Queries
            Map<String, Object> analyticsQueries = new HashMap<>();
            analyticsQueries.put("topActiveUsers", getTopActiveUsers(10));
            analyticsQueries.put("topActiveRooms", getTopActiveRooms(10));
            analyticsQueries.put("hourlyMessageDistribution", getHourlyMessageDistribution());
            metrics.put("analyticsQueries", analyticsQueries);

            // Metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("timestamp", new Timestamp(System.currentTimeMillis()).toString());
            metadata.put("databaseConnectionPool", connectionPool.getStats());
            metrics.put("metadata", metadata);

            return MAPPER.writeValueAsString(metrics);
        } catch (SQLException e) {
            LOGGER.error("Error getting metrics", e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to get metrics: " + e.getMessage());
            try {
                return MAPPER.writeValueAsString(errorResponse);
            } catch (Exception jsonError) {
                return "{\"error\":\"Failed to serialize error response\"}";
            }
        } catch (Exception e) {
            LOGGER.error("Error serializing metrics to JSON", e);
            return "{\"error\":\"Failed to serialize metrics: " + e.getMessage() + "\"}";
        }
    }

    /**
     * Core Query 1: Total number of messages in the system.
     */
    private long getTotalMessages() throws SQLException {
        String sql = "SELECT COUNT(*) FROM messages";
        try (Connection conn = connectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0;
        }
    }

    /**
     * Core Query 2: Number of messages per room.
     * Returns a list of room_id and message count, ordered by room_id.
     */
    private List<Map<String, Object>> getMessagesPerRoom() throws SQLException {
        String sql = "SELECT room_id, COUNT(*) as message_count " +
                     "FROM messages " +
                     "GROUP BY room_id " +
                     "ORDER BY room_id";

        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = connectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("roomId", rs.getInt("room_id"));
                row.put("messageCount", rs.getLong("message_count"));
                results.add(row);
            }
        }
        return results;
    }

    /**
     * Core Query 3: User participation in each room.
     * Returns which users have posted in which rooms.
     */
    private List<Map<String, Object>> getUserParticipation() throws SQLException {
        String sql = "SELECT room_id, user_id, COUNT(*) as message_count " +
                     "FROM messages " +
                     "GROUP BY room_id, user_id " +
                     "ORDER BY room_id, user_id";

        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = connectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("roomId", rs.getInt("room_id"));
                row.put("userId", rs.getString("user_id"));
                row.put("messageCount", rs.getLong("message_count"));
                results.add(row);
            }
        }
        return results;
    }

    /**
     * Analytics Query 1: Top N most active users.
     * Uses materialized view for better performance.
     */
    private List<Map<String, Object>> getTopActiveUsers(int limit) throws SQLException {
        String sql = "SELECT user_id, message_count, rooms_participated, " +
                     "       first_message, last_message " +
                     "FROM user_stats " +
                     "ORDER BY message_count DESC " +
                     "LIMIT ?";

        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = connectionPool.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("userId", rs.getString("user_id"));
                    row.put("totalMessages", rs.getLong("message_count"));
                    row.put("roomsParticipated", rs.getInt("rooms_participated"));
                    row.put("firstMessage", rs.getTimestamp("first_message").toString());
                    row.put("lastMessage", rs.getTimestamp("last_message").toString());
                    results.add(row);
                }
            }
        }
        return results;
    }

    /**
     * Analytics Query 2: Top N most active rooms.
     * Uses materialized view for better performance.
     */
    private List<Map<String, Object>> getTopActiveRooms(int limit) throws SQLException {
        String sql = "SELECT room_id, message_count, unique_users, " +
                     "       first_message, last_activity " +
                     "FROM room_stats " +
                     "ORDER BY message_count DESC " +
                     "LIMIT ?";

        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = connectionPool.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("roomId", rs.getInt("room_id"));
                    row.put("totalMessages", rs.getLong("message_count"));
                    row.put("uniqueUsers", rs.getInt("unique_users"));
                    row.put("firstMessage", rs.getTimestamp("first_message").toString());
                    row.put("lastActivity", rs.getTimestamp("last_activity").toString());
                    results.add(row);
                }
            }
        }
        return results;
    }

    /**
     * Analytics Query 3: Hourly message distribution.
     * Shows message volume patterns by hour.
     */
    private List<Map<String, Object>> getHourlyMessageDistribution() throws SQLException {
        String sql = "SELECT hour, message_count, unique_users, active_rooms " +
                     "FROM hourly_stats " +
                     "ORDER BY hour DESC " +
                     "LIMIT 24";  // Last 24 hours

        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = connectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("hour", rs.getTimestamp("hour").toString());
                row.put("totalMessages", rs.getLong("message_count"));
                row.put("uniqueUsers", rs.getInt("unique_users"));
                row.put("activeRooms", rs.getInt("active_rooms"));
                results.add(row);
            }
        }
        return results;
    }

    // ============================================
    // Additional Core Queries (Assignment 3 Requirements)
    // ============================================

    /**
     * Core Query: Get messages for a room in time range.
     * Performance target: < 100ms for 1000 messages
     *
     * @param roomId Room identifier
     * @param startTime Start timestamp (null for beginning)
     * @param endTime End timestamp (null for now)
     * @param limit Maximum number of messages to return
     * @return List of messages in time range
     */
    private List<Map<String, Object>> getRoomMessagesInTimeRange(
            int roomId, Timestamp startTime, Timestamp endTime, int limit) throws SQLException {

        StringBuilder sql = new StringBuilder(
            "SELECT message_id, user_id, content, timestamp " +
            "FROM messages " +
            "WHERE room_id = ?"
        );

        boolean hasStartTime = (startTime != null);
        boolean hasEndTime = (endTime != null);

        if (hasStartTime) {
            sql.append(" AND timestamp >= ?");
        }
        if (hasEndTime) {
            sql.append(" AND timestamp <= ?");
        }

        sql.append(" ORDER BY timestamp DESC LIMIT ?");

        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = connectionPool.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            int paramIndex = 1;
            stmt.setInt(paramIndex++, roomId);
            if (hasStartTime) {
                stmt.setTimestamp(paramIndex++, startTime);
            }
            if (hasEndTime) {
                stmt.setTimestamp(paramIndex++, endTime);
            }
            stmt.setInt(paramIndex, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("messageId", rs.getString("message_id"));
                    row.put("userId", rs.getString("user_id"));
                    row.put("content", rs.getString("content"));
                    row.put("timestamp", rs.getTimestamp("timestamp").toString());
                    results.add(row);
                }
            }
        }
        return results;
    }

    /**
     * Core Query: Get user's message history.
     * Performance target: < 200ms
     *
     * @param userId User identifier
     * @param startTime Start timestamp (null for beginning)
     * @param endTime End timestamp (null for now)
     * @param limit Maximum number of messages to return
     * @return User's messages across all rooms
     */
    private List<Map<String, Object>> getUserMessageHistory(
            String userId, Timestamp startTime, Timestamp endTime, int limit) throws SQLException {

        StringBuilder sql = new StringBuilder(
            "SELECT message_id, room_id, content, timestamp " +
            "FROM messages " +
            "WHERE user_id = ?"
        );

        boolean hasStartTime = (startTime != null);
        boolean hasEndTime = (endTime != null);

        if (hasStartTime) {
            sql.append(" AND timestamp >= ?");
        }
        if (hasEndTime) {
            sql.append(" AND timestamp <= ?");
        }

        sql.append(" ORDER BY timestamp DESC LIMIT ?");

        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = connectionPool.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            int paramIndex = 1;
            stmt.setString(paramIndex++, userId);
            if (hasStartTime) {
                stmt.setTimestamp(paramIndex++, startTime);
            }
            if (hasEndTime) {
                stmt.setTimestamp(paramIndex++, endTime);
            }
            stmt.setInt(paramIndex, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("messageId", rs.getString("message_id"));
                    row.put("roomId", rs.getInt("room_id"));
                    row.put("content", rs.getString("content"));
                    row.put("timestamp", rs.getTimestamp("timestamp").toString());
                    results.add(row);
                }
            }
        }
        return results;
    }

    /**
     * Core Query: Count active users in time window.
     * Performance target: < 500ms
     *
     * @param startTime Window start time
     * @param endTime Window end time
     * @return Number of unique active users
     */
    private long getActiveUsersInTimeWindow(Timestamp startTime, Timestamp endTime) throws SQLException {
        String sql = "SELECT COUNT(DISTINCT user_id) " +
                     "FROM messages " +
                     "WHERE timestamp >= ? AND timestamp <= ?";

        try (Connection conn = connectionPool.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, startTime);
            stmt.setTimestamp(2, endTime);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
                return 0;
            }
        }
    }

    /**
     * Core Query: Get rooms user has participated in.
     * Performance target: < 50ms
     *
     * @param userId User identifier
     * @return List of rooms with last activity timestamp
     */
    private List<Map<String, Object>> getRoomsUserParticipatedIn(String userId) throws SQLException {
        String sql = "SELECT room_id, " +
                     "       COUNT(*) as message_count, " +
                     "       MAX(timestamp) as last_activity " +
                     "FROM messages " +
                     "WHERE user_id = ? " +
                     "GROUP BY room_id " +
                     "ORDER BY last_activity DESC";

        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = connectionPool.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("roomId", rs.getInt("room_id"));
                    row.put("messageCount", rs.getLong("message_count"));
                    row.put("lastActivity", rs.getTimestamp("last_activity").toString());
                    results.add(row);
                }
            }
        }
        return results;
    }

    /**
     * Refresh all materialized views to get latest stats.
     * Should be called before querying analytics if data is stale.
     */
    public void refreshMaterializedViews() throws SQLException {
        LOGGER.info("Refreshing materialized views...");
        try (Connection conn = connectionPool.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT refresh_all_stats()");
        }
        LOGGER.info("Materialized views refreshed");
    }
}
