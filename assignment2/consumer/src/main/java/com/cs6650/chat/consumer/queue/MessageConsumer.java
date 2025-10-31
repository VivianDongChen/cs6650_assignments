package com.cs6650.chat.consumer.queue;

import com.cs6650.chat.consumer.broadcast.RoomManager;
import com.cs6650.chat.consumer.model.QueueMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rabbitmq.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Multi-threaded RabbitMQ consumer that processes messages and broadcasts to rooms.
 */
public class MessageConsumer {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageConsumer.class);

    private static final String RABBITMQ_HOST = System.getenv().getOrDefault("RABBITMQ_HOST", "localhost");
    private static final int RABBITMQ_PORT = Integer.parseInt(System.getenv().getOrDefault("RABBITMQ_PORT", "5672"));
    private static final String RABBITMQ_USERNAME = System.getenv().getOrDefault("RABBITMQ_USERNAME", "guest");
    private static final String RABBITMQ_PASSWORD = System.getenv().getOrDefault("RABBITMQ_PASSWORD", "guest");
    private static final int CONSUMER_THREADS = Integer.parseInt(System.getenv().getOrDefault("CONSUMER_THREADS", "40"));
    private static final int PREFETCH_COUNT = Integer.parseInt(System.getenv().getOrDefault("PREFETCH_COUNT", "10"));

    private final Connection connection;
    private final RoomManager roomManager;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;
    private final List<Channel> channels;

    public MessageConsumer(RoomManager roomManager) throws IOException, TimeoutException {
        this.roomManager = roomManager;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.channels = new ArrayList<>();
        this.executorService = Executors.newFixedThreadPool(CONSUMER_THREADS);

        // Create connection
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(RABBITMQ_HOST);
        factory.setPort(RABBITMQ_PORT);
        factory.setUsername(RABBITMQ_USERNAME);
        factory.setPassword(RABBITMQ_PASSWORD);

        // Connection settings for reliability
        factory.setAutomaticRecoveryEnabled(true);
        factory.setNetworkRecoveryInterval(10000);
        factory.setRequestedHeartbeat(60);
        factory.setConnectionTimeout(30000);

        this.connection = factory.newConnection(executorService);
        LOGGER.info("Connected to RabbitMQ at {}:{}", RABBITMQ_HOST, RABBITMQ_PORT);
    }

    /**
     * Start consuming messages from all room queues.
     */
    public void startConsuming() throws IOException {
        LOGGER.info("Starting {} consumer threads", CONSUMER_THREADS);

        // Distribute rooms across consumer threads
        int roomsPerThread = 20 / CONSUMER_THREADS;
        int remainingRooms = 20 % CONSUMER_THREADS;

        int currentRoom = 1;
        for (int i = 0; i < CONSUMER_THREADS; i++) {
            Channel channel = connection.createChannel();
            channel.basicQos(PREFETCH_COUNT);
            channels.add(channel);

            // Assign rooms to this thread
            int roomsForThisThread = roomsPerThread + (i < remainingRooms ? 1 : 0);
            List<String> assignedRooms = new ArrayList<>();

            for (int j = 0; j < roomsForThisThread && currentRoom <= 20; j++) {
                assignedRooms.add(String.valueOf(currentRoom));
                currentRoom++;
            }

            // Start consumer for assigned rooms
            startConsumerForRooms(channel, assignedRooms, i);
        }

        LOGGER.info("All {} consumer threads started", CONSUMER_THREADS);
    }

    /**
     * Start a consumer for specific rooms on a channel.
     */
    private void startConsumerForRooms(Channel channel, List<String> rooms, int threadId) throws IOException {
        for (String roomId : rooms) {
            String queueName = "room." + roomId;

            DefaultConsumer consumer = new DefaultConsumer(channel) {
                @Override
                public void handleDelivery(String consumerTag, Envelope envelope,
                                           AMQP.BasicProperties properties, byte[] body) throws IOException {
                    try {
                        // Parse message
                        String messageJson = new String(body, "UTF-8");
                        QueueMessage message = objectMapper.readValue(messageJson, QueueMessage.class);

                        LOGGER.debug("Thread {} consumed message {} from room {}",
                                threadId, message.getMessageId(), roomId);

                        // Broadcast to room
                        roomManager.broadcastToRoom(message);

                        // Acknowledge message
                        channel.basicAck(envelope.getDeliveryTag(), false);

                    } catch (Exception e) {
                        LOGGER.error("Error processing message from room {}: {}", roomId, e.getMessage(), e);
                        // Negative acknowledgment - requeue message
                        channel.basicNack(envelope.getDeliveryTag(), false, true);
                    }
                }
            };

            // Start consuming
            channel.basicConsume(queueName, false, consumer);
            LOGGER.info("Thread {} started consuming from {}", threadId, queueName);
        }
    }

    /**
     * Shutdown the consumer gracefully.
     */
    public void shutdown() {
        LOGGER.info("Shutting down MessageConsumer");

        // Close all channels
        for (Channel channel : channels) {
            try {
                if (channel.isOpen()) {
                    channel.close();
                }
            } catch (Exception e) {
                LOGGER.warn("Error closing channel", e);
            }
        }

        // Close connection
        try {
            if (connection.isOpen()) {
                connection.close();
            }
        } catch (IOException e) {
            LOGGER.warn("Error closing connection", e);
        }

        // Shutdown executor
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }

        LOGGER.info("MessageConsumer shutdown complete");
    }

    public RoomManager getRoomManager() {
        return roomManager;
    }
}
