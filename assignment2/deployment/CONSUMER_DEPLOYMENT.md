# Consumer Deployment Guide

Complete guide for deploying the CS6650 Chat Consumer application with delivery guarantees, health checks, and auto-restart capabilities.

## Features Implemented

- **At-Least-Once Delivery**: Manual ACK/NACK with RabbitMQ
- **Duplicate Detection**: In-memory cache with 5-minute TTL
- **Message Ordering**: One thread per room (20 threads total)
- **Retry Logic**: Exponential backoff with max 3 retries
- **Dead Letter Queue**: Automatic routing of failed messages
- **Health Checks**: HTTP endpoint on port 8080
- **Auto-Restart**: systemd configuration with restart limits

## Prerequisites

- RabbitMQ EC2 instance running at 54.188.26.217
- Exchange, queues, and DLQ created in RabbitMQ
- Consumer JAR built: `consumer/target/chat-consumer.jar`

## Step 1: Create Consumer EC2 Instance

### AWS Console Setup

1. Go to EC2 Dashboard
2. Click Launch Instance
3. Configure:
   - Name: `cs6650-consumer-instance`
   - AMI: Amazon Linux 2023
   - Instance Type: t2.micro or t2.small
   - Key Pair: cs6650-hw2-key
   - Security Group: Allow SSH (port 22)
   - Auto-assign Public IP: Enable

4. Launch and note the Public IP (example: 52.34.56.78)

## Step 2: Upload Consumer JAR

### From Local Machine

```bash
# Navigate to consumer target directory
cd /Users/chendong/Desktop/6650/cs6650_assignments/assignment2/consumer/target

# Upload JAR
scp -i /Users/chendong/Desktop/6650/cs6650-hw2-key.pem \
  chat-consumer.jar \
  ec2-user@52.34.56.78:~/

# Upload lib directory
scp -i /Users/chendong/Desktop/6650/cs6650-hw2-key.pem \
  -r lib \
  ec2-user@52.34.56.78:~/
```

## Step 3: SSH to Consumer EC2

```bash
ssh -i /Users/chendong/Desktop/6650/cs6650-hw2-key.pem ec2-user@52.34.56.78
```

## Step 4: Install Java and Deploy

### Manual Installation

```bash
# Update system
sudo yum update -y

# Install Java 11
sudo yum install -y java-11-amazon-corretto

# Verify installation
java -version
```

### Test Manual Run

```bash
# Set environment variables
export RABBITMQ_HOST=54.188.26.217
export RABBITMQ_PORT=5672
export RABBITMQ_USERNAME=guest
export RABBITMQ_PASSWORD=guest

# Run consumer
java -jar ~/chat-consumer.jar
```

Expected output:
```
=== Starting CS6650 Chat Consumer Application ===
RoomManager initialized with 20 rooms and deduplication cache
Connected to RabbitMQ at 54.188.26.217:5672
Starting 20 consumer threads (one per room)
Health check server started on port 8080
Health endpoint: http://localhost:8080/health
Status endpoint: http://localhost:8080/status
Consumer application started successfully
```

Press Ctrl+C to stop.

### Test Health Check

While consumer is running:
```bash
# In another terminal on EC2
curl http://localhost:8080/health

# Expected response:
{
  "status": "healthy",
  "timestamp": 1234567890,
  "components": {
    "roomManager": {"status": "healthy", "message": "RoomManager operational"},
    "messageConsumer": {"status": "healthy", "message": "Consumer threads running"}
  },
  "metrics": {
    "totalSessions": 0,
    "messagesProcessed": 0,
    "duplicatesDetected": 0,
    "broadcastsSucceeded": 0,
    "broadcastsFailed": 0
  }
}
```

## Step 5: Configure Auto-Restart with systemd

### Create Service File

```bash
# Create directory for consumer
sudo mkdir -p /home/ec2-user/consumer
sudo mv /home/ec2-user/chat-consumer.jar /home/ec2-user/consumer/
sudo mv /home/ec2-user/lib /home/ec2-user/consumer/
sudo chown -R ec2-user:ec2-user /home/ec2-user/consumer

# Create service file
sudo tee /etc/systemd/system/chat-consumer.service > /dev/null <<'EOF'
[Unit]
Description=CS6650 Chat Consumer Application
After=network.target

[Service]
Type=simple
User=ec2-user
WorkingDirectory=/home/ec2-user/consumer

# RabbitMQ Configuration
Environment="RABBITMQ_HOST=54.188.26.217"
Environment="RABBITMQ_PORT=5672"
Environment="RABBITMQ_USERNAME=guest"
Environment="RABBITMQ_PASSWORD=guest"

# Consumer Configuration (20 threads - one per room for ordering guarantee)
Environment="CONSUMER_THREADS=20"
Environment="PREFETCH_COUNT=10"
Environment="MAX_RETRIES=3"

# Health Check Configuration
Environment="HEALTH_PORT=8080"

# Statistics Reporting
Environment="STATS_INTERVAL=30"

ExecStart=/usr/bin/java -Xmx512m -Xms256m -jar /home/ec2-user/consumer/chat-consumer.jar

# Auto-restart on failure with limits
Restart=always
RestartSec=10
StartLimitInterval=200
StartLimitBurst=5

# Logging
StandardOutput=journal
StandardError=journal
SyslogIdentifier=chat-consumer

[Install]
WantedBy=multi-user.target
EOF
```

### Start Service

```bash
# Reload systemd
sudo systemctl daemon-reload

# Enable service (auto-start on boot)
sudo systemctl enable chat-consumer

# Start service
sudo systemctl start chat-consumer

# Check status
sudo systemctl status chat-consumer
```

## Step 6: Verify Deployment

### Check Service Status

```bash
sudo systemctl status chat-consumer
```

Expected output:
```
Active: active (running) since ...
Main PID: 12345 (java)
```

### View Logs

```bash
# Real-time logs
sudo journalctl -u chat-consumer -f

# Last 100 lines
sudo journalctl -u chat-consumer -n 100
```

### Test Auto-Restart

```bash
# Find process ID
ps aux | grep chat-consumer

# Kill process
sudo kill -9 PROCESS_ID

# Wait 10 seconds
sleep 10

# Check status - should be running again
sudo systemctl status chat-consumer
```

## Management Commands

### Service Control

```bash
# Start service
sudo systemctl start chat-consumer

# Stop service
sudo systemctl stop chat-consumer

# Restart service
sudo systemctl restart chat-consumer

# Check status
sudo systemctl status chat-consumer

# Enable auto-start on boot
sudo systemctl enable chat-consumer

# Disable auto-start
sudo systemctl disable chat-consumer
```

### Logs

```bash
# View real-time logs
sudo journalctl -u chat-consumer -f

# View last 50 lines
sudo journalctl -u chat-consumer -n 50

# View logs since today
sudo journalctl -u chat-consumer --since today

# View logs with grep
sudo journalctl -u chat-consumer | grep "ERROR"
```

## Troubleshooting

### Consumer Not Starting

1. Check Java installation:
   ```bash
   java -version
   ```

2. Check JAR file exists:
   ```bash
   ls -lh ~/chat-consumer.jar
   ```

3. Check service logs:
   ```bash
   sudo journalctl -u chat-consumer -n 100
   ```

### Cannot Connect to RabbitMQ

1. Check RabbitMQ is running:
   ```bash
   telnet 54.188.26.217 5672
   ```

2. Verify environment variables:
   ```bash
   sudo systemctl show chat-consumer --property=Environment
   ```

3. Check RabbitMQ EC2 security group allows port 5672

### Service Keeps Restarting

1. Check logs for errors:
   ```bash
   sudo journalctl -u chat-consumer -n 200
   ```

2. Check RabbitMQ queues exist:
   - Visit http://54.188.26.217:15672
   - Login with guest/guest
   - Check Queues tab for room.1 through room.20

## Configuration

### Environment Variables

Edit service file to change configuration:

```bash
sudo nano /etc/systemd/system/chat-consumer.service
```

Modify Environment variables:
- `RABBITMQ_HOST`: RabbitMQ server IP (default: localhost)
- `RABBITMQ_PORT`: RabbitMQ AMQP port (default: 5672)
- `RABBITMQ_USERNAME`: RabbitMQ username (default: guest)
- `RABBITMQ_PASSWORD`: RabbitMQ password (default: guest)
- `CONSUMER_THREADS`: Number of consumer threads (default: 20, one per room)
- `PREFETCH_COUNT`: Messages to prefetch per thread (default: 10)
- `MAX_RETRIES`: Maximum retry attempts before DLQ (default: 3)
- `HEALTH_PORT`: Health check server port (default: 8080)
- `STATS_INTERVAL`: Statistics reporting interval in seconds (default: 30)

After editing:
```bash
sudo systemctl daemon-reload
sudo systemctl restart chat-consumer
```

## Performance Monitoring

### Check Resource Usage

```bash
# CPU and memory
top -p $(pgrep -f chat-consumer)

# Detailed stats
ps aux | grep chat-consumer
```

### Check Consumer Statistics

Statistics are logged every 30 seconds:
```bash
sudo journalctl -u chat-consumer -f | grep "Statistics"
```

Output example:
```
=== RoomManager Statistics ===
Total sessions: 25
Messages processed: 15000
Duplicates detected: 42
Broadcasts succeeded: 149850
Broadcasts failed: 150
Cache size: 8234
```

### Check Health Status

Query health endpoint from outside EC2 (requires Security Group port 8080 open):
```bash
curl http://52.24.223.241:8080/health
```

Or from within EC2:
```bash
curl http://localhost:8080/health
```

Response includes:
- Overall health status (healthy/unhealthy)
- Component health (RoomManager, MessageConsumer)
- Metrics (sessions, messages, duplicates, broadcasts)
- JVM memory usage

### Monitor Dead Letter Queue

Check for failed messages in RabbitMQ Management Console:
1. Open http://54.188.26.217:15672
2. Go to Queues tab
3. Check `chat.dlq` queue
4. Messages in DLQ indicate repeated failures (exceeded MAX_RETRIES)

## Next Steps

After consumer deployment:
1. Deploy server-v2 to EC2 instances
2. Configure Application Load Balancer
3. Run performance tests
4. Collect metrics and screenshots

## Reference

- Consumer EC2 IP: [Your Consumer EC2 IP]
- RabbitMQ EC2 IP: 54.188.26.217
- Service name: chat-consumer
- JAR location: /home/ec2-user/chat-consumer.jar
- Logs: sudo journalctl -u chat-consumer
