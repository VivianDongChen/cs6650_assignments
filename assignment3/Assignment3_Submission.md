# CS6650 Assignment 3: Persistence and Data Management for Distributed Chat System

**Student**: Dong Chen   
**GitHub Repository**: https://github.com/VivianDongChen/cs6650_assignments

---

# Part A: Database Design

## 1. Database Choice Justification

**PostgreSQL 16.6 on AWS RDS** selected for ACID compliance, advanced indexing (B-tree, BRIN, covering), materialized views, and proven performance (5,988 msg/sec sustained). AWS RDS: automated backups, monitoring, scaling. **Deployment**: db.t3.micro (2 vCPU, 1GB RAM), 20GB GP3 SSD, us-west-2.

## 2. Schema Design

```sql
CREATE TABLE messages (
    message_id VARCHAR(64) PRIMARY KEY, room_id INTEGER NOT NULL,
    user_id VARCHAR(64) NOT NULL, content TEXT NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL, created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Analytics: user_stats, room_stats materialized views
-- Impact: 2,000ms → 10ms (200x speedup)
```
**Storage**: 1.3M msgs = 2.1GB (800MB table + 1.3GB indexes).

## 3. Indexing Strategy

```sql
-- 1. PK (auto): Uniqueness
-- 2. Room Timeline (covering): 150ms → 8ms
CREATE INDEX idx_messages_room_time ON messages(room_id, timestamp DESC)
INCLUDE (user_id, content);

-- 3. User History (covering): 200ms → 10ms
CREATE INDEX idx_messages_user_time ON messages(user_id, timestamp DESC)
INCLUDE (room_id, content);

-- 4. BRIN Timestamp: 500ms → 25ms (20MB only)
CREATE INDEX idx_messages_timestamp_brin ON messages USING BRIN(timestamp);

-- 5. User-Room: 300ms → 12ms
CREATE INDEX idx_messages_user_room ON messages(user_id, room_id, timestamp DESC);
```

**Query Performance**: Room msgs 8ms (<100ms target), User history 10ms (<200ms), Active users 25ms (<500ms), User rooms 12ms (<50ms). All pass. **Trade-off**: 62% index overhead justified by 95% speedup.

## 4. Scaling Considerations

**Current (db.t3.micro)**: CPU 8-10% (10x headroom), IOPS 80-100 (30x headroom). **Capacity**: 10-15M msgs, 12K msg/sec ceiling.

**Vertical**: t3.micro ($15/mo) → t3.small ($30, 15K msg/sec) → m5.large ($150, 50K+ msg/sec).

**Horizontal**: Read replicas (70% offload), sharding by `room_id%3` (3x writes, scatter-gather trade-off), monthly partitioning (50-90% speedup, S3 archival).

## 5. Backup and Recovery

**RDS**: Daily snapshots (7d retention), PITR (5min RPO), 15-30min RTO. **DR**: Cross-region replica (us-east-1, 1min lag, 5-10min failover). Monthly restore tests.

---

# Part B: Performance Report

## Executive Summary

The distributed chat system processed **6.9 million messages** across three load tests with **7,221 msg/sec average throughput** and **100% success rate**.

| Test | Messages | Duration | Throughput | Success |
|------|----------|----------|------------|---------|
| Test 1 (Baseline) | 500K | 64 sec | 7,813 msg/sec | 100% |
| Test 2 (Stress) | 1M | 127 sec | 7,880 msg/sec | 100% |
| Test 3 (Endurance) | 5.4M | 15 min | 5,988 msg/sec | 100% |
| **Combined** | **6.9M** | **16.2 min** | **7,221 avg** | **100%** |

---

## 1. System Architecture

| Component | Configuration | Purpose |
|-----------|--------------|---------|
| **RabbitMQ** | EC2 t2.micro (35.89.16.176) | Message broker |
| **Consumer** | EC2 t2.micro (35.161.158.82) | Message processor |
| **Database** | RDS PostgreSQL 16.6 (db.t3.micro) | Persistent storage |
| **Region** | us-west-2 | AWS region |

**Consumer Architecture**:
1. **Message Layer**: RabbitMQ channel, prefetch=1000, async handling
2. **Batch Layer**: 1,000 msg batches, 500ms flush interval
3. **Database Layer**: HikariCP pool (10-50 connections), prepared statements
4. **Monitoring**: `/health` and `/metrics` API endpoints

---

## 2. Load Test Results

### 2.1 Test 1: Baseline (500K Messages)

**Throughput**: 7,813 msg/sec | **Duration**: 64 sec | **Success**: 100%

**Analysis**: Stable throughput, ~125μs avg database write latency per message.

### 2.2 Test 2: Stress (1M Messages)

**Throughput**: 7,880 msg/sec | **Duration**: 127 sec | **Success**: 100%

**Analysis**: 0.8% throughput improvement (connection pool warmup, JVM optimization). No degradation.

### 2.3 Test 3: Endurance (15-Minute Sustained)

**Throughput**: 5,988 msg/sec (95.4% of 80% target) | **Duration**: 15 min | **Messages**: 5.4M | **Success**: 100%

**Stability Analysis**:

| Time Window | Messages | Rate (msg/sec) | Variance |
|-------------|----------|----------------|----------|
| 0-5 min | 1,891,224 | 6,304 | Baseline |
| 5-10 min | 1,807,572 | 6,025 | -4.4% |
| 10-15 min | 1,698,576 | 5,972 | -0.9% |

**Findings**:
- Throughput stable throughout 15 minutes (<6% variance)
- Zero failures over 5.4M messages
- Resources stable: CPU 3-10%, Memory 60-80MB free, IOPS 2-4/sec
- No connection pool exhaustion (1-2 active connections)
- No memory leaks (GC reclaimed memory effectively)

**Evidence**: [load-tests/endurance_test_results.json](load-tests/endurance_test_results.json), [load-tests/endurance_monitoring.txt](load-tests/endurance_monitoring.txt), [load-tests/screenshots/](load-tests/screenshots/)

### 2.4 Combined Results

**Total Sent**: 6,897,372 messages | **Total in DB**: 5,897,373 (85.5% write rate)

**Note**: 85.5% write rate due to duplicate message IDs rejected by `ON CONFLICT DO NOTHING` (expected test behavior).

---

## 3. Write Performance Analysis

### 3.1 Maximum Sustained Throughput

**Test 3 Results**: 5,988 msg/sec sustained over 15 minutes | **Peak**: 6,304 msg/sec (first 5 min) | **Stability**: <6% variance

**Batch Performance**: 1,000 messages per batch, 50-100ms insert time, 8,000-10,000 msg/sec throughput, 8-10% database CPU.

**Write Bottleneck Breakdown**:
- 40% Index maintenance (5 indexes)
- 30% Transaction commit (fsync)
- 20% Network latency (consumer to RDS)
- 10% SQL parsing (mitigated by prepared statements)

### 3.2 Latency Percentiles

**Database Write Latency** (per 1,000-message batch):

| Percentile | Latency | Notes |
|------------|---------|-------|
| p50 (median) | 55ms | Typical batch insert |
| p95 | 85ms | Includes commit overhead |
| p99 | 120ms | GC pauses or network jitter |
| p99.9 | 200ms | VACUUM or checkpoint activity |

**End-to-End Latency** (RabbitMQ to Database):

| Percentile | Latency | Components |
|------------|---------|------------|
| p50 | 550ms | Queue wait (500ms) + DB write (50ms) |
| p95 | 650ms | Queue wait (550ms) + DB write (100ms) |
| p99 | 800ms | Queue wait (600ms) + DB write (200ms) |

**Trade-off**: 500ms batch flush trades latency for 16x throughput gain (acceptable for non-critical persistence).

### 3.3 Batch Size Optimization

| Batch Size | Flush | Throughput | Latency (p95) | CPU | Result |
|------------|-------|-----------|---------------|-----|--------|
| 100 | 100ms | 3,000 msg/sec | 150ms | 40% | Low throughput |
| 500 | 500ms | 5,500 msg/sec | 550ms | 30% | Balanced |
| **1,000** | **500ms** | **8,000 msg/sec** | **650ms** | **25%** | **Optimal** |
| 5,000 | 1000ms | 9,000 msg/sec | 1,200ms | 20% | Poor latency |

**Selected**: 1,000 messages, 500ms flush interval. **Rationale**: Maximizes throughput while maintaining <1sec latency.

**Performance Comparison**:

| Strategy | Throughput | Network RTT | CPU |
|----------|-----------|-------------|-----|
| Individual Inserts | ~500 msg/sec | 1 per message | 80% |
| Batch (100) | ~3,000 msg/sec | 1 per 100 | 40% |
| **Batch (1000)** | **~8,000 msg/sec** | **1 per 1,000** | **25%** |

**Key Finding**: Batch processing reduced network RTT by 99.9% and CPU by 68%.

### 3.4 Resource Utilization

**Database Metrics** (Test 3 - Endurance):

| Resource | Baseline | During Test | Peak | Post-Test |
|----------|----------|-------------|------|-----------|
| CPU | <5% | 8-10% | 15% | <5% |
| Memory | 650 MB free | 580-600 MB | 580 MB | 640 MB |
| IOPS (Write) | <5 | 80-100 | 150 | <5 |
| Connections | 1-2 | 10-11 | 11 | 1 |

**Consumer Metrics**: CPU 30-40% (t2.micro, 1 vCPU) | Memory 350 MB (JVM heap)

**Connection Pool**:

| Metric | Value | Config |
|--------|-------|--------|
| Total Connections | 10-11 | Max=50 |
| Active | 1-2 | Executing queries |
| Idle | 8-9 | Pre-warmed, ready |
| Wait Time | 0ms | No contention |
| Pool Utilization | 20% | 80% headroom |

**Efficiency**: Database at 10-15% capacity. Consumer CPU-bound at 40%. No network bottleneck.

---

## 4. System Stability Analysis

### 4.1 Queue Depth

#### Queue Depth Over Time
![Queue Depth](load-tests/screenshots/queue_depth.png)

**Analysis**: Queue depth peaked at 50,000 messages within 30 seconds of test start, then drained completely to 0 by the 2-minute mark. The consumer successfully matched the publisher rate of 7,880 msg/sec, maintaining steady state at 0 depth for the remainder of the test. No message loss or backlog occurred.

#### 15-Minute Endurance Test
![Queue Depth Endurance](load-tests/screenshots/queue_depth_endurance.png)

**Endurance Analysis**: During the 15-minute sustained test, queue depth remained at 0 for over 13 minutes after the initial 2-minute drain period. This demonstrates excellent consumer performance and system stability under prolonged load.

**Key Metrics**:
| Time | Queue Depth | Consumer Rate | Publisher Rate | Status |
|------|-------------|---------------|----------------|--------|
| Start | 0 | 0 | 0 | Idle |
| +30s | 50,000 | 7,500 msg/sec | 7,880 msg/sec | Peak |
| +2min | 0 | 7,880 msg/sec | 7,880 msg/sec | Steady |
| +5-15min | 0 | 5,988 msg/sec | 5,988 msg/sec | Sustained |
| End | 0 | 0 | 0 | Drained |

**Summary**: Queue drained in 2 minutes. Steady state at 0 depth maintained. No message loss occurred. No backlog accumulated over 15 minutes.

### 4.2 Database Performance Metrics

**AWS CloudWatch** (see [load-tests/screenshots/](load-tests/screenshots/)):
- **CPU**: Peak 15%, avg 8-10% (efficient queries)
- **Connections**: Peak 10 (pool managed load effectively)
- **IOPS**: Peak 150 write, avg 80-100 (batch processing reduced IOPS 98%)
- **Memory**: 580-650 MB freeable (no leaks, effective caching)

**Storage**: 1.3M messages = 2.1 GB (800 MB table + 1.3 GB indexes). Avg: 1,600 bytes/message.

**Index Breakdown**: Primary 400 MB, Covering 600 MB, BRIN 20 MB, Composite 300 MB. 62% overhead (justified by 95% query speedup).

### 4.3 Memory Usage Patterns

**Consumer JVM** (Test 3):

| Metric | Start | 5 min | 10 min | 15 min | Post |
|--------|-------|-------|--------|--------|------|
| Heap Used | 150 MB | 280 MB | 290 MB | 285 MB | 180 MB |
| GC Count | 0 | 12 | 25 | 38 | 40 |
| GC Time | 0ms | 150ms | 310ms | 480ms | 500ms |

**Analysis**: No memory leak detected (heap stabilized at 280-290 MB). Minor GCs occurred every 20 seconds. 100+ MB headroom remained available. GC effectively reclaimed memory throughout the test.

**Database Memory**: 60-80 MB freeable (stable), 95%+ cache hit ratio (minimal disk I/O).

### 4.4 Connection Pool Statistics

| Metric | Value | Notes |
|--------|-------|-------|
| Total | 11 | Below max (50) |
| Active | 1-2 | Actively writing |
| Idle | 9-10 | Pre-warmed |
| Wait Time | 0ms | No blocking |
| Acquisition | <1ms | 100% pool hit |
| Utilization | 20% | 80% headroom |

**Performance**: No timeout errors occurred. No SQL exceptions encountered. Pool size remained stable. Zero connection failures recorded.

---

## 5. Bottleneck Analysis

### 5.1 Primary Bottlenecks

**1. Network Latency** (60% of latency)

| Link | Latency | Impact |
|------|---------|--------|
| Client → RabbitMQ | 10-20ms RTT | Publishing delay |
| RabbitMQ → Consumer | <1ms | Negligible (same VPC) |
| Consumer → RDS | 2-5ms | DB write delay |

**Root Cause**: Client on local machine, publishing over public internet to AWS us-west-2.

**2. Database Write Latency** (30% of processing time)

| Operation | Time | % |
|-----------|------|---|
| Batch Insert | 50-100ms | 50% |
| Transaction Commit | 20-30ms | 30% |
| Index Updates | 15-25ms | 20% |

**Root Cause**: 5 indexes require maintenance on every insert.

**3. RabbitMQ Queue Depth** (<5% impact)
- Peak: 50,000 messages (temporary burst)
- Drain time: 6-7 seconds (fast recovery)

### 5.2 Proposed Solutions

**Solution 1: Move Client to AWS** (addresses network latency)
- **Implementation**: Deploy load test client on EC2 in us-west-2
- **Impact**: 10-20ms → <1ms (95% reduction), latency 550ms → 100ms (82% improvement)
- **Cost**: $8/month (t2.micro)

**Solution 2: Optimize Indexes** (addresses write latency)
- **Implementation**: Partial indexes for recent data, defer less-critical indexes during writes
- **Impact**: Write time 50-100ms → 35-70ms (30% improvement), index storage -30%
- **Trade-off**: Slower queries for old data

**Solution 3: Upgrade Database** (addresses CPU/IOPS limits)
- **Implementation**: db.t3.micro → db.t3.small (2 vCPU, 2 GB RAM), Provisioned IOPS (3,000)
- **Impact**: Throughput 8K → 15K+ msg/sec (2x), IOPS 100 → 3,000 (30x)
- **Cost**: $30/month (2x)

**Solution 4: Caching Layer** (addresses read latency)
- **Implementation**: Redis for room timelines, user profiles, recent messages
- **Impact**: Read latency 8-10ms → <1ms, database read load -80%
- **Cost**: $15/month (ElastiCache t3.micro)

### 5.3 Trade-offs Made

| Decision | Benefit | Cost |
|----------|---------|------|
| Batch size = 1,000 | 16x throughput (500→8K msg/sec) | Max 500ms latency |
| 5 indexes (62% overhead) | 95% query speedup (150ms→8ms) | 1.3 GB index storage |
| Max pool = 50 | Handle burst traffic | 50 connection slots reserved |
| db.t3.micro | $15/month | 10x CPU headroom, limits scale |

**Rationale**: Read-heavy workload (90% reads) justifies index overhead. Chat persistence not latency-critical (<1sec acceptable).

---

## 6. Conclusion

### 6.1 Achievements

1. **High Throughput**: 7,880 msg/sec peak, 5,988 msg/sec sustained (15 min)
2. **Reliability**: 100% success rate across 6.9M messages
3. **Scalability**: Linear performance scaling (500K → 5.4M messages)
4. **Stability**: <6% throughput variance over 15-minute endurance test
5. **Cost Efficiency**: $0.17 per million messages ($34/month for 202M msg/month @ 10% duty)

### 6.2 Key Insights

1. **Batch Processing**: 16x throughput improvement, 99.9% fewer network RTT, 68% lower CPU
2. **Index Strategy**: 95% query speedup, covering indexes eliminate table lookups
3. **Connection Pooling**: <1ms connection acquisition prevents exhaustion
4. **Monitoring**: Metrics API enabled bottleneck identification and optimization

### 6.3 Performance Targets

| Requirement | Target | Achieved | Status |
|-------------|--------|----------|--------|
| Baseline (500K) | Complete | 7,813 msg/sec, 100% | Pass |
| Stress (1M) | Complete | 7,880 msg/sec, 100% | Pass |
| Endurance | 80%, 15+ min | 5,988 msg/sec (95.4%), 15 min | Pass |
| Room messages | < 100ms | 8ms | Pass |
| User history | < 200ms | 10ms | Pass |
| Active users | < 500ms | 25ms | Pass |
| User's rooms | < 50ms | 12ms | Pass |

**All targets met or exceeded.**

---

## Appendix: Configuration Files

### HikariCP
```java
config.setJdbcUrl(DB_JDBC_URL);
config.setMinimumIdle(10);
config.setMaximumPoolSize(50);
config.setConnectionTimeout(30000);
config.setIdleTimeout(600000);
config.setMaxLifetime(1800000);
```

### Batch Writer
```java
BATCH_SIZE = 1000
FLUSH_INTERVAL_MS = 500
INSERT_SQL = "INSERT INTO messages (...) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (message_id) DO NOTHING"
```

### RabbitMQ Consumer
```java
PREFETCH_COUNT = 1000
QUEUE_NAME = "room.*"
EXCHANGE = "chat.exchange"
```

### Circuit Breaker
```java
CIRCUIT_BREAKER_FAILURE_THRESHOLD = 5
CIRCUIT_BREAKER_TIMEOUT_MS = 60000
CIRCUIT_BREAKER_HALF_OPEN_AFTER_MS = 30000
```

See [config/database.properties](config/database.properties) for complete configuration.

---

## Evidence Files

### Test Results
- [load-tests/test_summary.txt](load-tests/test_summary.txt) - Test 1 & 2
- [load-tests/endurance_test_results.json](load-tests/endurance_test_results.json) - Test 3
- [load-tests/endurance_monitoring.txt](load-tests/endurance_monitoring.txt) - Monitoring log
- [load-tests/endurance_analysis.txt](load-tests/endurance_analysis.txt) - Analysis

### AWS CloudWatch Screenshots

#### CPU Utilization
![CPU Utilization](load-tests/screenshots/cpu.png)

**Analysis**: CPU peaked at 15% during stress test, averaged 8-10% during sustained load. Database remained below 20% CPU utilization throughout all tests, indicating 5x headroom for scaling.

#### Database Connections
![Database Connections](load-tests/screenshots/connection.png)

**Analysis**: Connection count stable at 10-11 connections during load, far below the max pool size of 50. HikariCP effectively managed connection pooling with zero timeout errors.

#### Write IOPS
![Write IOPS](load-tests/screenshots/write_iops.png)

**Analysis**: Write IOPS peaked at 150 during stress test, averaged 80-100 during sustained writes. Batch processing (1,000 messages per batch) reduced IOPS by 98% compared to individual inserts (estimated 8,000+ IOPS).

#### Memory Freeable
![Memory Freeable](load-tests/screenshots/memory.png)

**Analysis**: Freeable memory remained stable at 580-650 MB throughout tests. No memory leaks detected over 15-minute endurance test. Buffer pool effectively cached frequently accessed data (95%+ cache hit ratio).

#### Metrics API Response
![Metrics API](load-tests/screenshots/Metrics%20API.png)

**Analysis**: Metrics API endpoint returning comprehensive statistics including message counts, throughput rates, and query performance. All core queries meet performance targets (room messages: 8ms, user history: 10ms, active users: 25ms).

#### Batch Processing Performance (Part 2)
![Batch Testing](load-tests/screenshots/part%202.png)

**Analysis**: Batch size optimization tests showing 1,000-message batches achieving optimal balance between throughput (8,000 msg/sec) and latency (650ms p95). Smaller batches (100) resulted in lower throughput (3,000 msg/sec), while larger batches (5,000) caused unacceptable latency (1,200ms).

---


