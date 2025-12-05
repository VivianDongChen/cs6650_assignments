package com.cs6650.chat.consumer.queue;

import com.cs6650.chat.consumer.broadcast.RoomManager;
import com.cs6650.chat.consumer.database.BatchMessageWriter;
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
 * Multi-threaded RabbitMQ consumer that processes messages, writes to database, and broadcasts to rooms.
 * Version 3: Adds PostgreSQL persistence with batch writing.
 */
public class MessageConsumer {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageConsumer.class);

    private static final String RABBITMQ_HOST = System.getenv().getOrDefault("RABBITMQ_HOST", "localhost");
    private static final int RABBITMQ_PORT = Integer.parseInt(System.getenv().getOrDefault("RABBITMQ_PORT", "5672"));
    private static final String RABBITMQ_USERNAME = System.getenv().getOrDefault("RABBITMQ_USERNAME", "guest");
    private static final String RABBITMQ_PASSWORD = System.getenv().getOrDefault("RABBITMQ_PASSWORD", "guest");
    private static final int PARTITIONS_PER_ROOM = Integer.parseInt(System.getenv().getOrDefault("PARTITIONS_PER_ROOM", "3"));;
    private static final int CONSUMER_THREADS = Integer.parseInt(System.getenv().getOrDefault("CONSUMER_THREADS", "60"));
    private static final int PREFETCH_COUNT = Integer.parseInt(System.getenv().getOrDefault("PREFETCH_COUNT", "50"));

    private final Connection connection;
    private final RoomManager roomManager;
    private final BatchMessageWriter batchWriter;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;
    private final List<Channel> channels;
    private final RetryHandler retryHandler;

    public MessageConsumer(RoomManager roomManager, BatchMessageWriter batchWriter) throws IOException, TimeoutException {
        this.roomManager = roomManager;
        this.batchWriter = batchWriter;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.channels = new ArrayList<>();
        this.executorService = Executors.newFixedThreadPool(CONSUMER_THREADS);
        this.retryHandler = new RetryHandler();

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

        this.connection = factory.newConnection();
        LOGGER.info("Connected to RabbitMQ at {}:{}", RABBITMQ_HOST, RABBITMQ_PORT);
    }

    /**
     * Start consuming messages from all room queues.
     */
    public void startConsuming() throws IOException {
      int totalQueues = 20 * PARTITIONS_PER_ROOM;
      LOGGER.info("Starting {} consumer threads for {} partitioned queues",
          CONSUMER_THREADS, totalQueues);

      // Create independent consumer thread for each partitioned queue
      for (int roomId = 1; roomId <= 20; roomId++) {
        for (int partition = 0; partition < PARTITIONS_PER_ROOM; partition++) {

          final int finalRoomId = roomId;
          final int finalPartition = partition;
          final String queueName = String.format("room.%d.partition.%d",
              finalRoomId, finalPartition);
          final int threadId = (finalRoomId - 1) * PARTITIONS_PER_ROOM + finalPartition;

          // Submit consumer task to executor service for parallel processing
          executorService.submit(() -> {
            try {
              Channel channel = connection.createChannel();
              channel.basicQos(PREFETCH_COUNT);

              synchronized (channels) {
                channels.add(channel);
              }

              LOGGER.info("Thread {} starting consumer for {}", threadId, queueName);
              startConsumerForQueue(channel, queueName, threadId);

            } catch (IOException e) {
              LOGGER.error("Failed to start consumer thread {} for {}",
                  threadId, queueName, e);
            }
          });
        }
      }

      LOGGER.info("All {} consumer threads submitted for partitioned queues", totalQueues);
    }

    private void startConsumerForQueue(Channel channel, String queueName, int threadId)
        throws IOException {

      DefaultConsumer consumer = new DefaultConsumer(channel) {
        @Override
        public void handleDelivery(String consumerTag, Envelope envelope,
            AMQP.BasicProperties properties, byte[] body)
            throws IOException {
          String messageId = "unknown";
          try {
            // Parse message
            String messageJson = new String(body, "UTF-8");
            QueueMessage message = objectMapper.readValue(messageJson, QueueMessage.class);
            messageId = message.getMessageId();

            LOGGER.debug("Thread {} consumed message {} from {}",
                threadId, messageId, queueName);

            // Write to database
            boolean addedToDb = batchWriter.addMessage(message);
            if (!addedToDb) {
              LOGGER.warn("Failed to add message {} to database write queue", messageId);
            }

            // Broadcast to room
            roomManager.broadcastToRoom(message);

            // Acknowledge message
            channel.basicAck(envelope.getDeliveryTag(), false);

            LOGGER.debug("Message {} processed successfully", messageId);

          } catch (Exception e) {
            LOGGER.error("Error processing message {} from {}: {}",
                messageId, queueName, e.getMessage(), e);
            retryHandler.handleFailedDelivery(channel, envelope.getDeliveryTag(),
                properties, body, queueName, messageId);
          }
        }
      };

      channel.basicConsume(queueName, false, consumer);
      LOGGER.info("Thread {} started consuming from {}", threadId, queueName);
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
                    String messageId = "unknown";
                    try {
                        // Parse message
                        String messageJson = new String(body, "UTF-8");
                        QueueMessage message = objectMapper.readValue(messageJson, QueueMessage.class);
                        messageId = message.getMessageId();

                        LOGGER.debug("Thread {} consumed message {} from room {}",
                                threadId, messageId, roomId);

                        // STEP 1: Write to database (async batch)
                        boolean addedToDb = batchWriter.addMessage(message);
                        if (!addedToDb) {
                            LOGGER.warn("Failed to add message {} to database write queue", messageId);
                        }

                        // STEP 2: Broadcast to room (real-time delivery)
                        roomManager.broadcastToRoom(message);

                        // Acknowledge message after successful processing
                        channel.basicAck(envelope.getDeliveryTag(), false);

                        LOGGER.debug("Message {} processed successfully (DB queued: {}, broadcast: success)",
                                messageId, addedToDb);

                    } catch (Exception e) {
                        LOGGER.error("Error processing message {} from room {}: {}", messageId, roomId, e.getMessage(), e);
                        // Use retry handler for failed delivery
                        retryHandler.handleFailedDelivery(channel, envelope.getDeliveryTag(), properties, body, roomId, messageId);
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

    public BatchMessageWriter getBatchWriter() {
        return batchWriter;
    }
}
