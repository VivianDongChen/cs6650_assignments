package com.cs6650.chat.consumer.database;

import com.cs6650.chat.consumer.model.QueueMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Batch message writer for PostgreSQL.
 * Accumulates messages and writes them in batches for better performance.
 */
public class BatchMessageWriter {
    private static final Logger logger = LoggerFactory.getLogger(BatchMessageWriter.class);

    private final DatabaseConnectionPool connectionPool;
    private final int batchSize;
    private final long flushIntervalMs;
    private final BlockingQueue<QueueMessage> messageQueue;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService writerExecutor;
    private volatile boolean running;

    // Statistics
    private final AtomicLong totalMessagesWritten = new AtomicLong(0);
    private final AtomicLong totalBatchesWritten = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);

    // SQL query
    private static final String INSERT_SQL =
        "INSERT INTO messages (message_id, room_id, user_id, content, timestamp, created_at) " +
        "VALUES (?, ?, ?, ?, ?, ?) " +
        "ON CONFLICT (message_id) DO NOTHING";

    /**
     * Constructor with configurable batch size and flush interval.
     */
    public BatchMessageWriter(DatabaseConnectionPool connectionPool,
                             int batchSize,
                             long flushIntervalMs) {
        this.connectionPool = connectionPool;
        this.batchSize = batchSize;
        this.flushIntervalMs = flushIntervalMs;
        this.messageQueue = new LinkedBlockingQueue<>(batchSize * 10); // Buffer capacity
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "BatchWriter-Scheduler");
            t.setDaemon(true);
            return t;
        });
        this.writerExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "BatchWriter-Worker");
            t.setDaemon(true);
            return t;
        });
        this.running = true;

        logger.info("BatchMessageWriter initialized: batchSize={}, flushInterval={}ms",
                   batchSize, flushIntervalMs);
    }

    /**
     * Start the batch writer.
     */
    public void start() {
        logger.info("Starting BatchMessageWriter...");

        // Start periodic flush
        scheduler.scheduleAtFixedRate(
            this::flushPeriodically,
            flushIntervalMs,
            flushIntervalMs,
            TimeUnit.MILLISECONDS
        );

        // Start batch processor
        writerExecutor.submit(this::processBatches);

        logger.info("BatchMessageWriter started successfully");
    }

    /**
     * Add a message to the write queue.
     */
    public boolean addMessage(QueueMessage message) {
        if (!running) {
            logger.warn("BatchWriter is not running, message rejected");
            return false;
        }

        try {
            boolean added = messageQueue.offer(message, 1, TimeUnit.SECONDS);
            if (!added) {
                logger.warn("Failed to add message to queue (queue full): {}", message.getMessageId());
            }
            return added;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while adding message to queue", e);
            return false;
        }
    }

    /**
     * Process batches continuously.
     */
    private void processBatches() {
        logger.info("Batch processor started");
        List<QueueMessage> batch = new ArrayList<>(batchSize);

        while (running) {
            try {
                // Wait for first message
                QueueMessage firstMessage = messageQueue.poll(100, TimeUnit.MILLISECONDS);
                if (firstMessage == null) {
                    continue;
                }

                batch.add(firstMessage);

                // Collect more messages up to batch size
                messageQueue.drainTo(batch, batchSize - 1);

                // Write batch
                if (!batch.isEmpty()) {
                    writeBatch(batch);
                    batch.clear();
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.info("Batch processor interrupted");
                break;
            } catch (Exception e) {
                logger.error("Error in batch processor", e);
            }
        }

        logger.info("Batch processor stopped");
    }

    /**
     * Flush messages periodically.
     */
    private void flushPeriodically() {
        try {
            int queueSize = messageQueue.size();
            if (queueSize > 0) {
                logger.debug("Periodic flush triggered, queue size: {}", queueSize);
            }
        } catch (Exception e) {
            logger.error("Error in periodic flush", e);
        }
    }

    /**
     * Write a batch of messages to the database.
     */
    private void writeBatch(List<QueueMessage> messages) {
        if (messages.isEmpty()) {
            return;
        }

        long startTime = System.currentTimeMillis();
        Connection conn = null;
        PreparedStatement pstmt = null;

        try {
            conn = connectionPool.getConnection();
            pstmt = conn.prepareStatement(INSERT_SQL);

            Timestamp now = Timestamp.from(Instant.now());

            for (QueueMessage msg : messages) {
                pstmt.setString(1, msg.getMessageId());
                pstmt.setInt(2, msg.getRoomIdAsInt());
                pstmt.setString(3, msg.getUserId());
                pstmt.setString(4, msg.getContent());
                pstmt.setTimestamp(5, Timestamp.from(msg.getTimestamp()));
                pstmt.setTimestamp(6, now);
                pstmt.addBatch();
            }

            int[] results = pstmt.executeBatch();
            conn.commit();

            long duration = System.currentTimeMillis() - startTime;
            int written = countSuccessful(results);

            totalMessagesWritten.addAndGet(written);
            totalBatchesWritten.incrementAndGet();

            logger.info("Batch written: {} messages in {}ms (avg: {}ms/msg)",
                       written, duration, written > 0 ? duration / written : 0);

            if (written < messages.size()) {
                logger.warn("Some messages were skipped (duplicates): {} out of {}",
                           messages.size() - written, messages.size());
            }

        } catch (SQLException e) {
            totalErrors.incrementAndGet();
            logger.error("Failed to write batch of {} messages", messages.size(), e);

            // Rollback on error
            if (conn != null) {
                try {
                    conn.rollback();
                    logger.info("Transaction rolled back");
                } catch (SQLException rollbackEx) {
                    logger.error("Failed to rollback transaction", rollbackEx);
                }
            }

            // TODO: Consider implementing retry logic or DLQ for failed batches

        } finally {
            // Close resources
            if (pstmt != null) {
                try {
                    pstmt.close();
                } catch (SQLException e) {
                    logger.error("Failed to close PreparedStatement", e);
                }
            }
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    logger.error("Failed to close Connection", e);
                }
            }
        }
    }

    /**
     * Count successful inserts from batch results.
     */
    private int countSuccessful(int[] results) {
        int count = 0;
        for (int result : results) {
            if (result > 0 || result == PreparedStatement.SUCCESS_NO_INFO) {
                count++;
            }
        }
        return count;
    }

    /**
     * Get writer statistics.
     */
    public WriterStats getStats() {
        return new WriterStats(
            totalMessagesWritten.get(),
            totalBatchesWritten.get(),
            totalErrors.get(),
            messageQueue.size()
        );
    }

    /**
     * Shutdown the batch writer.
     */
    public void shutdown() {
        logger.info("Shutting down BatchMessageWriter...");
        running = false;

        // Shutdown scheduler
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }

        // Shutdown writer executor
        writerExecutor.shutdown();
        try {
            if (!writerExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                writerExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            writerExecutor.shutdownNow();
        }

        // Flush remaining messages
        List<QueueMessage> remaining = new ArrayList<>();
        messageQueue.drainTo(remaining);
        if (!remaining.isEmpty()) {
            logger.info("Flushing {} remaining messages", remaining.size());
            writeBatch(remaining);
        }

        logger.info("BatchMessageWriter shutdown complete. Final stats: {}", getStats());
    }

    /**
     * Writer statistics data class.
     */
    public static class WriterStats {
        public final long totalMessagesWritten;
        public final long totalBatchesWritten;
        public final long totalErrors;
        public final int queueSize;

        public WriterStats(long messagesWritten, long batchesWritten, long errors, int queueSize) {
            this.totalMessagesWritten = messagesWritten;
            this.totalBatchesWritten = batchesWritten;
            this.totalErrors = errors;
            this.queueSize = queueSize;
        }

        @Override
        public String toString() {
            return String.format("WriterStats[messages=%d, batches=%d, errors=%d, queueSize=%d]",
                    totalMessagesWritten, totalBatchesWritten, totalErrors, queueSize);
        }
    }
}
