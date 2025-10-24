package com.cs6650.chat.client;

import com.cs6650.chat.client.config.ClientConfig;
import com.cs6650.chat.client.config.ClientConfigLoader;
import com.cs6650.chat.client.metrics.MetricsRecorder;
import com.cs6650.chat.client.message.ChatMessage;
import com.cs6650.chat.client.message.MessageGenerator;
import com.cs6650.chat.client.send.SenderOrchestrator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Entry point for the Assignment 1 Client Part 1 application.
 * Wires together configuration, message generation, sender orchestration, and metrics reporting.
 */
public class App {

    public static void main(String[] args) {
        ClientConfig config = ClientConfigLoader.fromArgs(args);
        new App().run(config);
    }

    private void run(ClientConfig config) {
        System.out.println("=== Client Part 1 Bootstrap ===");
        System.out.printf("Target server: %s%n", config.serverUri());
        System.out.printf("Warmup: %d threads × %d messages%n", config.warmupThreads(), config.warmupMessagesPerThread());
        System.out.printf("Main phase total messages: %,d%n", config.totalMessages());
        System.out.printf("Main phase threads: %d%n", config.effectiveMainThreads());

        BlockingQueue<ChatMessage> messageQueue =
                new ArrayBlockingQueue<>(config.messageQueueCapacity());
        MetricsRecorder metrics = new MetricsRecorder();

        // Message generator thread
        MessageGenerator generator = new MessageGenerator(config, messageQueue);
        Thread generatorThread = new Thread(generator, "message-generator");
        generatorThread.setDaemon(true);
        generatorThread.start();

        // Executor for sender workers (warmup + main phase). The orchestrator will
        // dynamically submit tasks based on the phase requirements.
        ExecutorService senderPool = Executors.newCachedThreadPool();
        SenderOrchestrator orchestrator = new SenderOrchestrator(config, messageQueue, metrics, senderPool);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            orchestrator.shutdown();
            generator.stop();
            senderPool.shutdownNow();
        }));

        orchestrator.runWarmupAndMainPhase();

        generator.stop();
        try {
            generatorThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        senderPool.shutdown();
    }
}
