package com.cs6650.chat.client.metrics;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe accumulator for the "basic metrics" required by Assignment 1 Part 2.
 */
public final class MetricsRecorder {

    private final AtomicLong successfulMessages = new AtomicLong();
    private final AtomicLong failedMessages = new AtomicLong();
    private final AtomicLong totalRetries = new AtomicLong();
    private final AtomicLong connectionsOpened = new AtomicLong();
    private final AtomicLong connectionsReconnected = new AtomicLong();

    private volatile Instant startTime;
    private volatile Instant endTime;

    public void markStart() {
        startTime = Instant.now();
    }

    public void markEnd() {
        endTime = Instant.now();
    }

    public void incrementSuccess() {
        successfulMessages.incrementAndGet();
    }

    public void incrementFailure() {
        failedMessages.incrementAndGet();
    }

    public void addRetries(long retries) {
        totalRetries.addAndGet(retries);
    }

    public void incrementConnectionOpened() {
        connectionsOpened.incrementAndGet();
    }

    public void incrementConnectionReconnected() {
        connectionsReconnected.incrementAndGet();
    }

    public String summary() {
        Duration runtime = (startTime != null && endTime != null)
                ? Duration.between(startTime, endTime)
                : Duration.ZERO;
        long successes = successfulMessages.get();
        long failures = failedMessages.get();
        long total = successes + failures;
        double throughput = runtime.isZero() ? 0.0 : (double) total / runtime.toMillis() * 1000.0;

        return String.format(
                "=== Client Part 1 Metrics ===%n" +
                        "Successful messages : %d%n" +
                        "Failed messages     : %d%n" +
                        "Total retries       : %d%n" +
                        "Connections opened  : %d%n" +
                        "Reconnections       : %d%n" +
                        "Runtime (ms)        : %d%n" +
                        "Throughput (msg/s)  : %.2f%n",
                successes,
                failures,
                totalRetries.get(),
                connectionsOpened.get(),
                connectionsReconnected.get(),
                runtime.toMillis(),
                throughput
        );
    }
}
