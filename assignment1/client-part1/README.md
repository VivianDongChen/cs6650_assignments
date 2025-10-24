# Client Part 1

This module implements the Assignment 1 **Part 2 (Client Part 1)** multithreaded WebSocket client.  
Goals:

- Simulate 500 000 chat messages against the server.
- Honor the two-phase workload (warmup: 32 threads × 1000 msgs → main phase).
- Produce “basic metrics” output (success/failed counts, runtime, throughput, connection stats).

## High-Level Architecture

| Component | Purpose |
| --- | --- |
| `MessageGenerator` (single thread) | Pre-generates 500 000 messages into a bounded queue (capacity 10 000) using the required randomisation rules. |
| `SenderWorker` (worker threads) | Consumes messages, stamps client send time, and coordinates retry/backoff logic. |
| `ReliableWebSocketClient` | Thin wrapper around `java.net.http.WebSocket` that keeps connections open and handles reconnects. |
| `SenderOrchestrator` | Runs warmup (32 × 1 000) and main phases with dynamic work allocation per thread. |
| `MetricsRecorder` | Thread-safe counters + timer that produce the “basic metrics” summary. |
| `App` + `ClientConfigLoader` | CLI entry point + argument parsing/defaults. |

## Implementation Highlights

1. **Warmup + main orchestration** – `SenderOrchestrator` submits one worker per warmup thread, then redistributes the remaining messages across the configured main-phase thread count.
2. **Message generation contract** – `MessageGenerator` follows the spec (`userId` 1-100 000, `username` = `user<id>`, 50-template message pool, `roomId` 1-20, message type mix 90 % TEXT / 5 % JOIN / 5 % LEAVE, timestamp = `Instant.now()`).
3. **Backpressure control** – The bounded queue keeps around 10 000 prefetched messages so workers almost never block waiting for payloads.
4. **Retry and reconnection** – `SenderWorker` attempts each send up to 5 times with exponential backoff (`initial-backoff`, `max-backoff`) and forces a reconnect on transport errors.
5. **Metrics output** – `MetricsRecorder` tracks successes, failures (post-retry), total retries, connections opened, reconnections, runtime, and throughput; it logs a summary when the run completes.
6. **Configuration via flags** – Defaults match the assignment (32 warmup threads × 1 000, 500 000 total messages, etc.) and can be overridden with `--key=value` CLI args.

## Recommended Validation Steps

- Quick smoke test with a small workload (e.g. `--total-messages=1000 --main-threads=4`) to confirm connectivity.
- Full 500 000 message run against localhost; capture metrics output and screenshots (stored in `results/`).
- Reproduce the single-thread baseline for Little’s Law analysis (`--warmup-threads=1 --main-threads=1 --total-messages=1000`).
- Optional: run from an EC2 client in the same region as the server to compare WAN vs. LAN throughput.

## Running the client (sample)

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
mvn -q clean package
$JAVA_HOME/bin/java -jar target/client-part1-1.0-SNAPSHOT-shaded.jar \
  --server-uri=ws://localhost:8080/chat/1 \
  --warmup-threads=32 \
  --warmup-messages-per-thread=1000 \
  --main-threads=64 \
  --total-messages=500000
```

Additional flags:

| Flag | Description | Default |
| --- | --- | --- |
| `--queue-capacity` | Bounded queue size between generator and senders | 10 000 |
| `--send-timeout` | ISO-8601 duration for awaiting server ack | `PT10S` |
| `--max-retries` | Max retries per message before counting as failure | 5 |
| `--initial-backoff` | First backoff duration (`PT0.1S`) | `PT0.1S` |
| `--max-backoff` | Maximum backoff duration | `PT5S` |

## Future Enhancements

- Add per-message latency capture + CSV export (Part 3 preparation).
- Integrate structured logging and optional progress reporting.
- Replace ad-hoc CLI parsing with a library (Picocli) for better validation/help output.
