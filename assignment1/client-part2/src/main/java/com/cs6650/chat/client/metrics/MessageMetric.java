package com.cs6650.chat.client.metrics;

import java.util.concurrent.TimeUnit;

/**
 * Records performance metrics for a single message transmission.
 * This class is immutable and thread-safe.
 */
public final class MessageMetric {

    private final String messageId;
    private final String messageType;
    private final int roomId;
    private final long sendTimeNanos;
    private final long receiveTimeNanos;
    private final long latencyMs;
    private final String statusCode;

    /**
     * Creates a new message metric.
     *
     * @param messageId unique identifier for the message
     * @param messageType type of message (TEXT, JOIN, LEAVE)
     * @param roomId room identifier (1-20)
     * @param sendTimeNanos timestamp when message was sent (System.nanoTime())
     * @param receiveTimeNanos timestamp when response was received (System.nanoTime())
     * @param statusCode "success" or "error"
     */
    public MessageMetric(String messageId,
                         String messageType,
                         int roomId,
                         long sendTimeNanos,
                         long receiveTimeNanos,
                         String statusCode) {
        this.messageId = messageId;
        this.messageType = messageType;
        this.roomId = roomId;
        this.sendTimeNanos = sendTimeNanos;
        this.receiveTimeNanos = receiveTimeNanos;
        this.latencyMs = TimeUnit.NANOSECONDS.toMillis(receiveTimeNanos - sendTimeNanos);
        this.statusCode = statusCode;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getMessageType() {
        return messageType;
    }

    public int getRoomId() {
        return roomId;
    }

    public long getSendTimeNanos() {
        return sendTimeNanos;
    }

    public long getReceiveTimeNanos() {
        return receiveTimeNanos;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public String getStatusCode() {
        return statusCode;
    }

    /**
     * Returns the timestamp in milliseconds since test start.
     * Used for CSV export and time-series analysis.
     *
     * @param testStartTimeNanos the start time of the test (System.nanoTime())
     * @return milliseconds elapsed since test start
     */
    public long getTimestampMs(long testStartTimeNanos) {
        return TimeUnit.NANOSECONDS.toMillis(sendTimeNanos - testStartTimeNanos);
    }

    @Override
    public String toString() {
        return String.format("MessageMetric{id=%s, type=%s, room=%d, latency=%dms, status=%s}",
                messageId, messageType, roomId, latencyMs, statusCode);
    }
}
