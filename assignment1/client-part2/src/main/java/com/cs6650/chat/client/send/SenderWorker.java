package com.cs6650.chat.client.send;

import com.cs6650.chat.client.config.ClientConfig;
import com.cs6650.chat.client.metrics.MetricsRecorder;
import com.cs6650.chat.client.metrics.MetricsCollector;
import com.cs6650.chat.client.metrics.MessageMetric;
import com.cs6650.chat.client.message.ChatMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;

public final class SenderWorker implements Runnable {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final ClientConfig config;
    private final BlockingQueue<ChatMessage> queue;
    private final MetricsRecorder metrics;
    private final MetricsCollector detailedMetrics;  // Part 2: detailed per-message metrics
    private final long messagesToSend;
    private final String workerName;

    public SenderWorker(ClientConfig config,
                        BlockingQueue<ChatMessage> queue,
                        MetricsRecorder metrics,
                        MetricsCollector detailedMetrics,
                        long messagesToSend,
                        String workerName) {
        this.config = config;
        this.queue = queue;
        this.metrics = metrics;
        this.detailedMetrics = detailedMetrics;
        this.messagesToSend = messagesToSend;
        this.workerName = workerName;
    }

    @Override
    public void run() {
        ReliableWebSocketClient client = new ReliableWebSocketClient(config.serverUri(), config.sendTimeout());
        try {
            client.connect();
            metrics.incrementConnectionOpened();
        } catch (Exception e) {
            System.err.printf("[%s] Failed to open WebSocket connection: %s%n", workerName, e.getMessage());
            metrics.incrementFailure();
            return;
        }

        long sent = 0;
        while (sent < messagesToSend && !Thread.currentThread().isInterrupted()) {
            try {
                ChatMessage baseMessage = queue.take();
                ChatMessage message = baseMessage.withClientSendTime(Instant.now());
                boolean success = sendWithRetry(client, message);
                if (success) {
                    metrics.incrementSuccess();
                } else {
                    metrics.incrementFailure();
                }
                sent++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        client.close();
    }

    private boolean sendWithRetry(ReliableWebSocketClient client, ChatMessage message) {
        int attempt = 0;
        Duration backoff = config.initialBackoff();
        while (attempt <= config.maxRetries()) {
            try {
                // Record send time (Part 2: detailed metrics)
                long sendTimeNanos = System.nanoTime();

                String payload = MAPPER.writeValueAsString(message.withClientSendTime(Instant.now()));
                String response = client.sendAndAwait(payload, config.sendTimeout());

                // Record receive time (Part 2: detailed metrics)
                long receiveTimeNanos = System.nanoTime();

                boolean success = isSuccess(response);

                // Record detailed metric (Part 2)
                MessageMetric metric = new MessageMetric(
                    message.messageId(),
                    message.messageType(),
                    message.roomId(),
                    sendTimeNanos,
                    receiveTimeNanos,
                    success ? "success" : "error"
                );
                detailedMetrics.recordMetric(metric);

                if (success) {
                    return true;
                }
                throw new IllegalStateException("Server responded with non-success status");
            } catch (Exception ex) {
                attempt++;
                if (attempt > config.maxRetries()) {
                    System.err.printf("[%s] Message failed after %d attempts: %s%n", workerName, attempt - 1, ex.getMessage());

                    // Record failed metric (Part 2)
                    MessageMetric failedMetric = new MessageMetric(
                        message.messageId(),
                        message.messageType(),
                        message.roomId(),
                        System.nanoTime(),
                        System.nanoTime(),
                        "error"
                    );
                    detailedMetrics.recordMetric(failedMetric);

                    return false;
                }
                metrics.addRetries(1);
                sleep(backoff);
                backoff = backoff.multipliedBy(2);
                if (backoff.compareTo(config.maxBackoff()) > 0) {
                    backoff = config.maxBackoff();
                }
                try {
                    client.reconnect();
                    metrics.incrementConnectionReconnected();
                } catch (Exception reconnect) {
                    System.err.printf("[%s] Reconnect failed: %s%n", workerName, reconnect.getMessage());
                }
            }
        }
        return false;
    }

    private boolean isSuccess(String response) throws Exception {
        JsonNode node = MAPPER.readTree(response);
        JsonNode statusNode = node.get("status");
        return statusNode != null && "success".equalsIgnoreCase(statusNode.asText());
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(Math.max(1, duration.toMillis()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
