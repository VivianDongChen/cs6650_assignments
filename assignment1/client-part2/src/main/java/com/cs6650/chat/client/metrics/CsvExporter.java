package com.cs6650.chat.client.metrics;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * Exports message metrics to CSV format.
 * CSV format: timestamp,messageType,latencyMs,statusCode,roomId
 */
public class CsvExporter {

    /**
     * Exports metrics to a CSV file.
     *
     * @param metrics list of message metrics to export
     * @param filename output CSV filename
     * @param testStartTimeNanos test start time in nanoseconds (from System.nanoTime())
     * @throws IOException if file write fails
     */
    public void exportToCsv(List<MessageMetric> metrics, String filename, long testStartTimeNanos) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            // Write CSV header
            writer.println("timestamp,messageType,latencyMs,statusCode,roomId");

            // Write each metric
            for (MessageMetric metric : metrics) {
                writer.printf("%d,%s,%d,%s,%d%n",
                    metric.getTimestampMs(testStartTimeNanos),
                    metric.getMessageType(),
                    metric.getLatencyMs(),
                    metric.getStatusCode(),
                    metric.getRoomId()
                );
            }
        }
    }

    /**
     * Exports metrics to CSV with progress indication for large datasets.
     *
     * @param metrics list of message metrics to export
     * @param filename output CSV filename
     * @param testStartTimeNanos test start time in nanoseconds
     * @param progressInterval print progress every N records (0 to disable)
     * @throws IOException if file write fails
     */
    public void exportToCsvWithProgress(List<MessageMetric> metrics, String filename,
                                       long testStartTimeNanos, int progressInterval) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("timestamp,messageType,latencyMs,statusCode,roomId");

            int count = 0;
            for (MessageMetric metric : metrics) {
                writer.printf("%d,%s,%d,%s,%d%n",
                    metric.getTimestampMs(testStartTimeNanos),
                    metric.getMessageType(),
                    metric.getLatencyMs(),
                    metric.getStatusCode(),
                    metric.getRoomId()
                );

                count++;
                if (progressInterval > 0 && count % progressInterval == 0) {
                    System.out.printf("  Exported %,d / %,d metrics (%.1f%%)%n",
                        count, metrics.size(), (count * 100.0) / metrics.size());
                }
            }
        }
    }
}
