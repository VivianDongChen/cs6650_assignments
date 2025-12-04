package com.cs6650.chat.consumer.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Database connection pool manager using HikariCP.
 * Provides efficient connection pooling for PostgreSQL database.
 */
public class DatabaseConnectionPool {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConnectionPool.class);

    private static DatabaseConnectionPool instance;
    private final HikariDataSource dataSource;

    // Configuration constants
    private static final int MINIMUM_IDLE = 10;
    private static final int MAXIMUM_POOL_SIZE = 50;
    private static final long CONNECTION_TIMEOUT_MS = 30000;  // 30 seconds
    private static final long IDLE_TIMEOUT_MS = 600000;       // 10 minutes
    private static final long MAX_LIFETIME_MS = 1800000;      // 30 minutes
    private static final long LEAK_DETECTION_THRESHOLD_MS = 60000; // 1 minute

    /**
     * Private constructor to enforce singleton pattern.
     */
    private DatabaseConnectionPool(String jdbcUrl, String username, String password) {
        logger.info("Initializing HikariCP connection pool...");

        HikariConfig config = new HikariConfig();

        // Database connection settings
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");

        // Pool configuration
        config.setMinimumIdle(MINIMUM_IDLE);
        config.setMaximumPoolSize(MAXIMUM_POOL_SIZE);
        config.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
        config.setIdleTimeout(IDLE_TIMEOUT_MS);
        config.setMaxLifetime(MAX_LIFETIME_MS);
        config.setLeakDetectionThreshold(LEAK_DETECTION_THRESHOLD_MS);

        // Connection properties
        config.setAutoCommit(false); // Manual commit for batch operations
        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("ChatConsumerPool");

        // Performance optimization
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("reWriteBatchedInserts", "true"); // Important for batch performance

        // PostgreSQL specific optimizations
        config.addDataSourceProperty("tcpKeepAlive", "true");
        config.addDataSourceProperty("socketTimeout", "30");

        this.dataSource = new HikariDataSource(config);

        logger.info("HikariCP connection pool initialized successfully");
        logger.info("Pool configuration: minIdle={}, maxPoolSize={}, connectionTimeout={}ms",
                MINIMUM_IDLE, MAXIMUM_POOL_SIZE, CONNECTION_TIMEOUT_MS);
    }

    /**
     * Get singleton instance of DatabaseConnectionPool.
     */
    public static synchronized DatabaseConnectionPool getInstance(
            String jdbcUrl, String username, String password) {
        if (instance == null) {
            instance = new DatabaseConnectionPool(jdbcUrl, username, password);
        }
        return instance;
    }

    /**
     * Get a connection from the pool.
     */
    public Connection getConnection() throws SQLException {
        try {
            Connection conn = dataSource.getConnection();
            logger.debug("Connection acquired from pool. Active connections: {}",
                    dataSource.getHikariPoolMXBean().getActiveConnections());
            return conn;
        } catch (SQLException e) {
            logger.error("Failed to get connection from pool", e);
            throw e;
        }
    }

    /**
     * Get pool statistics.
     */
    public PoolStats getStats() {
        return new PoolStats(
                dataSource.getHikariPoolMXBean().getActiveConnections(),
                dataSource.getHikariPoolMXBean().getIdleConnections(),
                dataSource.getHikariPoolMXBean().getTotalConnections(),
                dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection()
        );
    }

    /**
     * Close the connection pool.
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            logger.info("Closing HikariCP connection pool...");
            dataSource.close();
            logger.info("Connection pool closed");
        }
    }

    /**
     * Pool statistics data class.
     */
    public static class PoolStats {
        public final int activeConnections;
        public final int idleConnections;
        public final int totalConnections;
        public final int threadsAwaiting;

        public PoolStats(int active, int idle, int total, int awaiting) {
            this.activeConnections = active;
            this.idleConnections = idle;
            this.totalConnections = total;
            this.threadsAwaiting = awaiting;
        }

        @Override
        public String toString() {
            return String.format("PoolStats[active=%d, idle=%d, total=%d, awaiting=%d]",
                    activeConnections, idleConnections, totalConnections, threadsAwaiting);
        }
    }
}
