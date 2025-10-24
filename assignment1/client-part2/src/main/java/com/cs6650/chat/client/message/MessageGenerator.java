package com.cs6650.chat.client.message;

import com.cs6650.chat.client.config.ClientConfig;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import java.util.UUID;

/**
 * Generates random messages following the assignment specification and places them into a queue
 * for sender workers to consume.
 */
public final class MessageGenerator implements Runnable {

    private static final List<String> MESSAGE_TEMPLATES = List.copyOf(buildTemplates());

    private final ClientConfig config;
    private final BlockingQueue<ChatMessage> queue;
    private final AtomicLong generatedCount = new AtomicLong();
    private final AtomicBoolean running = new AtomicBoolean(true);

    public MessageGenerator(ClientConfig config, BlockingQueue<ChatMessage> queue) {
        this.config = config;
        this.queue = queue;
    }

    @Override
    public void run() {
        long target = config.totalMessages();
        try {
            while (running.get() && generatedCount.get() < target) {
                ChatMessage message = randomMessage();
                queue.put(message);
                generatedCount.incrementAndGet();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    public void stop() {
        running.set(false);
    }

    private ChatMessage randomMessage() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int userId = random.nextInt(1, 100_001);
        String username = "user" + userId;
        String template = MESSAGE_TEMPLATES.get(random.nextInt(MESSAGE_TEMPLATES.size()));
        String messageType = randomMessageType(random);
        int roomId = random.nextInt(1, 21);

        return new ChatMessage(
                UUID.randomUUID().toString(),
                null, // sender worker will populate clientSendTime before sending
                userId,
                username,
                template,
                Instant.now(),
                messageType,
                roomId
        );
    }

    private static String randomMessageType(ThreadLocalRandom random) {
        int roll = random.nextInt(100);
        if (roll < 5) {
            return "JOIN";
        } else if (roll < 10) {
            return "LEAVE";
        }
        return "TEXT";
    }

    private static List<String> buildTemplates() {
        List<String> templates = new ArrayList<>(50);
        templates.addAll(List.of(
                "Hello everyone!",
                "Anyone up for a standup meeting?",
                "Don't forget to commit your changes.",
                "Deploying the latest build now.",
                "Lunch time suggestions?",
                "Reminder: code freeze tonight.",
                "QA found a blocker issue.",
                "Let's pair on the WebSocket bug.",
                "Sprint demo at 3pm.",
                "Docs are updated in Confluence.")
        );
        // Generate simple numbered templates to reach 50 entries.
        int remaining = 50 - templates.size();
        IntStream.range(0, remaining)
                .mapToObj(i -> "Auto message template #" + (i + 1))
                .forEach(templates::add);
        return templates;
    }
}
