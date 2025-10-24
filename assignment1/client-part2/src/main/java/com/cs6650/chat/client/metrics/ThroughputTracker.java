package com.cs6650.chat.client.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Analyzes throughput over time by grouping messages into time buckets.
 * Each bucket represents a 10-second interval and contains the message count for that period.
 */
public class ThroughputTracker {

    private static final long BUCKET_SIZE_MS = 10_000; // 10 seconds in milliseconds

    /**
     * Calculates throughput buckets from collected metrics.
     * Each bucket represents a 10-second interval with message count and throughput.
     *
     * @param metrics list of message metrics
     * @param testStartTimeNanos the test start time in nanoseconds (from System.nanoTime())
     * @return list of throughput buckets ordered by time
     */
    public List<ThroughputBucket> calculateThroughputBuckets(List<MessageMetric> metrics, long testStartTimeNanos) {
        if (metrics == null || metrics.isEmpty()) {
            return new ArrayList<>();
        }

        // Group messages by 10-second buckets
        // Key: bucket index (0, 1, 2...), Value: message count in that bucket
        Map<Long, Long> bucketCounts = new TreeMap<>();

        for (MessageMetric metric : metrics) {
            // Calculate elapsed time in milliseconds from test start
            long elapsedMs = (metric.getReceiveTimeNanos() - testStartTimeNanos) / 1_000_000;

            // Determine which bucket this message belongs to
            long bucketIndex = elapsedMs / BUCKET_SIZE_MS;

            // Increment count for this bucket
            bucketCounts.merge(bucketIndex, 1L, Long::sum);
        }

        // Convert map to list of ThroughputBucket objects
        List<ThroughputBucket> buckets = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : bucketCounts.entrySet()) {
            long bucketIndex = entry.getKey();
            long messageCount = entry.getValue();

            // Calculate start and end time for this bucket
            long startTimeSeconds = bucketIndex * (BUCKET_SIZE_MS / 1000);
            long endTimeSeconds = (bucketIndex + 1) * (BUCKET_SIZE_MS / 1000);

            // Calculate throughput (messages per second)
            double throughput = messageCount / (BUCKET_SIZE_MS / 1000.0);

            buckets.add(new ThroughputBucket(
                bucketIndex,
                startTimeSeconds,
                endTimeSeconds,
                messageCount,
                throughput
            ));
        }

        return buckets;
    }

    /**
     * Represents a single time bucket with throughput statistics.
     */
    public static class ThroughputBucket {
        private final long bucketIndex;
        private final long startTimeSeconds;
        private final long endTimeSeconds;
        private final long messageCount;
        private final double messagesPerSecond;

        public ThroughputBucket(long bucketIndex, long startTimeSeconds, long endTimeSeconds,
                                long messageCount, double messagesPerSecond) {
            this.bucketIndex = bucketIndex;
            this.startTimeSeconds = startTimeSeconds;
            this.endTimeSeconds = endTimeSeconds;
            this.messageCount = messageCount;
            this.messagesPerSecond = messagesPerSecond;
        }

        public long getBucketIndex() {
            return bucketIndex;
        }

        public long getStartTimeSeconds() {
            return startTimeSeconds;
        }

        public long getEndTimeSeconds() {
            return endTimeSeconds;
        }

        public long getMessageCount() {
            return messageCount;
        }

        public double getMessagesPerSecond() {
            return messagesPerSecond;
        }

        @Override
        public String toString() {
            return String.format("[%ds-%ds]: %d messages, %.2f msg/s",
                startTimeSeconds, endTimeSeconds, messageCount, messagesPerSecond);
        }
    }

    /**
     * Exports throughput buckets to CSV format for visualization.
     *
     * @param buckets list of throughput buckets
     * @return CSV string with header and data rows
     */
    public String exportToCsv(List<ThroughputBucket> buckets) {
        StringBuilder csv = new StringBuilder();
        csv.append("startTime,endTime,messageCount,messagesPerSecond\n");

        for (ThroughputBucket bucket : buckets) {
            csv.append(bucket.getStartTimeSeconds()).append(",")
               .append(bucket.getEndTimeSeconds()).append(",")
               .append(bucket.getMessageCount()).append(",")
               .append(String.format("%.2f", bucket.getMessagesPerSecond()))
               .append("\n");
        }

        return csv.toString();
    }
}
