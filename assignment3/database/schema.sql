-- ============================================
-- CS6650 Assignment 3 - Database Schema
-- Database: PostgreSQL 16
-- Author: Dong Chen
-- Date: 2025-11-19
-- ============================================

-- ============================================
-- Cleanup (for fresh setup)
-- ============================================
DROP TABLE IF EXISTS messages CASCADE;
DROP MATERIALIZED VIEW IF EXISTS user_stats CASCADE;
DROP MATERIALIZED VIEW IF EXISTS room_stats CASCADE;
DROP MATERIALIZED VIEW IF EXISTS hourly_stats CASCADE;

-- ============================================
-- Main Messages Table
-- ============================================
CREATE TABLE messages (
    -- Primary key: unique message identifier
    message_id VARCHAR(64) PRIMARY KEY,

    -- Room information
    room_id INT NOT NULL,

    -- User information
    user_id VARCHAR(64) NOT NULL,

    -- Message content
    content TEXT NOT NULL,

    -- Message timestamp (from client)
    timestamp TIMESTAMPTZ NOT NULL,

    -- Database insert timestamp
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE messages IS 'Main table storing all chat messages';
COMMENT ON COLUMN messages.message_id IS 'Unique identifier for idempotent writes';
COMMENT ON COLUMN messages.timestamp IS 'Message timestamp from client';
COMMENT ON COLUMN messages.created_at IS 'Database insertion timestamp';

-- ============================================
-- Indexes for Core Queries
-- ============================================

-- Index 1: For Query "Get messages for room in time range"
-- Performance target: < 100ms for 1000 messages
-- Query pattern: WHERE room_id = ? AND timestamp BETWEEN ? AND ?
CREATE INDEX idx_messages_room_time
ON messages(room_id, timestamp DESC)
INCLUDE (message_id, user_id, content);

COMMENT ON INDEX idx_messages_room_time IS
'Covering index for room-based time range queries';

-- Index 2: For Query "Get user's message history"
-- Performance target: < 200ms
-- Query pattern: WHERE user_id = ? AND timestamp BETWEEN ? AND ?
CREATE INDEX idx_messages_user_time
ON messages(user_id, timestamp DESC)
INCLUDE (room_id, content);

COMMENT ON INDEX idx_messages_user_time IS
'Covering index for user message history queries';

-- Index 3: For Query "Count active users in time window"
-- Performance target: < 500ms
-- Query pattern: WHERE timestamp BETWEEN ? AND ?
-- Using BRIN index for timestamp (efficient for sequential inserts)
CREATE INDEX idx_messages_timestamp_brin
ON messages USING BRIN(timestamp)
WITH (pages_per_range = 128);

COMMENT ON INDEX idx_messages_timestamp_brin IS
'BRIN index for time-range queries (low storage overhead)';

-- Index 4: For Query "Get rooms user has participated in"
-- Performance target: < 50ms
-- Query pattern: WHERE user_id = ? GROUP BY room_id
CREATE INDEX idx_messages_user_room
ON messages(user_id, room_id, timestamp DESC);

COMMENT ON INDEX idx_messages_user_room IS
'Composite index for user participation queries';

-- ============================================
-- Materialized Views for Analytics
-- ============================================

-- Materialized View 1: User Statistics
CREATE MATERIALIZED VIEW user_stats AS
SELECT
    user_id,
    COUNT(*) as message_count,
    MIN(timestamp) as first_message,
    MAX(timestamp) as last_message,
    COUNT(DISTINCT room_id) as rooms_participated,
    NOW() as last_refreshed
FROM messages
GROUP BY user_id;

CREATE UNIQUE INDEX idx_user_stats_user_id ON user_stats(user_id);
CREATE INDEX idx_user_stats_count ON user_stats(message_count DESC);

COMMENT ON MATERIALIZED VIEW user_stats IS
'Aggregated statistics per user for analytics queries';

-- Materialized View 2: Room Statistics
CREATE MATERIALIZED VIEW room_stats AS
SELECT
    room_id,
    COUNT(*) as message_count,
    COUNT(DISTINCT user_id) as unique_users,
    MIN(timestamp) as first_message,
    MAX(timestamp) as last_activity,
    NOW() as last_refreshed
FROM messages
GROUP BY room_id;

CREATE UNIQUE INDEX idx_room_stats_room_id ON room_stats(room_id);
CREATE INDEX idx_room_stats_count ON room_stats(message_count DESC);
CREATE INDEX idx_room_stats_activity ON room_stats(last_activity DESC);

COMMENT ON MATERIALIZED VIEW room_stats IS
'Aggregated statistics per room for analytics queries';

-- Materialized View 3: Hourly Statistics
CREATE MATERIALIZED VIEW hourly_stats AS
SELECT
    DATE_TRUNC('hour', timestamp) as hour,
    COUNT(*) as message_count,
    COUNT(DISTINCT user_id) as unique_users,
    COUNT(DISTINCT room_id) as active_rooms,
    NOW() as last_refreshed
FROM messages
GROUP BY DATE_TRUNC('hour', timestamp)
ORDER BY hour;

CREATE UNIQUE INDEX idx_hourly_stats_hour ON hourly_stats(hour DESC);

COMMENT ON MATERIALIZED VIEW hourly_stats IS
'Hourly aggregated statistics for time-series analytics';

-- ============================================
-- Helper Functions
-- ============================================

-- Function to refresh all materialized views
CREATE OR REPLACE FUNCTION refresh_all_stats()
RETURNS TABLE(
    view_name TEXT,
    refresh_time INTERVAL
) AS $$
DECLARE
    start_time TIMESTAMPTZ;
    end_time TIMESTAMPTZ;
BEGIN
    -- Refresh user_stats
    start_time := clock_timestamp();
    REFRESH MATERIALIZED VIEW CONCURRENTLY user_stats;
    end_time := clock_timestamp();
    view_name := 'user_stats';
    refresh_time := end_time - start_time;
    RETURN NEXT;

    -- Refresh room_stats
    start_time := clock_timestamp();
    REFRESH MATERIALIZED VIEW CONCURRENTLY room_stats;
    end_time := clock_timestamp();
    view_name := 'room_stats';
    refresh_time := end_time - start_time;
    RETURN NEXT;

    -- Refresh hourly_stats
    start_time := clock_timestamp();
    REFRESH MATERIALIZED VIEW CONCURRENTLY hourly_stats;
    end_time := clock_timestamp();
    view_name := 'hourly_stats';
    refresh_time := end_time - start_time;
    RETURN NEXT;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION refresh_all_stats() IS
'Refresh all materialized views and return timing information';

-- ============================================
-- Performance Tuning
-- ============================================

-- Update table statistics for query planner
ANALYZE messages;

-- ============================================
-- Verification Queries
-- ============================================

-- Check table size
SELECT
    schemaname,
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size
FROM pg_tables
WHERE tablename = 'messages';

-- Check index sizes
SELECT
    indexname,
    pg_size_pretty(pg_relation_size(indexname::regclass)) AS size
FROM pg_indexes
WHERE tablename = 'messages'
ORDER BY pg_relation_size(indexname::regclass) DESC;

-- Check materialized view sizes
SELECT
    schemaname,
    matviewname,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||matviewname)) AS size
FROM pg_matviews;
