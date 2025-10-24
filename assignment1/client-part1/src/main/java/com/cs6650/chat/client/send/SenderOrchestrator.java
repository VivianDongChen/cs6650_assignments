package com.cs6650.chat.client.send;

import com.cs6650.chat.client.config.ClientConfig;
import com.cs6650.chat.client.metrics.MetricsRecorder;
import com.cs6650.chat.client.message.ChatMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Coordinates warmup and main phase execution by submitting {@link SenderWorker} tasks to a
 * provided thread pool. Each worker maintains its own WebSocket connection and reports metrics
 * back to the shared {@link MetricsRecorder}.
 */
public final class SenderOrchestrator {

    private final ClientConfig config;
    private final BlockingQueue<ChatMessage> queue;
    private final MetricsRecorder metrics;
    private final ExecutorService executor;

    public SenderOrchestrator(ClientConfig config,
                              BlockingQueue<ChatMessage> queue,
                              MetricsRecorder metrics,
                              ExecutorService executor) {
        this.config = config;
        this.queue = queue;
        this.metrics = metrics;
        this.executor = executor;
    }

    public void runWarmupAndMainPhase() {
        metrics.markStart();
        runWarmupPhase();
        runMainPhase();
        metrics.markEnd();
        System.out.println(metrics.summary());
    }

    public void shutdown() {
        // Placeholder for future resource cleanup.
    }

    private void runWarmupPhase() {
        int threads = config.warmupThreads();
        long messagesPerThread = config.warmupMessagesPerThread();
        System.out.printf("[Warmup] Starting %d threads × %d messages%n", threads, messagesPerThread);
        submitAndAwait(threads, messagesPerThread, Phase.WARMUP);
        System.out.println("[Warmup] Completed");
    }

    private void runMainPhase() {
        long remaining = config.mainPhaseMessagesTotal();
        if (remaining <= 0) {
            return;
        }
        int threads = config.effectiveMainThreads();
        System.out.printf("[Main] Starting %d threads for %,d messages%n", threads, remaining);

        long base = remaining / threads;
        long remainder = remaining % threads;

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            long messagesForThread = base + (i < remainder ? 1 : 0);
            if (messagesForThread <= 0) {
                continue;
            }
            SenderWorker worker = new SenderWorker(config, queue, metrics, messagesForThread, Phase.MAIN.prefix + (i + 1));
            futures.add(executor.submit(worker));
        }
        waitForFutures(futures);
        System.out.println("[Main] Completed");
    }

    private void submitAndAwait(int threadCount, long messagesPerThread, Phase phase) {
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            SenderWorker worker = new SenderWorker(
                    config,
                    queue,
                    metrics,
                    messagesPerThread,
                    phase.prefix + (i + 1)
            );
            futures.add(executor.submit(worker));
        }
        waitForFutures(futures);
    }

    private void waitForFutures(List<Future<?>> futures) {
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                throw new RuntimeException("Sender worker execution failed", e);
            }
        }
        futures.clear();
    }

    private enum Phase {
        WARMUP("warmup-"),
        MAIN("main-");

        private final String prefix;

        Phase(String prefix) {
            this.prefix = prefix;
        }
    }
}
