package com.cs6650.chat.client.metrics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Calculates statistical metrics from collected message data.
 */
public class StatisticsAnalyzer {

    /**
     * Analyzes a list of message metrics and calculates comprehensive statistics.
     *
     * @param metrics list of message metrics
     * @return Statistics object containing all calculated metrics
     */
    public Statistics analyze(List<MessageMetric> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return createEmptyStatistics();
        }

        // Extract and sort latencies for percentile calculations
        List<Long> latencies = metrics.stream()
            .map(MessageMetric::getLatencyMs)
            .sorted()
            .collect(Collectors.toList());

        // Calculate basic statistics
        double mean = calculateMean(latencies);
        double median = calculatePercentile(latencies, 0.50);
        double p95 = calculatePercentile(latencies, 0.95);
        double p99 = calculatePercentile(latencies, 0.99);
        long min = latencies.get(0);
        long max = latencies.get(latencies.size() - 1);

        // Calculate throughput per room
        Map<Integer, Long> throughputPerRoom = calculateThroughputPerRoom(metrics);

        // Calculate message type distribution
        Map<String, Long> messageTypeDistribution = calculateMessageTypeDistribution(metrics);

        // Count successes and failures
        long total = metrics.size();
        long successful = metrics.stream().filter(m -> "success".equals(m.getStatusCode())).count();
        long failed = total - successful;

        return new Statistics(
            mean,
            median,
            p95,
            p99,
            min,
            max,
            throughputPerRoom,
            messageTypeDistribution,
            total,
            successful,
            failed
        );
    }

    /**
     * Calculates the mean (average) of a list of values.
     */
    private double calculateMean(List<Long> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        long sum = 0;
        for (long value : values) {
            sum += value;
        }
        return (double) sum / values.size();
    }

    /**
     * Calculates a percentile from a sorted list of values.
     *
     * @param sortedValues list of values (must be sorted ascending)
     * @param percentile percentile to calculate (0.0 to 1.0)
     * @return the value at the specified percentile
     */
    private double calculatePercentile(List<Long> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) {
            return 0.0;
        }
        if (sortedValues.size() == 1) {
            return sortedValues.get(0);
        }

        // Use linear interpolation between closest ranks
        double rank = percentile * (sortedValues.size() - 1);
        int lowerIndex = (int) Math.floor(rank);
        int upperIndex = (int) Math.ceil(rank);

        if (lowerIndex == upperIndex) {
            return sortedValues.get(lowerIndex);
        }

        double fraction = rank - lowerIndex;
        long lowerValue = sortedValues.get(lowerIndex);
        long upperValue = sortedValues.get(upperIndex);

        return lowerValue + fraction * (upperValue - lowerValue);
    }

    /**
     * Calculates message count per room.
     */
    private Map<Integer, Long> calculateThroughputPerRoom(List<MessageMetric> metrics) {
        Map<Integer, Long> roomCounts = new HashMap<>();
        for (MessageMetric metric : metrics) {
            roomCounts.merge(metric.getRoomId(), 1L, Long::sum);
        }
        return roomCounts;
    }

    /**
     * Calculates message count per message type.
     */
    private Map<String, Long> calculateMessageTypeDistribution(List<MessageMetric> metrics) {
        Map<String, Long> typeCounts = new HashMap<>();
        for (MessageMetric metric : metrics) {
            typeCounts.merge(metric.getMessageType(), 1L, Long::sum);
        }
        return typeCounts;
    }

    /**
     * Creates an empty statistics object for edge cases.
     */
    private Statistics createEmptyStatistics() {
        return new Statistics(
            0.0, 0.0, 0.0, 0.0, 0L, 0L,
            new HashMap<>(),
            new HashMap<>(),
            0L, 0L, 0L
        );
    }
}
