package com.cs6650.chat.client.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe collector for message performance metrics.
 * Uses lock-free data structures for high-performance concurrent collection.
 */
public class MetricsCollector {

    private final ConcurrentLinkedQueue<MessageMetric> metrics;
    private final AtomicLong testStartTimeNanos;
    private final AtomicLong metricsCount;

    public MetricsCollector() {
        this.metrics = new ConcurrentLinkedQueue<>();
        this.testStartTimeNanos = new AtomicLong(0);
        this.metricsCount = new AtomicLong(0);
    }

    /**
     * Records the start time of the test (called once before test begins).
     */
    public void markTestStart() {
        testStartTimeNanos.set(System.nanoTime());
    }

    /**
     * Records a message metric.
     * This method is thread-safe and lock-free for high performance.
     *
     * @param metric the metric to record
     */
    public void recordMetric(MessageMetric metric) {
        metrics.offer(metric);
        metricsCount.incrementAndGet();
    }

    /**
     * Returns all collected metrics as a list.
     * This creates a snapshot of current metrics.
     *
     * @return list of all metrics (may be large, ~500k entries)
     */
    public List<MessageMetric> getAllMetrics() {
        return new ArrayList<>(metrics);
    }

    /**
     * Returns the test start time in nanoseconds.
     *
     * @return System.nanoTime() value when test started
     */
    public long getTestStartTimeNanos() {
        return testStartTimeNanos.get();
    }

    /**
     * Returns the total number of metrics collected.
     *
     * @return count of metrics
     */
    public long getMetricsCount() {
        return metricsCount.get();
    }

    /**
     * Clears all collected metrics (useful for testing).
     */
    public void clear() {
        metrics.clear();
        metricsCount.set(0);
    }
}
