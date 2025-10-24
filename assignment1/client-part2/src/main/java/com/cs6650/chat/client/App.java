package com.cs6650.chat.client;

import com.cs6650.chat.client.config.ClientConfig;
import com.cs6650.chat.client.config.ClientConfigLoader;
import com.cs6650.chat.client.metrics.*;
import com.cs6650.chat.client.message.ChatMessage;
import com.cs6650.chat.client.message.MessageGenerator;
import com.cs6650.chat.client.send.SenderOrchestrator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Entry point for Assignment 1 Client Part 2 application.
 * Extends Part 1 with detailed per-message metrics collection and analysis.
 */
public class App {

    private static final String OUTPUT_DIR = "output";
    private static final String METRICS_CSV = "metrics.csv";
    private static final String THROUGHPUT_CSV = "throughput.csv";

    public static void main(String[] args) {
        ClientConfig config = ClientConfigLoader.fromArgs(args);
        new App().run(config);
    }

    private void run(ClientConfig config) {
        System.out.println("=== Client Part 2 Bootstrap ===");
        System.out.printf("Target server: %s%n", config.serverUri());
        System.out.printf("Warmup: %d threads × %d messages%n", config.warmupThreads(), config.warmupMessagesPerThread());
        System.out.printf("Main phase total messages: %,d%n", config.totalMessages());
        System.out.printf("Main phase threads: %d%n", config.effectiveMainThreads());
        System.out.println("Part 2: Detailed metrics collection ENABLED");

        // Create output directory
        createOutputDirectory();

        BlockingQueue<ChatMessage> messageQueue =
                new ArrayBlockingQueue<>(config.messageQueueCapacity());
        MetricsRecorder metrics = new MetricsRecorder();
        MetricsCollector detailedMetrics = new MetricsCollector();  // Part 2 metrics

        // Message generator thread
        MessageGenerator generator = new MessageGenerator(config, messageQueue);
        Thread generatorThread = new Thread(generator, "message-generator");
        generatorThread.setDaemon(true);
        generatorThread.start();

        // Executor for sender workers (warmup + main phase)
        ExecutorService senderPool = Executors.newCachedThreadPool();
        SenderOrchestrator orchestrator = new SenderOrchestrator(
            config, messageQueue, metrics, detailedMetrics, senderPool
        );

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            orchestrator.shutdown();
            generator.stop();
            senderPool.shutdownNow();
        }));

        // Mark test start for detailed metrics
        detailedMetrics.markTestStart();

        // Run the test
        orchestrator.runWarmupAndMainPhase();

        generator.stop();
        try {
            generatorThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        senderPool.shutdown();

        // Part 2: Analyze and export metrics
        analyzeAndExportMetrics(detailedMetrics);
    }

    /**
     * Performs statistical analysis and exports all metrics to CSV files.
     */
    private void analyzeAndExportMetrics(MetricsCollector collector) {
        System.out.println("\n=== Part 2: Analyzing Metrics ===");

        List<MessageMetric> allMetrics = collector.getAllMetrics();
        long testStartTimeNanos = collector.getTestStartTimeNanos();

        System.out.printf("Total metrics collected: %,d%n", allMetrics.size());

        // 1. Statistical Analysis
        System.out.println("\n[1/4] Calculating statistics...");
        StatisticsAnalyzer analyzer = new StatisticsAnalyzer();
        Statistics stats = analyzer.analyze(allMetrics);
        System.out.println(stats);

        // 2. Export detailed metrics to CSV
        System.out.println("\n[2/4] Exporting detailed metrics to CSV...");
        CsvExporter csvExporter = new CsvExporter();
        Path metricsPath = Paths.get(OUTPUT_DIR, METRICS_CSV);
        try {
            csvExporter.exportToCsvWithProgress(allMetrics, metricsPath.toString(), testStartTimeNanos, 50000);
            System.out.printf("✓ Metrics exported to: %s%n", metricsPath);
        } catch (IOException e) {
            System.err.printf("✗ Failed to export metrics: %s%n", e.getMessage());
        }

        // 3. Calculate and export throughput buckets
        System.out.println("\n[3/4] Calculating throughput over time...");
        ThroughputTracker throughputTracker = new ThroughputTracker();
        List<ThroughputTracker.ThroughputBucket> buckets =
            throughputTracker.calculateThroughputBuckets(allMetrics, testStartTimeNanos);

        System.out.printf("Total time buckets: %d (10-second intervals)%n", buckets.size());

        Path throughputPath = Paths.get(OUTPUT_DIR, THROUGHPUT_CSV);
        try {
            String csv = throughputTracker.exportToCsv(buckets);
            Files.writeString(throughputPath, csv);
            System.out.printf("✓ Throughput data exported to: %s%n", throughputPath);
        } catch (IOException e) {
            System.err.printf("✗ Failed to export throughput: %s%n", e.getMessage());
        }

        // 4. Print visualization instructions
        System.out.println("\n[4/4] Visualization");
        System.out.println("To generate throughput chart, run:");
        System.out.printf("  python3 visualize_throughput.py %s%n", throughputPath);

        System.out.println("\n=== Analysis Complete ===");
        System.out.printf("Output files saved in: %s/%n", OUTPUT_DIR);
    }

    /**
     * Creates the output directory if it doesn't exist.
     */
    private void createOutputDirectory() {
        try {
            Path outputPath = Paths.get(OUTPUT_DIR);
            if (!Files.exists(outputPath)) {
                Files.createDirectories(outputPath);
                System.out.printf("Created output directory: %s%n", outputPath.toAbsolutePath());
            }
        } catch (IOException e) {
            System.err.printf("Warning: Could not create output directory: %s%n", e.getMessage());
        }
    }
}
