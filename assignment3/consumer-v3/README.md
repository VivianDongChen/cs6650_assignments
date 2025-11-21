# CS6650 Chat Consumer v3 - with PostgreSQL Persistence

Version 3 of the chat consumer adds PostgreSQL database persistence while maintaining real-time message delivery.

## Features

- ✅ RabbitMQ message consumption (20 threads, one per room)
- ✅ **PostgreSQL persistence with batch writing**
- ✅ **HikariCP connection pooling**
- ✅ Real-time WebSocket broadcast
- ✅ Health check endpoint
- ✅ Graceful shutdown with message flushing

## Architecture

```
RabbitMQ Queue → Consumer Thread → [1] BatchWriter → PostgreSQL
                                  → [2] RoomManager → WebSocket Clients
```

**Processing Flow:**
1. Consumer receives message from RabbitMQ
2. Message is added to BatchWriter queue (async)
3. Message is broadcast to WebSocket clients (real-time)
4. BatchWriter accumulates messages and writes in batches
5. Consumer ACKs message to RabbitMQ

## Configuration

### Environment Variables

**Database Configuration:**
```bash
DB_JDBC_URL=jdbc:postgresql://cs6650-chat-db.cr6q6mmc0zok.us-west-2.rds.amazonaws.com:5432/chatdb
DB_USERNAME=postgres
DB_PASSWORD=MyPassword123
```

**Batch Writer Configuration:**
```bash
BATCH_SIZE=1000           # Messages per batch
FLUSH_INTERVAL_MS=500     # Flush interval in milliseconds
```

**RabbitMQ Configuration:**
```bash
RABBITMQ_HOST=54.245.205.40
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=guest
RABBITMQ_PASSWORD=guest
CONSUMER_THREADS=20
PREFETCH_COUNT=10
```

**Statistics:**
```bash
STATS_INTERVAL=30         # Statistics logging interval in seconds
```

## Build

```bash
mvn clean package
```

This creates:
- `target/chat-consumer-v3.jar`
- `target/lib/` (all dependencies)

## Run Locally

```bash
# Set environment variables
export DB_JDBC_URL="jdbc:postgresql://cs6650-chat-db.cr6q6mmc0zok.us-west-2.rds.amazonaws.com:5432/chatdb"
export DB_USERNAME="postgres"
export DB_PASSWORD="MyPassword123"
export RABBITMQ_HOST="54.245.205.40"
export BATCH_SIZE="1000"
export FLUSH_INTERVAL_MS="500"

# Run
java -jar target/chat-consumer-v3.jar
```

## Deploy to EC2

### 1. Build locally
```bash
mvn clean package
```

### 2. Copy to EC2
```bash
scp -i cs6650-hw2-key.pem -r target ec2-user@<EC2-IP>:~/consumer-v3/
```

### 3. Run on EC2
```bash
ssh -i cs6650-hw2-key.pem ec2-user@<EC2-IP>

# Set environment variables
export DB_JDBC_URL="jdbc:postgresql://cs6650-chat-db.cr6q6mmc0zok.us-west-2.rds.amazonaws.com:5432/chatdb"
export DB_USERNAME="postgres"
export DB_PASSWORD="MyPassword123"
export RABBITMQ_HOST="localhost"  # Or RabbitMQ server IP
export BATCH_SIZE="1000"
export FLUSH_INTERVAL_MS="500"

# Run in background
nohup java -jar consumer-v3/target/chat-consumer-v3.jar > consumer.log 2>&1 &
```

### 4. Check logs
```bash
tail -f consumer.log
```

## Performance Tuning

### Batch Size vs Latency

| Batch Size | Flush Interval | Write Latency | Throughput |
|-----------|----------------|---------------|------------|
| 500 | 250ms | Lower | ~2000 msg/s |
| 1000 | 500ms | Medium | ~3000-4000 msg/s |
| 2000 | 1000ms | Higher | ~5000+ msg/s |

**Recommendation for Assignment 3:**
- **Batch Size:** 1000
- **Flush Interval:** 500ms
- Balances throughput and latency

### Connection Pool Settings

Current configuration (in `DatabaseConnectionPool.java`):
```java
Minimum Idle: 10
Maximum Pool Size: 50
Connection Timeout: 30 seconds
Idle Timeout: 10 minutes
Max Lifetime: 30 minutes
```

## Monitoring

### Health Check
```bash
curl http://localhost:8080/health
```

Response:
```json
{
  "status": "healthy",
  "timestamp": "2025-11-19T22:00:00Z",
  "activeConsumers": 20,
  "activeRooms": 20
}
```

### Statistics (in logs)

Every 30 seconds, you'll see:
```
=== Consumer Statistics ===
RoomManager: total_messages=50000, active_sessions=100, ...
Database Writer: WriterStats[messages=50000, batches=50, errors=0, queueSize=0]
Connection Pool: PoolStats[active=5, idle=15, total=20, awaiting=0]
```

## Database Schema

Tables:
- `messages` - All chat messages

Indexes:
- `idx_messages_room_time` - Room + time range queries
- `idx_messages_user_time` - User history queries
- `idx_messages_timestamp_brin` - Time-based analytics
- `idx_messages_user_room` - User participation queries

Materialized Views:
- `user_stats` - Per-user statistics
- `room_stats` - Per-room statistics
- `hourly_stats` - Hourly aggregations

## Troubleshooting

### Database connection fails
```bash
# Check connectivity
psql -h cs6650-chat-db.cr6q6mmc0zok.us-west-2.rds.amazonaws.com \
     -U postgres -d chatdb

# Check security group allows EC2 instance
```

### Messages not being written
```bash
# Check batch writer stats in logs
# Look for "Database Writer:" lines

# Check for errors
grep "ERROR" consumer.log
```

### High database latency
```bash
# Increase batch size
export BATCH_SIZE="2000"

# Increase flush interval
export FLUSH_INTERVAL_MS="1000"
```

## Assignment 3 Specific

### Screenshot Requirements

1. **Consumer logs showing batch writes**
   - Look for: "Batch written: X messages in Yms"

2. **Database write metrics**
   - Look for: "Database Writer: WriterStats[...]"

3. **Connection pool stats**
   - Look for: "Connection Pool: PoolStats[...]"

### Performance Testing

During load tests, monitor:
- Batch write times (should be < 100ms for 1000 messages)
- Queue size (should stay low, < 100)
- Connection pool (active connections should not max out)

---

**Version:** 3.0
**Date:** November 19, 2025
**Author:** Dong Chen
