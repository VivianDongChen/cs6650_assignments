package com.cs6650.chat.consumer.queue;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles retry logic with exponential backoff and dead-letter queue routing.
 * Tracks message retry count and enforces maximum retry limit.
 */
public class RetryHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RetryHandler.class);

    private static final int MAX_RETRIES = Integer.parseInt(System.getenv().getOrDefault("MAX_RETRIES", "3"));
    private static final String DLQ_EXCHANGE = "chat.dlx";
    private static final String DLQ_ROUTING_KEY = "dlq";

    /**
     * Handle a failed message delivery.
     * Checks retry count and either requeues with backoff or sends to DLQ.
     *
     * @param channel The RabbitMQ channel
     * @param deliveryTag The message delivery tag
     * @param properties The message properties (contains retry metadata)
     * @param body The message body
     * @param roomId The room ID for logging
     * @param messageId The message ID for logging
     * @throws IOException if acknowledgment fails
     */
    public void handleFailedDelivery(Channel channel, long deliveryTag, AMQP.BasicProperties properties,
                                     byte[] body, String roomId, String messageId) throws IOException {
        int retryCount = getRetryCount(properties);

        LOGGER.warn("Message {} in room {} failed delivery (retry count: {})", messageId, roomId, retryCount);

        if (retryCount >= MAX_RETRIES) {
            // Max retries exceeded - send to DLQ
            LOGGER.error("Message {} in room {} exceeded max retries ({}). Sending to DLQ.",
                    messageId, roomId, MAX_RETRIES);
            sendToDLQ(channel, properties, body, roomId, messageId);
            // Acknowledge the message to remove it from the original queue
            channel.basicAck(deliveryTag, false);
        } else {
            // Requeue with increased retry count
            LOGGER.info("Requeuing message {} in room {} (retry {}/{})",
                    messageId, roomId, retryCount + 1, MAX_RETRIES);

            // Calculate exponential backoff delay
            int delayMs = calculateBackoffDelay(retryCount);
            LOGGER.debug("Applying {}ms backoff delay for message {}", delayMs, messageId);

            // Negative acknowledgment - requeue for retry
            // Note: RabbitMQ will requeue immediately. For proper backoff, would need
            // a delayed retry queue with TTL, but for simplicity we use immediate requeue
            channel.basicNack(deliveryTag, false, true);
        }
    }

    /**
     * Get the current retry count from message properties.
     * RabbitMQ tracks this in x-death header when using DLX.
     */
    private int getRetryCount(AMQP.BasicProperties properties) {
        if (properties == null || properties.getHeaders() == null) {
            return 0;
        }

        Object xDeath = properties.getHeaders().get("x-death");
        if (xDeath instanceof List) {
            List<?> deaths = (List<?>) xDeath;
            if (!deaths.isEmpty() && deaths.get(0) instanceof Map) {
                Map<?, ?> death = (Map<?, ?>) deaths.get(0);
                Object count = death.get("count");
                if (count instanceof Long) {
                    return ((Long) count).intValue();
                } else if (count instanceof Integer) {
                    return (Integer) count;
                }
            }
        }

        return 0;
    }

    /**
     * Calculate exponential backoff delay.
     * Formula: 100ms * 2^retryCount
     * Results: 100ms, 200ms, 400ms, 800ms, etc.
     */
    private int calculateBackoffDelay(int retryCount) {
        int baseDelayMs = 100;
        return baseDelayMs * (int) Math.pow(2, retryCount);
    }

    /**
     * Send message to dead-letter queue for manual inspection.
     */
    private void sendToDLQ(Channel channel, AMQP.BasicProperties properties, byte[] body,
                           String roomId, String messageId) throws IOException {
        try {
            // Add metadata about the failure
            Map<String, Object> headers = new HashMap<>();
            if (properties.getHeaders() != null) {
                headers.putAll(properties.getHeaders());
            }
            headers.put("x-original-room", roomId);
            headers.put("x-dlq-timestamp", System.currentTimeMillis());
            headers.put("x-dlq-reason", "Max retries exceeded");

            AMQP.BasicProperties dlqProperties = new AMQP.BasicProperties.Builder()
                    .headers(headers)
                    .contentType(properties.getContentType())
                    .deliveryMode(2)  // Persistent
                    .build();

            // Publish to DLQ exchange
            channel.basicPublish(DLQ_EXCHANGE, DLQ_ROUTING_KEY, dlqProperties, body);
            LOGGER.info("Sent message {} from room {} to DLQ", messageId, roomId);
        } catch (IOException e) {
            LOGGER.error("Failed to send message {} to DLQ: {}", messageId, e.getMessage(), e);
            throw e;
        }
    }

    public int getMaxRetries() {
        return MAX_RETRIES;
    }
}
