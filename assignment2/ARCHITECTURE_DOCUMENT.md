# CS6650 Assignment 2: Architecture Document

**Student:** [Your Name]
**Course:** CS6650 Fall 2024
**Date:** November 3, 2025

---

## 1. System Architecture Overview

### High-Level Architecture

```
┌─────────┐
│ Client  │
└────┬────┘
     │ WebSocket
     ▼
┌────────────────────┐
│  Application Load  │
│    Balancer (ALB)  │
└─────────┬──────────┘
          │ Sticky Sessions
          │
    ┌─────┴──────┬──────────┬──────────┐
    ▼            ▼          ▼          ▼
┌────────┐  ┌────────┐ ┌────────┐ ┌────────┐
│Server 1│  │Server 2│ │Server 3│ │Server 4│
└───┬────┘  └───┬────┘ └───┬────┘ └───┬────┘
    │           │          │          │
    └───────────┴──────────┴──────────┘
                │ Publish Messages
                ▼
        ┌──────────────┐
        │   RabbitMQ   │
        │ Topic Exchange│
        └───────┬──────┘
                │ 20 Queues (room.1-room.20)
                ▼
        ┌──────────────┐
        │   Consumer   │
        │ (20 Threads) │
        └───────┬──────┘
                │ Broadcast
                ▼
        ┌──────────────┐
        │   Clients    │
        │ (via WebSocket)│
        └──────────────┘
```

### Component Details

**Load Balancer:**
- AWS Application Load Balancer
- URL: cs6650-alb-631563720.us-west-2.elb.amazonaws.com
- Sticky sessions enabled (AWSALB cookie)
- Health checks every 30 seconds

**Server Instances (4x t3.micro EC2):**
- Server 1: 18.237.196.134
- Server 2: 54.186.55.54
- Server 3: 44.251.147.184
- Server 4: 34.213.93.17
- Each runs Apache Tomcat 9.0.82 with Server-v2 application

**Message Queue:**
- RabbitMQ 3.13.7 (Docker container)
- Host: 18.246.237.223
- Management UI: Port 15672
- AMQP Port: 5672

**Consumer Service:**
- Dedicated EC2 instance: 34.216.219.207
- Jetty WebSocket server on port 8080
- Systemd service for auto-restart

---

## 2. Message Flow Sequence

### Step-by-Step Message Journey

```
1. Client → ALB
   WebSocket message sent to load balancer

2. ALB → Server (via sticky session)
   Routed to assigned server instance

3. Server receives message
   ├─→ Validate format
   └─→ Create QueueMessage with metadata

4. Server → RabbitMQ
   ├─→ Borrow channel from pool
   ├─→ Publish to chat.exchange
   │   Routing key: room.{roomId}
   └─→ Wait for publisher confirm

5. Server → Client
   Return ACK immediately

6. RabbitMQ → Queue
   Route to appropriate room queue

7. Consumer pulls message
   ├─→ Deserialize QueueMessage
   ├─→ Check duplicates
   └─→ Route to RoomManager

8. RoomManager → Broadcast
   Send to all WebSocket sessions in room

9. Consumer → RabbitMQ
   ACK message after successful broadcast

10. Clients receive message
    All participants in room get the message
```

---

## 3. Queue Topology Design

### RabbitMQ Configuration

**Exchange:**
- Name: `chat.exchange`
- Type: `topic`
- Durable: `true`

**Routing Pattern:**
- Routing key: `room.{roomId}`
- Example: `room.1`, `room.2`, ..., `room.20`

**Queues (20 total):**
- room.1 through room.20
- Each bound to exchange with matching routing key
- Durable queues (survive broker restart)

**Queue Properties:**
- Message TTL: 1 hour
- Max length: 10,000 messages
- Dead Letter Exchange configured

### Design Rationale

**One Queue Per Room:**
- Guarantees message ordering within each room
- Allows parallel processing across rooms
- Isolated failure domains

**Topic Exchange:**
- Flexible routing by room ID
- Industry standard for pub-sub
- Future extensibility

---

## 4. Consumer Threading Model

### Thread Architecture

```
Consumer Application
│
├─→ Main Thread
│   └─→ Initialize RabbitMQ connection
│   └─→ Create 20 consumer threads
│   └─→ Start health server (port 8080)
│
├─→ Thread 1  → room.1
├─→ Thread 2  → room.2
...
└─→ Thread 20 → room.20
```

**Each Consumer Thread:**
1. Subscribes to one queue
2. Pulls messages (prefetch=10)
3. Processes sequentially
4. Broadcasts via RoomManager
5. ACKs after successful broadcast

### Why This Design?

**Message Ordering:**
- Single thread per room ensures FIFO order
- Critical for chat (messages must appear in send order)

**Parallel Processing:**
- 20 threads work simultaneously across rooms
- High throughput while maintaining order

**Fault Isolation:**
- Slow room doesn't block others
- Failed message in one room doesn't affect others

---

## 5. Load Balancing Configuration

### ALB Setup

**Sticky Sessions:**
- Enabled: Yes
- Cookie: AWSALB
- Duration: 24 hours
- **Critical for WebSocket** (stateful connections)

**Health Checks:**
- Path: `/chat-server/`
- Interval: 30 seconds
- Timeout: 5 seconds
- Healthy threshold: 2
- Unhealthy threshold: 2

**Advanced Settings:**
- Idle timeout: 120 seconds (long-lived WebSocket)
- Connection draining: 300 seconds

### Load Distribution

**Algorithm:** Round Robin + Sticky Sessions
- First request → Round robin
- Same client → Sticky session to same server
- WebSocket upgrade → Maintained on same server

**Expected Distribution:**
- 4 servers sharing load evenly
- ~25% traffic per server (ideal case)

---

## 6. Failure Handling Strategies

### Server-Side Failures

**RabbitMQ Connection Failure:**
- Auto-recovery enabled (10s interval)
- Publisher returns error to client
- Client can retry

**Channel Pool Exhaustion:**
- Block and wait (30s timeout)
- Return error if timeout
- Proper cleanup in finally blocks

**Publish Failure:**
- Publisher confirms detect failures
- Log error with messageId
- Return NACK to client

### Consumer-Side Failures

**Consumer Crash:**
- Systemd auto-restarts service
- Unacked messages return to queue
- Consumer resumes from where it stopped

**Broadcast Failure:**
- Continue to other sessions
- Increment failure metric
- Still ACK message (processed)

**Duplicate Messages:**
- messageId cache detects duplicates
- Skip processing
- ACK immediately

### ALB Failures

**Target Unhealthy:**
- Health check fails (2 consecutive)
- Stop routing new connections
- Drain existing connections (300s)
- Route to healthy targets only

**All Targets Down:**
- ALB returns 503
- Client retries with backoff

### Network Failures

**Server ↔ RabbitMQ Partition:**
- Auto-recovery attempts reconnection
- During outage: Servers return errors
- Clients retry or switch servers (via ALB)

**Client Disconnect:**
- Server removes session from RoomManager
- Client reconnects → New session
- Missed messages during disconnect are lost

---

## Summary

This architecture provides:
- **High Availability:** 4 servers, automatic failover
- **Scalability:** Horizontal scaling via ALB
- **Reliability:** At-least-once delivery, auto-recovery
- **Performance:** 3468 msg/s with 4 servers (+17% vs single server)
- **Maintainability:** Clear separation of concerns

Key design priorities:
1. Message ordering within rooms
2. Fault tolerance and recovery
3. Operational simplicity
4. Cost efficiency (t3.micro instances)
