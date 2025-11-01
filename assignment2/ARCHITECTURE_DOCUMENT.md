# CS6650 Assignment 2 - Architecture Document

## System Architecture

### High-Level Architecture Diagram

```
                    ┌─────────────────────────────────────────┐
                    │         Test Client (Local)            │
                    │  - Sends 500K messages                  │
                    │  - Multi-threaded connections           │
                    └──────────────┬──────────────────────────┘
                                   │
                                   │ WebSocket Connection
                                   ↓
                    ┌──────────────────────────────────────────┐
                    │  AWS Application Load Balancer (ALB)    │
                    │  - Sticky sessions enabled               │
                    │  - Health check: /chat-server/           │
                    │  - Distributes load across servers       │
                    └──────────────┬───────────────────────────┘
                                   │
         ┌─────────────────────────┼─────────────────────────┐
         │                         │                         │
         ↓                         ↓                         ↓
    ┌────────┐              ┌────────┐              ┌────────┐
    │Server 1│              │Server 2│      ...     │Server 4│
    │ EC2    │              │ EC2    │              │ EC2    │
    │:8080   │              │:8080   │              │:8080   │
    └───┬────┘              └───┬────┘              └───┬────┘
        │                       │                       │
        │     Publish to RabbitMQ (all servers)        │
        └───────────────────────┼───────────────────────┘
                                │
                                ↓
                ┌─────────────────────────────────────┐
                │   RabbitMQ (54.188.26.217)         │
                │                                     │
                │  ┌──────────────────────────────┐  │
                │  │ chat.exchange (topic)        │  │
                │  │   routing: room.{roomId}     │  │
                │  └────────────┬─────────────────┘  │
                │               │                     │
                │  ┌────────────▼─────────────────┐  │
                │  │ Queues (durable, with DLX)   │  │
                │  │  - room.1                    │  │
                │  │  - room.2                    │  │
                │  │  - ...                       │  │
                │  │  - room.20                   │  │
                │  └────────────┬─────────────────┘  │
                │               │                     │
                │  ┌────────────▼─────────────────┐  │
                │  │ Dead Letter Queue            │  │
                │  │  - chat.dlx (exchange)       │  │
                │  │  - chat.dlq (queue)          │  │
                │  └──────────────────────────────┘  │
                └─────────────────┬───────────────────┘
                                  │
                                  │ Consume messages
                                  ↓
                ┌─────────────────────────────────────┐
                │   Consumer (52.24.223.241)         │
                │                                     │
                │  ┌──────────────────────────────┐  │
                │  │ MessageConsumer              │  │
                │  │  - 20 threads                │  │
                │  │  - One thread per room       │  │
                │  │  - At-least-once delivery    │  │
                │  └────────────┬─────────────────┘  │
                │               │                     │
                │  ┌────────────▼─────────────────┐  │
                │  │ RoomManager                  │  │
                │  │  - Duplicate detection       │  │
                │  │  - Caffeine cache (5min TTL) │  │
                │  │  - WebSocket broadcasting    │  │
                │  └────────────┬─────────────────┘  │
                │               │                     │
                │  ┌────────────▼─────────────────┐  │
                │  │ Health Server (:8080)        │  │
                │  │  - /health endpoint          │  │
                │  │  - Metrics reporting         │  │
                │  └──────────────────────────────┘  │
                └─────────────────────────────────────┘
                                  │
                                  │ Broadcast to
                                  ↓
                          Connected Clients
```

---

## Message Flow Sequence

### 1. Client Sends Message

```
Client                Server               RabbitMQ            Consumer
  |                     |                     |                   |
  |--WebSocket msg----->|                     |                   |
  |  {userId, text,     |                     |                   |
  |   roomId}           |                     |                   |
  |                     |                     |                   |
```

### 2. Server Publishes to Queue

```
Client                Server               RabbitMQ            Consumer
  |                     |                     |                   |
  |                     |--Publish QueueMsg-->|                   |
  |                     |  to chat.exchange   |                   |
  |                     |  routing: room.X    |                   |
  |                     |                     |                   |
  |                     |                     |--Store in-------->|
  |                     |                     |  room.X queue     |
  |<---ACK response-----|                     |                   |
  |  {status: success}  |                     |                   |
```

### 3. Consumer Processes and Broadcasts

```
Client                Server               RabbitMQ            Consumer
  |                     |                     |                   |
  |                     |                     |<--Pull message----|
  |                     |                     |  from room.X      |
  |                     |                     |                   |
  |                     |                     |                   |--Check duplicate
  |                     |                     |                   |  (Caffeine cache)
  |                     |                     |                   |
  |                     |                     |                   |--Broadcast to
  |                     |                     |                   |  all clients
  |<----------------------------------------------WebSocket msg---|
  |                     |                     |                   |
  |                     |                     |<--ACK-------------|
  |                     |                     |  (manual confirm) |
```

### 4. Retry on Failure

```
Client                Server               RabbitMQ            Consumer
  |                     |                     |                   |
  |                     |                     |                   |--Broadcast fails
  |                     |                     |                   |
  |                     |                     |<--NACK (requeue)--|
  |                     |                     |                   |
  |                     |  [Wait backoff]     |                   |
  |                     |                     |                   |
  |                     |                     |--Retry delivery-->|
  |                     |                     |                   |
  |                     |     [After 3 retries fail]              |
  |                     |                     |                   |
  |                     |                     |<--Route to DLQ----|
  |                     |                     |  (chat.dlq)       |
```

---

## Queue Topology Design

### Exchange Configuration

**Type**: Topic Exchange
**Name**: `chat.exchange`
**Durable**: Yes
**Auto-delete**: No

**Routing Pattern**:
- Messages route by `room.{roomId}`
- Example: Message to room 5 uses routing key `room.5`

### Queue Configuration (20 Room Queues)

**For each queue (room.1 to room.20)**:
- **Durable**: Yes (survives RabbitMQ restart)
- **Exclusive**: No (multiple consumers can connect)
- **Auto-delete**: No (persists when no consumers)
- **Arguments**:
  - `x-dead-letter-exchange`: `chat.dlx`
  - `x-dead-letter-routing-key`: `dlq`

### Dead Letter Queue (DLQ)

**Exchange**: `chat.dlx` (direct)
**Queue**: `chat.dlq`
**Purpose**: Store messages that exceed max retry count (3)

---

## Consumer Threading Model

### Thread Architecture

**Total Threads**: 20 (configurable via `CONSUMER_THREADS`)
**Distribution**: One thread per room queue
**Purpose**: Guarantee FIFO message ordering within each room

```
┌─────────────────────────────────────────┐
│         Consumer Application            │
│                                         │
│  Thread 0  ──consume──> room.1         │
│  Thread 1  ──consume──> room.2         │
│  Thread 2  ──consume──> room.3         │
│  Thread 3  ──consume──> room.4         │
│     ...                                 │
│  Thread 19 ──consume──> room.20        │
│                                         │
│  Each thread:                           │
│    - Dedicated channel                  │
│    - Prefetch count: 10 messages        │
│    - Manual ACK/NACK                    │
│    - Independent processing             │
└─────────────────────────────────────────┘
```

### Message Processing Pipeline

```
1. Pull from Queue
   └─> basicConsume(queueName, autoAck=false)

2. Deserialize
   └─> Jackson ObjectMapper

3. Check Duplicate
   └─> Caffeine cache lookup by messageId
   └─> If duplicate: skip and return
   └─> Else: cache messageId with 5min TTL

4. Broadcast
   └─> RoomManager.broadcastToRoom()
   └─> Send to all WebSocket sessions in room

5. Acknowledge
   └─> SUCCESS: channel.basicAck()
   └─> FAILURE: RetryHandler.handleFailedDelivery()
       ├─> Retry < 3: channel.basicNack(requeue=true)
       └─> Retry >= 3: send to DLQ
```

---

## Load Balancing Configuration

### ALB Settings

**Listener**: HTTP on port 8080
**Target Group**: cs6650-servers-tg
**Target Type**: Instance
**Protocol**: HTTP
**Port**: 8080

### Health Check Configuration

```
Path: /chat-server/
Interval: 30 seconds
Timeout: 5 seconds
Healthy threshold: 2
Unhealthy threshold: 3
```

### Sticky Sessions (Critical for WebSocket)

**Enabled**: Yes
**Duration**: 3600 seconds (1 hour)
**Cookie Name**: AWSALB

**Why Required**: WebSocket connections are stateful. Once a client connects to a specific server, all subsequent messages must go to the same server to maintain the connection.

### Target Registration

```
Target 1: cs6650-server-1 (instance-id-1) port 8080
Target 2: cs6650-server-2 (instance-id-2) port 8080
Target 3: cs6650-server-3 (instance-id-3) port 8080
Target 4: cs6650-server-4 (instance-id-4) port 8080
```

---

## Delivery Guarantees Implementation

### 1. At-Least-Once Delivery

**Server Side (Producer)**:
- Publisher confirms enabled: `channel.confirmSelect()`
- Synchronous wait for confirm: `channel.waitForConfirmsOrDie(5000)`
- Persistent messages: `MessageProperties.PERSISTENT_TEXT_PLAIN`

**Consumer Side**:
- Manual acknowledgment mode: `autoAck = false`
- ACK only after successful processing: `channel.basicAck()`
- NACK on failure to requeue: `channel.basicNack(requeue=true)`

### 2. Duplicate Message Handling

**Implementation**: Caffeine in-memory cache

```java
Cache<String, Long> processedMessages = Caffeine.newBuilder()
    .expireAfterWrite(5, TimeUnit.MINUTES)
    .maximumSize(100000)
    .build();
```

**Logic**:
1. Check if messageId exists in cache
2. If exists: skip processing (duplicate detected)
3. If not: process and cache messageId with timestamp

**Metrics**: `duplicatesDetected` counter tracks duplicate count

### 3. Message Ordering Within Rooms

**Solution**: One thread per room queue

- Each of 20 threads consumes from exactly one queue
- RabbitMQ queues are FIFO by nature
- Single consumer per queue guarantees order
- No parallel processing within same room

### 4. Failed Delivery Retry Logic

**Retry Handler** (`RetryHandler.java`):

```
Attempt 1: Immediate retry (NACK + requeue)
          └─> Backoff: 100ms

Attempt 2: Retry after backoff
          └─> Backoff: 200ms (exponential)

Attempt 3: Final retry
          └─> Backoff: 400ms

After 3 failures:
          └─> Route to Dead Letter Queue (chat.dlq)
```

**Exponential Backoff Formula**:
```
delay = 100ms × 2^retryCount
```

---

## Failure Handling Strategies

### 1. Server Failure

**Detection**: ALB health checks fail (3 consecutive failures)
**Action**: ALB removes server from target group
**Impact**: Remaining servers handle load
**Recovery**: When server comes back online, ALB adds it back

### 2. RabbitMQ Connection Failure

**Detection**: IOException during publish
**Action**:
- Automatic reconnection (RabbitMQ client library)
- Network recovery interval: 10 seconds
- Connection timeout: 30 seconds

**Fallback**: Circuit breaker pattern (not implemented, future work)

### 3. Consumer Failure

**Detection**: Process crash or systemd monitoring
**Action**: systemd auto-restart
**Configuration**:
```ini
Restart=always
RestartSec=10
StartLimitBurst=5
```

**Message Safety**: Unacknowledged messages return to queue

### 4. Queue Overflow

**Prevention**:
- Consumer threads: 20 (matches queue count)
- Prefetch count: 10 per thread
- Capacity: 200 messages in-flight concurrently

**Monitoring**: Track queue depth via RabbitMQ Management Console

---

## Performance Characteristics

### Throughput Expectations

**Single Server**:
- Expected: 5,000-10,000 msgs/sec
- Bottleneck: Server CPU and RabbitMQ publish rate

**2 Servers with ALB**:
- Expected: 10,000-18,000 msgs/sec
- Improvement: ~80-100% over single server

**4 Servers with ALB**:
- Expected: 20,000-35,000 msgs/sec
- Improvement: ~200-250% over single server

### Resource Utilization

**Server EC2 (t2.small)**:
- CPU: 50-70% under load
- Memory: 512MB-1GB (Tomcat + connections)
- Network: 10-50 Mbps

**Consumer EC2 (t2.micro)**:
- CPU: 30-50% under load
- Memory: 256MB-512MB
- Network: 5-20 Mbps

**RabbitMQ EC2 (t2.small)**:
- CPU: 40-60% under load
- Memory: 512MB-1GB
- Disk I/O: Minimal (in-memory processing)

---

## Configuration Summary

### Server Environment Variables

```bash
RABBITMQ_HOST=54.188.26.217
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=guest
RABBITMQ_PASSWORD=guest
CHANNEL_POOL_SIZE=20
```

### Consumer Environment Variables

```bash
RABBITMQ_HOST=54.188.26.217
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=guest
RABBITMQ_PASSWORD=guest
CONSUMER_THREADS=20
PREFETCH_COUNT=10
MAX_RETRIES=3
HEALTH_PORT=8080
STATS_INTERVAL=30
```

### Instance Configuration

| Component | Instance Type | IP Address | Ports |
|-----------|--------------|------------|-------|
| RabbitMQ | t3.micro | 54.190.49.133 | 5672, 15672 |
| Consumer | t3.micro | 34.214.37.149 | 8080 |
| Server 1 | t3.micro | 34.212.226.25 | 8080 |
| Server 2 | t3.micro | 44.243.190.97 | 8080 |
| Server 3 | t3.micro | 44.251.238.69 | 8080 |
| Server 4 | t3.micro | 54.190.99.98 | 8080 |
| ALB | - | TBD | 80 |

---

## Conclusion

This architecture provides:
- **Scalability**: Horizontal scaling via ALB
- **Reliability**: At-least-once delivery with retries
- **Performance**: Distributed load across multiple servers
- **Monitoring**: Health checks and metrics at all levels
- **Fault Tolerance**: Auto-restart and DLQ for failed messages

The system successfully decouples message production (servers) from consumption (consumer), allowing independent scaling and failure isolation.
