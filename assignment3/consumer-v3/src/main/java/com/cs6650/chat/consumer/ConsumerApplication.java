package com.cs6650.chat.consumer;

import com.cs6650.chat.consumer.broadcast.RoomManager;
import com.cs6650.chat.consumer.database.BatchMessageWriter;
import com.cs6650.chat.consumer.database.DatabaseConnectionPool;
import com.cs6650.chat.consumer.health.HealthServer;
import com.cs6650.chat.consumer.queue.MessageConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Main application for the chat message consumer - Version 3 with PostgreSQL persistence.
 * Consumes messages from RabbitMQ, writes to database, and broadcasts to WebSocket clients.
 */
public class ConsumerApplication {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConsumerApplication.class);

    // Database configuration
    private static final String DB_JDBC_URL = System.getenv().getOrDefault(
            "DB_JDBC_URL",
            "jdbc:postgresql://cs6650-chat-db.cr6q6mmc0zok.us-west-2.rds.amazonaws.com:5432/chatdb"
    );
    private static final String DB_USERNAME = System.getenv().getOrDefault("DB_USERNAME", "postgres");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "MyPassword123");

    // Batch writer configuration
    private static final int BATCH_SIZE = Integer.parseInt(System.getenv().getOrDefault("BATCH_SIZE", "1000"));
    private static final long FLUSH_INTERVAL_MS = Long.parseLong(System.getenv().getOrDefault("FLUSH_INTERVAL_MS", "500"));

    private static final int STATS_INTERVAL_SECONDS = Integer.parseInt(
            System.getenv().getOrDefault("STATS_INTERVAL", "30"));

    public static void main(String[] args) {
        LOGGER.info("=== Starting CS6650 Chat Consumer Application v3 (with PostgreSQL) ===");
        LOGGER.info("Database URL: {}", DB_JDBC_URL);
        LOGGER.info("Batch configuration: size={}, flushInterval={}ms", BATCH_SIZE, FLUSH_INTERVAL_MS);

        DatabaseConnectionPool connectionPool = null;
        BatchMessageWriter batchWriter = null;
        RoomManager roomManager = null;
        MessageConsumer messageConsumer = null;
        HealthServer healthServer = null;
        ScheduledExecutorService statsScheduler = null;
        ScheduledExecutorService cacheScheduler = null;

        try {
            // Initialize Database Connection Pool
            LOGGER.info("Initializing database connection pool...");
            connectionPool = DatabaseConnectionPool.getInstance(DB_JDBC_URL, DB_USERNAME, DB_PASSWORD);
            LOGGER.info("Database connection pool initialized: {}", connectionPool.getStats());

            // Initialize Batch Message Writer
            LOGGER.info("Initializing batch message writer...");
            batchWriter = new BatchMessageWriter(connectionPool, BATCH_SIZE, FLUSH_INTERVAL_MS);
            batchWriter.start();
            LOGGER.info("Batch message writer started");

            // Initialize Room Manager
            roomManager = new RoomManager();

            // Initialize Message Consumer (with database writer)
            messageConsumer = new MessageConsumer(roomManager, batchWriter);

            // Start consuming messages
            messageConsumer.startConsuming();

            // Start health check server with WebSocket broadcast endpoint and metrics API
            healthServer = new HealthServer(messageConsumer, roomManager, connectionPool);
            healthServer.start();

            LOGGER.info("Consumer application started successfully");

            // Schedule periodic statistics reporting
            RoomManager finalRoomManager = roomManager;
            BatchMessageWriter finalBatchWriter = batchWriter;
            DatabaseConnectionPool finalConnectionPool = connectionPool;
            statsScheduler = Executors.newScheduledThreadPool(1);
            statsScheduler.scheduleAtFixedRate(
                    () -> {
                        LOGGER.info("=== Consumer Statistics ===");
                        finalRoomManager.printStats();
                        LOGGER.info("Database Writer: {}", finalBatchWriter.getStats());
                        LOGGER.info("Connection Pool: {}", finalConnectionPool.getStats());
                    },
                    STATS_INTERVAL_SECONDS,
                    STATS_INTERVAL_SECONDS,
                    TimeUnit.SECONDS
            );

            // Schedule cache performance logging every 10 seconds
            cacheScheduler = Executors.newScheduledThreadPool(1);
            cacheScheduler.scheduleAtFixedRate(() -> finalRoomManager.logCachePerformance(), 0, 10, TimeUnit.SECONDS);

            // Add shutdown hook
            MessageConsumer finalMessageConsumer = messageConsumer;
            HealthServer finalHealthServer = healthServer;
            ScheduledExecutorService finalStatsScheduler = statsScheduler;
            ScheduledExecutorService finalCacheScheduler = cacheScheduler;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOGGER.info("Shutdown signal received");

                // Shutdown stats scheduler
                if (finalStatsScheduler != null) {
                    finalStatsScheduler.shutdown();
                }

                // Shutdown cache scheduler
                if (finalCacheScheduler != null) {
                    finalCacheScheduler.shutdown();
                }

                // Stop health server
                if (finalHealthServer != null) {
                    try {
                        finalHealthServer.stop();
                    } catch (Exception e) {
                        LOGGER.error("Error stopping health server", e);
                    }
                }

                // Shutdown message consumer
                if (finalMessageConsumer != null) {
                    finalMessageConsumer.shutdown();
                }

                // Shutdown batch writer (flush remaining messages)
                if (finalBatchWriter != null) {
                    LOGGER.info("Shutting down batch writer...");
                    finalBatchWriter.shutdown();
                }

                // Close database connection pool
                if (finalConnectionPool != null) {
                    LOGGER.info("Closing database connection pool...");
                    finalConnectionPool.close();
                }

                // Print final statistics
                if (finalRoomManager != null) {
                    finalRoomManager.printStats();
                }
                if (finalBatchWriter != null) {
                    LOGGER.info("Final database writer stats: {}", finalBatchWriter.getStats());
                }

                LOGGER.info("Consumer application stopped");
            }));

            // Keep the application running
            LOGGER.info("Consumer application is running. Press Ctrl+C to stop.");
            Thread.currentThread().join();

        } catch (Exception e) {
            LOGGER.error("Fatal error in consumer application", e);

            // Cleanup on error
            if (batchWriter != null) {
                batchWriter.shutdown();
            }
            if (connectionPool != null) {
                connectionPool.close();
            }

            System.exit(1);
        }
    }
}
