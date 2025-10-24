# Client Part 2 – Instrumented WebSocket Load Client

This module extends the Assignment 1 Part 1 client with detailed performance instrumentation, CSV export, and throughput visualization. It reuses the same networking core (message generation, warmup/main phases, retry logic) and layers metrics collectors on top.

## Features

- Warmup (32 × 1,000) + configurable main workload (default 500,000 messages)
- Per-message latency capture, including message type, room, status code
- Percentile statistics (mean, median, p95, p99, min, max)
- 10-second throughput buckets with CSV export
- Python script (`visualize_throughput.py`) for generating throughput charts
- Designed for both local and EC2 testing against the Assignment server

## Build

```bash
cd client-part2
mvn -q clean package
```

Artifacts:
- `target/client-part2-1.0-SNAPSHOT-jar-with-dependencies.jar` – runnable shaded JAR
- `target/client-part2-1.0-SNAPSHOT.jar` – standard JAR (requires classpath deps)

## Usage

```bash
java -jar target/client-part2-1.0-SNAPSHOT-jar-with-dependencies.jar \
  --server-uri=ws://34.220.13.15:8080/chat-server/chat/1 \
  --total-messages=500000 \
  --main-threads=288 \
  --warmup-threads=32 \
  --warmup-messages-per-thread=1000 \
  --queue-capacity=10000 \
  --send-timeout=PT5S \
  --max-retries=5 \
  --initial-backoff=PT0.05S \
  --max-backoff=PT1S \
  --metrics-output=../results/part2-output/metrics.csv \
  --throughput-output=../results/part2-output/throughput.csv
```

Key CLI flags (defaults match assignment requirements):

| Flag | Description | Default |
|------|-------------|---------|
| `--server-uri` | Target WebSocket endpoint (`ws://host:port/chat-server/chat/roomId`) | `ws://localhost:8080/chat-server/chat/1` |
| `--total-messages` | Total messages to send (includes warmup) | `500000` |
| `--main-threads` | Number of sender workers after warmup | `288` |
| `--warmup-threads` | Warmup threads | `32` |
| `--warmup-messages-per-thread` | Messages per warmup thread | `1000` |
| `--queue-capacity` | Generator queue capacity | `10000` |
| `--max-retries` | Max retries per message | `5` |
| `--initial-backoff` / `--max-backoff` | Retry backoff range | `PT0.05S` / `PT1S` |
| `--metrics-output` | CSV path for per-message metrics | `metrics.csv` (cwd) |
| `--throughput-output` | CSV path for throughput buckets | `throughput.csv` (cwd) |

## Output Artifacts

- **Per-message CSV** (`metrics.csv`): `timestamp,messageType,latencyMs,statusCode,roomId`
- **Throughput CSV** (`throughput.csv`): `startTime,endTime,messageCount,messagesPerSecond`
- **Chart** (`../results/part2-output/throughput_chart.png`): Generated via `visualize_throughput.py`
- **Metrics summary**: Printed to stdout (successful/failed messages, retries, connections, runtime, throughput)

## Visualization

After running the client, generate the throughput chart:

```bash
python visualize_throughput.py \
  --input ../results/part2-output/throughput.csv \
  --output ../results/part2-output/throughput_chart.png
```

The script creates a dual-panel PNG showing throughput per bucket and cumulative message count.

## Notes

- The module shares code with Part 1 but remains self-contained for grading.
- CSV/PNG files committed under `results/part2-output/` provide the evidence referenced in the design document.
- For EC2 testing, ensure the security group allows your workstation’s IP on port 8080 and update `--server-uri` accordingly.
