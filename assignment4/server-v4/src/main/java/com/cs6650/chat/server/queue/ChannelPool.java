package com.cs6650.chat.server.queue;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeoutException;

/**
 * Thread-safe channel pool for RabbitMQ connections.
 * Manages a pool of channels to avoid creating new channels for each message.
 */
public class ChannelPool {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChannelPool.class);

    private final BlockingQueue<Channel> pool;
    private final Connection connection;
    private final int poolSize;

    private static final String RABBITMQ_HOST = System.getenv().getOrDefault("RABBITMQ_HOST", "localhost");
    private static final int RABBITMQ_PORT = Integer.parseInt(System.getenv().getOrDefault("RABBITMQ_PORT", "5672"));
    private static final String RABBITMQ_USERNAME = System.getenv().getOrDefault("RABBITMQ_USERNAME", "guest");
    private static final String RABBITMQ_PASSWORD = System.getenv().getOrDefault("RABBITMQ_PASSWORD", "guest");

    public ChannelPool(int poolSize) throws IOException, TimeoutException {
        this.poolSize = poolSize;
        this.pool = new ArrayBlockingQueue<>(poolSize);

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

        // Pre-create channels
        for (int i = 0; i < poolSize; i++) {
            Channel channel = connection.createChannel();
            // Enable publisher confirms for reliability
            channel.confirmSelect();
            pool.offer(channel);
        }
        LOGGER.info("Channel pool initialized with {} channels", poolSize);
    }

    /**
     * Borrow a channel from the pool.
     * Blocks if no channels are available.
     */
    public Channel borrowChannel() throws InterruptedException {
        Channel channel = pool.take();

        // Verify channel is still open
        if (!channel.isOpen()) {
            LOGGER.warn("Borrowed channel was closed, creating new one");
            try {
                channel = connection.createChannel();
                channel.confirmSelect();
            } catch (IOException e) {
                LOGGER.error("Failed to create replacement channel", e);
                throw new RuntimeException("Failed to create replacement channel", e);
            }
        }

        return channel;
    }

    /**
     * Return a channel to the pool.
     */
    public void returnChannel(Channel channel) {
        if (channel != null && channel.isOpen()) {
            pool.offer(channel);
        } else {
            LOGGER.warn("Cannot return closed channel to pool");
            // Try to create a new channel to maintain pool size
            try {
                Channel newChannel = connection.createChannel();
                newChannel.confirmSelect();
                pool.offer(newChannel);
            } catch (IOException e) {
                LOGGER.error("Failed to create replacement channel", e);
            }
        }
    }

    /**
     * Close all channels and the connection.
     */
    public void close() {
        LOGGER.info("Closing channel pool");
        while (!pool.isEmpty()) {
            try {
                Channel channel = pool.poll();
                if (channel != null && channel.isOpen()) {
                    channel.close();
                }
            } catch (Exception e) {
                LOGGER.warn("Error closing channel", e);
            }
        }

        try {
            if (connection != null && connection.isOpen()) {
                connection.close();
            }
        } catch (IOException e) {
            LOGGER.warn("Error closing connection", e);
        }
        LOGGER.info("Channel pool closed");
    }

    public int getPoolSize() {
        return poolSize;
    }

    public int getAvailableChannels() {
        return pool.size();
    }
}
