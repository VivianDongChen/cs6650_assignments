# Assignment 1 - Test Results and Analysis

This folder contains test evidence, metrics, and performance analysis for Assignment 1.

Design document: [design-document.md](design-document.md) • [PDF](design-document.pdf)
Architecture image: [architecture.png](architecture.png)

---

## Part 1: Server Implementation and Validation

### Server Deployment Evidence

- [`server-health-local.png`](server-health-local.png) – Local health check via `curl http://localhost:8080/chat-server/health`
- [`server-health-ec2.png`](server-health-ec2.png) – EC2 health check via `curl http://34.220.13.15:8080/chat-server/health`
- [`server-websocket-success.png`](server-websocket-success.png) – Successful WebSocket message echo (Postman)
- [`server-websocket-invalid.png`](server-websocket-invalid.png) – Validation error example (missing required fields)

**Server Environment:**
- **Local**: Apache Tomcat 10.1.24, running on localhost:8080
- **EC2**: AWS EC2 instance (us-west-2), t3.micro, running on 34.220.13.15:8080

---

## Part 2: Multithreaded Client Performance Testing

### Common Test Configuration

All tests follow the required architecture:
- **Warmup Phase**: 32 threads × 1,000 messages each = 32,000 messages
- **Main Phase**: Variable thread count sending remaining messages to reach 500,000 total
- **Message Generation**: Single dedicated thread generates 500,000 messages in advance and places them in a thread-safe queue (capacity 10,000)
- **Message Payloads**:
  - `userId`: 1-100,000 (random)
  - `username`: `user<id>` format
  - `message`: 50 pre-defined message templates (random selection)
  - `roomId`: 1-20 (random)
  - `messageType`: 90% TEXT, 5% JOIN, 5% LEAVE
  - `timestamp`: `Instant.now()` in ISO-8601 format
- **Connection Management**: WebSocket connection pooling with reconnection on failure
- **Error Handling**: Retry up to 5 times with exponential backoff (PT0.05S to PT1S); exceeded retries counted as failures

---

## 2.1 Local Server Testing

### Test Environment
- **Server**: Local Tomcat (localhost:8080)
- **Client**: Local workstation
- **Network**: Loopback interface (no network latency)

### Single-Thread Baseline Test

**Configuration:**
```bash
java -jar target/client-part1-1.0-SNAPSHOT-shaded.jar \
  --server-uri=ws://localhost:8080/chat-server/chat/1 \
  --warmup-threads=1 \
  --warmup-messages-per-thread=1 \
  --main-threads=1 \
  --total-messages=1000 \
  --queue-capacity=100 \
  --send-timeout=PT5S \
  --max-retries=3 \
  --initial-backoff=PT0.05S \
  --max-backoff=PT0.2S
```

**Result:**
- Runtime: 1,916 ms
- Throughput: 521.92 msg/s
- **Average service time:** `W_local = 1.916 ms`

Screenshot: [client-part1-single-thread.png](client-part1-single-thread.png)

### Multi-Thread Performance Results

| Main Threads | Runtime (ms) | Throughput (msg/s) | Total Retries | Reconnections | Connections Opened | Screenshot |
|-------------|--------------|-------------------|---------------|---------------|-------------------|------------|
| 48  | 31,132 | 16,060.64 | 12,269 | 12,269 | 80  | [client-part1-main48.png](client-part1-main48.png) |
| 64  | 24,468 | 20,434.85 | 12,234 | 12,234 | 96  | [client-part1-main64.png](client-part1-main64.png) |
| 96  | 18,260 | 27,382.26 | 12,250 | 12,250 | 128 | [client-part1-main96.png](client-part1-main96.png) |
| 128 | 16,526 | **30,255.36** | 12,253 | 12,253 | 160 | [client-part1-main128.png](client-part1-main128.png) |
| 160 | 17,700 | 28,248.59 | 12,290 | 12,290 | 192 | [client-part1-main160.png](client-part1-main160.png) |

**Key Observations:**
- All 500,000 messages completed successfully (0 failures) across all configurations
- Approximately 12k retries/reconnections occur consistently across all thread counts
- Throughput peaks at **128 threads (~30,255 msg/s)**
- 160 threads shows diminishing returns (28,249 msg/s)

### Local Little's Law Analysis

Using Little's Law: `λ = L / W`
- `L` = number of concurrent workers (main phase threads)
- `W` = average service time (1.916 ms from single-thread test)
- `λ` = predicted throughput

| Main Threads (L) | Observed λ (msg/s) | Predicted λ = L / W (msg/s) | Efficiency (%) | Analysis |
|-----------------|-------------------|----------------------------|---------------|----------|
| 48  | 16,061 | ≈ 25,052 | 64.1% | Contention and retry overhead reduce throughput |
| 64  | 20,435 | ≈ 33,403 | 61.2% | Overhead persists with more threads |
| 96  | 27,382 | ≈ 50,104 | 54.7% | Increasing synchronization costs |
| 128 | 30,255 | ≈ 66,805 | 45.3% | **Optimal: Best throughput despite lower efficiency** |
| 160 | 28,249 | ≈ 83,507 | 33.8% | Diminishing returns evident |

**Local Analysis Summary:**
1. **Efficiency decreases with scale**: From 64% (48 threads) to 45% (128 threads) to 34% (160 threads)
2. **Optimal configuration**: 128 threads provides best absolute throughput (~30k msg/s)
3. **Retry impact**: Consistent ~12k retries across all tests indicate server-side backpressure
4. **Scalability ceiling**: Beyond 128 threads, thread contention outweighs parallelism benefits

### Local Connection Statistics (128 threads - Optimal)
- **Total connections opened:** 160
- **Successful messages:** 500,000
- **Failed messages:** 0
- **Total retries:** 12,253
- **Reconnections:** 12,253
- **Overall runtime:** 16,526 ms
- **Effective throughput:** 30,255.36 msg/s

---

## 2.2 EC2 Server Testing

### Test Environment
- **Server**: AWS EC2 (us-west-2), t3.micro instance, IP: 34.220.13.15
- **Client**: Local workstation
- **Network**: Public internet connection with variable latency

### Single-Thread Baseline Test (EC2)

**Configuration:**
```bash
java -jar target/client-part1-1.0-SNAPSHOT-shaded.jar \
  --server-uri=ws://34.220.13.15:8080/chat-server/chat/1 \
  --warmup-threads=1 \
  --warmup-messages-per-thread=1 \
  --main-threads=1 \
  --total-messages=1000 \
  --queue-capacity=100 \
  --send-timeout=PT5S \
  --max-retries=3 \
  --initial-backoff=PT0.05S \
  --max-backoff=PT0.2S
```

**Result:**
- Runtime: 17,371 ms
- Throughput: 57.57 msg/s
- **Average service time (with network latency):** `W_ec2 = 17.371 ms`
- **Network latency overhead:** +15.455 ms vs local (+806%)

Screenshot: [client-part1-ec2-single-thread.png](client-part1-ec2-single-thread.png)

### Multi-Thread Performance Results (EC2)

| Main Threads | Runtime (ms) | Throughput (msg/s) | Total Retries | Reconnections | Connections Opened | Failed Messages | Screenshot |
|-------------|--------------|-------------------|---------------|---------------|-------------------|-----------------|------------|
| 224 | 74,107 | 6,747.00 | 12,239 | 12,239 | 256 | 0 | [client-part1-ec2-main224.png](client-part1-ec2-main224.png) |
| 256 | 65,043 | 7,687.22 | 12,098 | 12,098 | 288 | 0 | [client-part1-ec2-main256.png](client-part1-ec2-main256.png) |
| 288 | 62,967 | **7,940.67** | 12,114 | 12,102 | 320 | 0 | [client-part1-ec2-main288.png](client-part1-ec2-main288.png) |
| 320 | 91,426 | 5,325.08 | 11,974 | 11,807 | 343 | 9 | [client-part1-ec2-main320.png](client-part1-ec2-main320.png) |

**Key Observations:**
- **Optimal configuration**: 288 threads achieved peak throughput of **7,940.67 msg/s**
- All 500,000 messages completed successfully up to 288 threads (0 failures)
- At 320 threads, system became overloaded:
  - 9 failed messages (486,842 successful out of 500,000 total)
  - Numerous "HTTP/1.1 header parser received no bytes" and timeout errors
  - Performance degraded 42% from peak (7,941 → 5,325 msg/s)
- EC2 requires **2.25x more threads** (288 vs 128) compared to local testing

### EC2 Little's Law Analysis

Using Little's Law: `λ = L / W`
- `L` = number of concurrent workers (main phase threads)
- `W` = average service time (17.371 ms from EC2 single-thread test)
- `λ` = predicted throughput

| Main Threads (L) | Observed λ (msg/s) | Predicted λ = L / W (msg/s) | Efficiency (%) | Analysis |
|-----------------|-------------------|----------------------------|---------------|----------|
| 224 | 6,747 | ≈ 12,900 | 52.3% | Network latency overhead visible |
| 256 | 7,687 | ≈ 14,739 | 52.2% | Consistent efficiency maintained |
| 288 | 7,941 | ≈ 16,582 | 47.9% | **Optimal: Peak throughput achieved** |
| 320 | 5,325 | ≈ 18,424 | 28.9% | System overload causes dramatic collapse |

**EC2 Analysis Summary:**
1. **Network latency impact**: 17.371 ms vs 1.916 ms local (+15.455 ms, 806% increase)
2. **Higher efficiency**: EC2 maintains ~48-52% efficiency (vs 45% local) due to network latency hiding contention
3. **Optimal configuration**: 288 threads provides peak throughput (7,941 msg/s)
4. **Hard limit at 320**: Connection timeouts and failures indicate system bottleneck
5. **Latency compensation**: Higher concurrency effectively hides network latency

### EC2 Connection Statistics (288 threads - Optimal)
- **Total connections opened:** 320
- **Successful messages:** 500,000
- **Failed messages:** 0
- **Total retries:** 12,114
- **Reconnections:** 12,102
- **Overall runtime:** 62,967 ms
- **Effective throughput:** 7,940.67 msg/s

---

## 2.3 Performance Comparison: Local vs EC2

### Summary Table

| Environment | Optimal Threads | Peak Throughput | Service Time (1-thread) | Peak Efficiency |
|-------------|----------------|-----------------|------------------------|----------------|
| **Local** | 128 | 30,255 msg/s | 1.916 ms | 45.3% |
| **EC2** | 288 | 7,941 msg/s | 17.371 ms | 47.9% |
| **Difference** | +160 (+125%) | -22,314 (-74%) | +15.455 ms (+806%) | +2.6% |

### Detailed Comparison

| Metric | Local Server | EC2 Server | Difference |
|--------|--------------|------------|------------|
| Single-thread latency | 1.916 ms | 17.371 ms | +15.455 ms (+806%) |
| Optimal thread count | 128 | 288 | +160 threads (+125%) |
| Peak throughput | 30,255 msg/s | 7,941 msg/s | -22,314 msg/s (-74%) |
| Peak efficiency | 45.3% | 47.9% | +2.6% |
| Network factor | Localhost (loopback) | Public Internet | Cross-region WAN |
| Retry/Reconnection rate | ~12,253 per 500k msgs | ~12,114 per 500k msgs | Consistent behavior |
| Failure threshold | >160 threads (graceful) | >288 threads (hard limit) | EC2 hits connection limits |

### Critical Insights

1. **Little's Law Validation**:
   - Both environments follow Little's Law predictions with ~45-50% efficiency
   - Indicates consistent system behavior and proper concurrency design
   - Validates theoretical modeling approach

2. **Network Latency Dominates Performance**:
   - 15.5ms network RTT vs 1.9ms local latency (9x increase)
   - Results in 74% throughput reduction on EC2
   - Demonstrates real-world WAN overhead

3. **Concurrency Compensates for Latency**:
   - EC2 requires 2.25x more threads (288 vs 128) to achieve optimal throughput
   - Higher concurrency effectively "hides" network latency
   - Efficiency actually improves slightly (47.9% vs 45.3%) due to reduced contention

4. **Scalability Limits**:
   - **Local**: Graceful degradation beyond 128 threads, still functional at 160
   - **EC2**: Hard failure at 320 threads (connection timeouts, message failures)
   - Suggests different bottlenecks: local = CPU contention, EC2 = connection limits

5. **Consistent Retry Behavior**:
   - ~12k retries in both environments
   - Indicates server-side backpressure handling rather than client issues
   - Shows robust error handling across network conditions

6. **Efficiency Paradox**:
   - EC2 shows slightly **higher** efficiency (47.9% vs 45.3%) despite lower throughput
   - Network latency increases service time uniformly, reducing relative impact of contention
   - Suggests client-side threading model scales well to distributed environments

### Practical Implications

- **Distributed deployment** reduces raw throughput but maintains efficiency
- **Network latency** can be mitigated by increasing concurrency levels
- **Connection pooling** becomes critical in high-latency environments
- **Real-world testing** reveals bottlenecks not visible in local testing
- **System design** should account for 3-5x latency increase in WAN deployments

---

## Part 3: Detailed Performance Analysis and Metrics Collection

### Overview

Part 3 extends Part 2 with detailed per-message metrics collection, statistical analysis, and throughput visualization. The client-part2 implementation adds comprehensive instrumentation without modifying the server.

### Implementation Architecture

**Key Components:**
1. **MessageMetric** - Immutable data class storing per-message performance data
   - Fields: messageId, messageType, roomId, sendTimeNanos, receiveTimeNanos, latencyMs, statusCode
   - Calculated latency: `latencyMs = (receiveTimeNanos - sendTimeNanos) / 1_000_000`

2. **MetricsCollector** - Thread-safe collector using `ConcurrentLinkedQueue`
   - Lock-free design for high-performance concurrent collection
   - Records test start time for accurate timestamp calculation
   - Collects all 500,000 message metrics in memory

3. **StatisticsAnalyzer** - Calculates statistical metrics
   - Mean, median, p95, p99, min, max latencies
   - Message type distribution (TEXT, JOIN, LEAVE)
   - Throughput per room
   - Uses linear interpolation for percentile calculations

4. **ThroughputTracker** - Time series analysis
   - Groups messages into 10-second buckets
   - Calculates messages/second for each bucket
   - Exports data for visualization

5. **CsvExporter** - Exports metrics to CSV format
   - Format: `timestamp,messageType,latencyMs,statusCode,roomId`
   - Progress tracking for large datasets (500K messages)

6. **visualize_throughput.py** - Python visualization script
   - Generates throughput-over-time line chart
   - Displays cumulative message count
   - Shows mean, peak, min throughput statistics

### EC2 Test Results (288 Threads - Optimal Configuration)

**Test Configuration:**
```bash
java -jar target/client-part2-1.0-SNAPSHOT-jar-with-dependencies.jar \
  --server-uri=ws://34.220.13.15:8080/chat-server/chat/1 \
  --total-messages=500000 \
  --main-threads=288 \
  --warmup-threads=20 \
  --warmup-messages-per-thread=10
```

**Environment:**
- **Server**: AWS EC2 (us-west-2), Tomcat 9, IP: 34.220.13.15
- **Client**: Local workstation
- **Network**: Public internet with ~15ms baseline RTT

### Performance Summary

| Metric | Value |
|--------|-------|
| **Total messages** | 500,000 |
| **Successful messages** | 500,000 (100.00%) |
| **Failed messages** | 0 (0.00%) |
| **Total retries** | 12,201 |
| **Connections opened** | 308 |
| **Reconnections** | 12,201 |
| **Overall runtime** | 51,182 ms (51.2 seconds) |
| **Average throughput** | 9,769.06 msg/s |

### Latency Statistics

| Metric | Latency (ms) |
|--------|-------------|
| **Mean** | 21.40 |
| **Median** | 19.00 |
| **95th percentile (p95)** | 36.00 |
| **99th percentile (p99)** | 74.00 |
| **Minimum** | 9 |
| **Maximum** | 263 |

**Key Observations:**
- Median latency (19ms) is close to single-thread baseline (17.371ms), indicating efficient concurrency
- p95 latency (36ms) shows most messages complete within 2x median time
- p99 latency (74ms) indicates some messages experience retry/reconnection overhead
- Max latency (263ms) likely corresponds to messages requiring multiple retries

### Message Type Distribution

| Message Type | Count | Percentage |
|-------------|-------|------------|
| **TEXT** | 450,010 | 90.00% |
| **JOIN** | 25,010 | 5.00% |
| **LEAVE** | 24,980 | 5.00% |

Distribution matches expected 90/5/5 ratio perfectly, validating message generation logic.

### Throughput Per Room (Top 10)

| Room ID | Message Count | Percentage |
|---------|--------------|------------|
| Room 1 | 25,344 | 5.07% |
| Room 19 | 25,248 | 5.05% |
| Room 3 | 25,117 | 5.02% |
| Room 13 | 25,103 | 5.02% |
| Room 7 | 25,090 | 5.02% |
| Room 18 | 25,081 | 5.02% |
| Room 20 | 25,075 | 5.02% |
| Room 9 | 25,044 | 5.01% |
| Room 4 | 25,028 | 5.01% |
| Room 10 | 25,006 | 5.00% |

**Analysis:**
- 20 rooms receive approximately equal distribution (~25,000 messages each)
- Variance is minimal (25,006 - 25,344 range = 338 messages, 1.3% variation)
- Confirms uniform random room selection in message generation

### Throughput Over Time (10-Second Buckets)

| Time Bucket | Message Count | Throughput (msg/s) |
|-------------|--------------|-------------------|
| 0-10s | 74,557 | 7,455.70 |
| 10-20s | 105,994 | **10,599.40** (peak) |
| 20-30s | 109,719 | **10,971.90** (peak) |
| 30-40s | 101,285 | 10,128.50 |
| 40-50s | 107,413 | 10,741.30 |
| 50-60s | 1,032 | 103.20 (tail) |

**Throughput Chart:** See [throughput_chart.png](./part2-output/throughput_chart.png)

**Key Insights:**
1. **Warmup Period (0-10s)**: 7,456 msg/s as threads initialize and connections establish
2. **Peak Performance (10-50s)**: Sustained 10,000-11,000 msg/s throughput
3. **Tail Period (50-60s)**: Only 1,032 messages remaining, showing efficient completion
4. **Average Throughput**: 8,333.33 msg/s (500,000 messages / 60 seconds)
5. **Peak Throughput**: 10,971.90 msg/s (at 20-30s bucket)

### Comparison: Part 1 vs Part 2 Performance

| Metric | Part 1 (288 threads) | Part 2 (288 threads) | Difference |
|--------|---------------------|---------------------|------------|
| **Runtime** | 62,967 ms | 51,182 ms | -11,785 ms (-18.7%) |
| **Average Throughput** | 7,940.67 msg/s | 9,769.06 msg/s | +1,828.39 msg/s (+23.0%) |
| **Peak Throughput** | N/A | 10,971.90 msg/s | Measured in Part 2 |
| **Total Retries** | 12,114 | 12,201 | +87 (+0.7%) |
| **Reconnections** | 12,102 | 12,201 | +99 (+0.8%) |
| **Connections Opened** | 320 | 308 | -12 (-3.8%) |
| **Failed Messages** | 0 | 0 | No failures |

**Performance Improvement Analysis:**
- **23% throughput increase** in Part 2 likely due to:
  - More efficient connection pooling with fewer total connections
  - Optimized retry logic
  - Slightly different network conditions during test run
- **Retry behavior remains consistent** (~12k retries per 500k messages)
- **Zero failures** in both tests confirm system stability

### Statistical Analysis Insights

1. **Latency Distribution**:
   - Mean (21.4ms) is slightly higher than median (19ms), indicating right-skewed distribution
   - Most messages complete quickly, with a small tail of high-latency outliers
   - Network latency contributes ~17ms baseline (from Part 1 single-thread test)
   - Additional 4ms mean overhead from concurrency, queueing, and retries

2. **Percentile Analysis**:
   - 95% of messages complete within 36ms (1.9x median)
   - 99% of messages complete within 74ms (3.9x median)
   - 1% of messages (5,000 out of 500,000) exceed 74ms
   - Tail latencies correlate with retry attempts

3. **Throughput Stability**:
   - After initial warmup, throughput remains stable at 10,000-11,000 msg/s
   - Coefficient of variation: (4,233.75 / 8,333.33) = 50.8% (includes warmup/tail)
   - Excluding warmup/tail, steady-state CV < 5%

4. **System Efficiency**:
   - Mean latency (21.4ms) × Peak throughput (10,971.9 msg/s) = ~235 concurrent requests
   - Configured threads: 288
   - Utilization: 235 / 288 = 81.6% effective concurrency
   - Suggests good thread pool sizing with minimal idle threads

### Comparison with Part 2 Local Testing (From Part 1)

| Environment | Throughput | Mean Latency | Optimal Threads | Efficiency |
|-------------|-----------|--------------|-----------------|------------|
| **Local** | 30,255 msg/s | ~1.9 ms | 128 | 45.3% |
| **EC2 (Part 2)** | 9,769 msg/s | 21.4 ms | 288 | ~47.9% |
| **Difference** | -67.7% | +1026% | +125% | +2.6% |

**Observations:**
- Network latency multiplies end-to-end latency by ~11x (21.4ms vs 1.9ms)
- Compensating with 2.25x more threads (288 vs 128) recovers efficiency
- EC2 throughput is 32% of local throughput (9,769 / 30,255)
- Part 2's 9,769 msg/s exceeds Part 1's EC2 result (7,941 msg/s) by 23%

### Exported Data Files

1. [**metrics.csv**](./part2-output/metrics.csv) (12 MB, 500,000 rows)
   - Format: `timestamp,messageType,latencyMs,statusCode,roomId`
   - Complete per-message performance data
   - Suitable for offline analysis, dashboards, or time-series databases

2. [**throughput.csv**](./part2-output/throughput.csv) (174 bytes, 6 rows)
   - Format: `startTime,endTime,messageCount,messagesPerSecond`
   - 10-second bucket aggregation
   - Ready for plotting and visualization

3. [**throughput_chart.png**](./part2-output/throughput_chart.png)
   - Dual-panel visualization:
     - Panel 1: Throughput over time (line chart)
     - Panel 2: Cumulative message count
   - Includes summary statistics (total, avg, peak, min throughput)

### Visualization

The generated chart shows:
- **Clear warmup phase**: Throughput ramps from 0 to peak in first 10 seconds
- **Stable peak performance**: 10,000-11,000 msg/s sustained for 40 seconds
- **Efficient completion**: Rapid tail-off as final messages complete

### Key Findings

1. **System achieves 100% success rate** with 500,000 messages at 288 threads
2. **Median latency (19ms)** close to theoretical network baseline (17ms)
3. **p99 latency (74ms)** indicates 99% of requests complete within acceptable bounds
4. **Peak throughput (10,972 msg/s)** exceeds Part 1 measurement by 38%
5. **Throughput stability** demonstrates robust system behavior under sustained load
6. **Message distribution** confirms correct implementation (90% TEXT, 5% JOIN, 5% LEAVE)
7. **Room distribution** is uniform, validating random selection algorithm

### Recommendations for Production

1. **Thread Pool Sizing**: 288 threads is optimal for EC2 deployment with ~15ms network latency
2. **Latency Budget**: Plan for p99 latency of ~75ms in cross-region WAN scenarios
3. **Retry Handling**: ~2.4% of messages require retry (12,201 / 500,000); ensure retry logic is robust
4. **Connection Pooling**: 308 connections for 288 threads shows efficient reuse
5. **Monitoring**: Track p95/p99 latencies and throughput per 10-second bucket for SLA compliance

---
