package com.cs6650.chat.server.queue;

import com.cs6650.chat.server.config.ObjectMapperProvider;
import com.cs6650.chat.server.model.ChatMessage;
import com.cs6650.chat.server.model.QueueMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.MessageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

/**
 * Publishes messages to RabbitMQ queues.
 */
public class MessagePublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessagePublisher.class);

    private static final String EXCHANGE_NAME = "chat.exchange";
    private static final String ROUTING_KEY_PREFIX = "room.";
    private static final int PARTITIONS_PER_ROOM = Integer.parseInt(System.getenv().getOrDefault("PARTITIONS_PER_ROOM", "3"));;
    private static final int CHANNEL_POOL_SIZE = Integer.parseInt(
            System.getenv().getOrDefault("CHANNEL_POOL_SIZE", "20"));

    private final ChannelPool channelPool;
    private final ObjectMapper objectMapper;
    private final String serverId;

    public MessagePublisher() throws IOException, TimeoutException {
        this.channelPool = new ChannelPool(CHANNEL_POOL_SIZE);
        this.objectMapper = ObjectMapperProvider.get();
        this.serverId = generateServerId();

        // Setup exchange and queues
        setupRabbitMQ();

        LOGGER.info("MessagePublisher initialized with serverId: {}", serverId);
    }

    /**
     * Setup RabbitMQ exchange and queues for 20 rooms.
     */
    private void setupRabbitMQ() {
        Channel channel = null;
        try {
            channel = channelPool.borrowChannel();

            // Declare topic exchange
            channel.exchangeDeclare(EXCHANGE_NAME, "topic", true);
            LOGGER.info("Declared exchange: {}", EXCHANGE_NAME);

            // Declare partitioned queues for rooms 1-20
            for (int roomId = 1; roomId <= 20; roomId++) {
              for (int partition = 0; partition < PARTITIONS_PER_ROOM; partition++) {
                String queueName = String.format("room.%d.partition.%d", roomId, partition);
                String routingKey = String.format("room.%d.partition.%d", roomId, partition);

                // Declare durable queue
                channel.queueDeclare(queueName, true, false, false, null);
                channel.queueBind(queueName, EXCHANGE_NAME, routingKey);

                LOGGER.debug("Declared and bound queue: {} with routing key: {}", queueName, routingKey);
              }
            }

            LOGGER.info("RabbitMQ setup complete: {} partitioned queues created", 20 * PARTITIONS_PER_ROOM);
        } catch (IOException | InterruptedException e) {
            LOGGER.error("Failed to setup RabbitMQ", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Failed to setup RabbitMQ", e);
        } finally {
            if (channel != null) {
                channelPool.returnChannel(channel);
            }
        }
    }

    /**
     * Publish a chat message to the appropriate room queue.
     */
    public void publishMessage(ChatMessage chatMessage, String roomId, String clientIp) {
        Channel channel = null;
        try {
            channel = channelPool.borrowChannel();

            // Build queue message
            QueueMessage queueMessage = new QueueMessage(
                    chatMessage.getMessageId() != null ? chatMessage.getMessageId() : UUID.randomUUID().toString(),
                    roomId,
                    String.valueOf(chatMessage.getUserId()),
                    chatMessage.getUsername(),
                    chatMessage.getMessage(),
                    chatMessage.getTimestamp() != null ? chatMessage.getTimestamp() : Instant.now(),
                    chatMessage.getMessageType(),
                    serverId,
                    clientIp
            );

            // Serialize to JSON
            String messageJson = objectMapper.writeValueAsString(queueMessage);
            byte[] messageBytes = messageJson.getBytes("UTF-8");

            // Calculate partition based on userId hash
            String userId = String.valueOf(chatMessage.getUserId());
            int partition = Math.abs(userId.hashCode()) % PARTITIONS_PER_ROOM;

            // Publish to partitioned queue
            String routingKey = String.format("room.%s.partition.%d", roomId, partition);
            channel.basicPublish(
                EXCHANGE_NAME,
                routingKey,
                MessageProperties.PERSISTENT_TEXT_PLAIN,
                messageBytes
            );

            LOGGER.debug("Published message {} to room {} partition {} (userId: {})",
                queueMessage.getMessageId(), roomId, partition, userId);
        } catch (InterruptedException e) {
            LOGGER.error("Interrupted while borrowing channel", e);
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            LOGGER.error("Failed to publish message to room {}", roomId, e);
            // In production, implement retry logic or dead letter queue
        } finally {
            if (channel != null) {
                channelPool.returnChannel(channel);
            }
        }
    }

    /**
     * Generate a unique server ID.
     */
    private String generateServerId() {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            return hostname + "-" + UUID.randomUUID().toString().substring(0, 8);
        } catch (Exception e) {
            return "server-" + UUID.randomUUID().toString().substring(0, 8);
        }
    }

    /**
     * Close the channel pool.
     */
    public void close() {
        channelPool.close();
    }

    public ChannelPool getChannelPool() {
        return channelPool;
    }
}
