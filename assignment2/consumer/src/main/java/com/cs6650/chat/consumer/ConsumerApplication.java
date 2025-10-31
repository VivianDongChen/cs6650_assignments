package com.cs6650.chat.consumer;

import com.cs6650.chat.consumer.broadcast.RoomManager;
import com.cs6650.chat.consumer.queue.MessageConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Main application for the chat message consumer.
 * Consumes messages from RabbitMQ and broadcasts them to WebSocket clients.
 */
public class ConsumerApplication {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConsumerApplication.class);

    private static final int STATS_INTERVAL_SECONDS = Integer.parseInt(
            System.getenv().getOrDefault("STATS_INTERVAL", "30"));

    public static void main(String[] args) {
        LOGGER.info("=== Starting CS6650 Chat Consumer Application ===");

        RoomManager roomManager = null;
        MessageConsumer messageConsumer = null;
        ScheduledExecutorService statsScheduler = null;

        try {
            // Initialize Room Manager
            roomManager = new RoomManager();

            // Initialize Message Consumer
            messageConsumer = new MessageConsumer(roomManager);

            // Start consuming messages
            messageConsumer.startConsuming();

            LOGGER.info("Consumer application started successfully");

            // Schedule periodic statistics reporting
            RoomManager finalRoomManager = roomManager;
            statsScheduler = Executors.newScheduledThreadPool(1);
            statsScheduler.scheduleAtFixedRate(
                    () -> finalRoomManager.printStats(),
                    STATS_INTERVAL_SECONDS,
                    STATS_INTERVAL_SECONDS,
                    TimeUnit.SECONDS
            );

            // Add shutdown hook
            MessageConsumer finalMessageConsumer = messageConsumer;
            ScheduledExecutorService finalStatsScheduler = statsScheduler;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOGGER.info("Shutdown signal received");
                if (finalStatsScheduler != null) {
                    finalStatsScheduler.shutdown();
                }
                if (finalMessageConsumer != null) {
                    finalMessageConsumer.shutdown();
                }
                if (finalRoomManager != null) {
                    finalRoomManager.printStats();
                }
                LOGGER.info("Consumer application stopped");
            }));

            // Keep the application running
            LOGGER.info("Consumer application is running. Press Ctrl+C to stop.");
            Thread.currentThread().join();

        } catch (Exception e) {
            LOGGER.error("Fatal error in consumer application", e);
            System.exit(1);
        }
    }
}
