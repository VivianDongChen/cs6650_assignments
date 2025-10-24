package com.cs6650.chat.client.metrics;

import java.util.Map;

/**
 * Container for statistical analysis results.
 */
public class Statistics {

    private final double meanLatencyMs;
    private final double medianLatencyMs;
    private final double p95LatencyMs;
    private final double p99LatencyMs;
    private final long minLatencyMs;
    private final long maxLatencyMs;
    private final Map<Integer, Long> throughputPerRoom;
    private final Map<String, Long> messageTypeDistribution;
    private final long totalMessages;
    private final long successfulMessages;
    private final long failedMessages;

    public Statistics(double meanLatencyMs,
                     double medianLatencyMs,
                     double p95LatencyMs,
                     double p99LatencyMs,
                     long minLatencyMs,
                     long maxLatencyMs,
                     Map<Integer, Long> throughputPerRoom,
                     Map<String, Long> messageTypeDistribution,
                     long totalMessages,
                     long successfulMessages,
                     long failedMessages) {
        this.meanLatencyMs = meanLatencyMs;
        this.medianLatencyMs = medianLatencyMs;
        this.p95LatencyMs = p95LatencyMs;
        this.p99LatencyMs = p99LatencyMs;
        this.minLatencyMs = minLatencyMs;
        this.maxLatencyMs = maxLatencyMs;
        this.throughputPerRoom = throughputPerRoom;
        this.messageTypeDistribution = messageTypeDistribution;
        this.totalMessages = totalMessages;
        this.successfulMessages = successfulMessages;
        this.failedMessages = failedMessages;
    }

    public double getMeanLatencyMs() {
        return meanLatencyMs;
    }

    public double getMedianLatencyMs() {
        return medianLatencyMs;
    }

    public double getP95LatencyMs() {
        return p95LatencyMs;
    }

    public double getP99LatencyMs() {
        return p99LatencyMs;
    }

    public long getMinLatencyMs() {
        return minLatencyMs;
    }

    public long getMaxLatencyMs() {
        return maxLatencyMs;
    }

    public Map<Integer, Long> getThroughputPerRoom() {
        return throughputPerRoom;
    }

    public Map<String, Long> getMessageTypeDistribution() {
        return messageTypeDistribution;
    }

    public long getTotalMessages() {
        return totalMessages;
    }

    public long getSuccessfulMessages() {
        return successfulMessages;
    }

    public long getFailedMessages() {
        return failedMessages;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n=== Performance Analysis (Part 2) ===\n");
        sb.append(String.format("Total messages: %,d%n", totalMessages));
        sb.append(String.format("Successful: %,d (%.2f%%)%n", successfulMessages, (successfulMessages * 100.0) / totalMessages));
        sb.append(String.format("Failed: %,d (%.2f%%)%n", failedMessages, (failedMessages * 100.0) / totalMessages));
        sb.append("\n--- Latency Statistics ---\n");
        sb.append(String.format("Mean latency:   %.2f ms%n", meanLatencyMs));
        sb.append(String.format("Median latency: %.2f ms%n", medianLatencyMs));
        sb.append(String.format("95th percentile: %.2f ms%n", p95LatencyMs));
        sb.append(String.format("99th percentile: %.2f ms%n", p99LatencyMs));
        sb.append(String.format("Min latency:    %d ms%n", minLatencyMs));
        sb.append(String.format("Max latency:    %d ms%n", maxLatencyMs));

        sb.append("\n--- Message Type Distribution ---\n");
        for (Map.Entry<String, Long> entry : messageTypeDistribution.entrySet()) {
            double pct = (entry.getValue() * 100.0) / totalMessages;
            sb.append(String.format("  %-6s: %,10d (%5.2f%%)%n", entry.getKey(), entry.getValue(), pct));
        }

        sb.append("\n--- Throughput Per Room (Top 10) ---\n");
        throughputPerRoom.entrySet().stream()
            .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()))
            .limit(10)
            .forEach(entry -> {
                sb.append(String.format("  Room %2d: %,10d messages%n", entry.getKey(), entry.getValue()));
            });

        return sb.toString();
    }
}
