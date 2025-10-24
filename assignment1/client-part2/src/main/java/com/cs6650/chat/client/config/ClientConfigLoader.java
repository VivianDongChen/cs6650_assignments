package com.cs6650.chat.client.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Parses {@link ClientConfig} from CLI arguments or environment variables.
 */
public final class ClientConfigLoader {

    private static final String DEFAULT_SERVER_URI = "ws://localhost:8080/chat/1";
    private static final int DEFAULT_WARMUP_THREADS = 32;
    private static final int DEFAULT_WARMUP_MSGS_PER_THREAD = 1000;
    private static final long DEFAULT_TOTAL_MESSAGES = 500_000L;
    private static final int DEFAULT_QUEUE_CAPACITY = 10_000;
    private static final int DEFAULT_MAIN_THREADS = 64;
    private static final Duration DEFAULT_SEND_TIMEOUT = Duration.ofSeconds(10);
    private static final int DEFAULT_MAX_RETRIES = 5;
    private static final Duration DEFAULT_INITIAL_BACKOFF = Duration.ofMillis(100);
    private static final Duration DEFAULT_MAX_BACKOFF = Duration.ofSeconds(5);

    private ClientConfigLoader() {
    }

    public static ClientConfig fromArgs(String[] args) {
        Map<String, String> parsed = parseArgs(args);
        String serverUri = parsed.getOrDefault("server-uri", DEFAULT_SERVER_URI);
        try {
            return new ClientConfig(
                    new URI(serverUri),
                    asInt(parsed, "warmup-threads", DEFAULT_WARMUP_THREADS),
                    asInt(parsed, "warmup-messages-per-thread", DEFAULT_WARMUP_MSGS_PER_THREAD),
                    asLong(parsed, "total-messages", DEFAULT_TOTAL_MESSAGES),
                    asInt(parsed, "queue-capacity", DEFAULT_QUEUE_CAPACITY),
                    asInt(parsed, "main-threads", DEFAULT_MAIN_THREADS),
                    asDuration(parsed, "send-timeout", DEFAULT_SEND_TIMEOUT),
                    asInt(parsed, "max-retries", DEFAULT_MAX_RETRIES),
                    asDuration(parsed, "initial-backoff", DEFAULT_INITIAL_BACKOFF),
                    asDuration(parsed, "max-backoff", DEFAULT_MAX_BACKOFF)
            );
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid server URI: " + serverUri, e);
        }
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--") || !arg.contains("=")) {
                continue;
            }
            String[] parts = arg.substring(2).split("=", 2);
            if (parts.length == 2) {
                map.put(parts[0], parts[1]);
            }
        }
        return map;
    }

    private static int asInt(Map<String, String> source, String key, int defaultValue) {
        String value = source.get(key);
        return value == null ? defaultValue : Integer.parseInt(value);
    }

    private static long asLong(Map<String, String> source, String key, long defaultValue) {
        String value = source.get(key);
        return value == null ? defaultValue : Long.parseLong(value);
    }

    private static Duration asDuration(Map<String, String> source, String key, Duration defaultValue) {
        String value = source.get(key);
        return value == null ? defaultValue : Duration.parse(value);
    }
}
