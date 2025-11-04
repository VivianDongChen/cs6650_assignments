# Monitoring Tools

Tools for monitoring Assignment 2 system health and performance metrics.

## Available Scripts

### 1. check-system-health.sh
Complete system health check script.

**Checks:**
- RabbitMQ Management Console accessibility
- Consumer health status and metrics
- 4 Server instance status
- ALB accessibility

**Usage:**
```bash
./check-system-health.sh
```

**Sample Output:**
```
=========================================
CS6650 Assignment 2 - System Health Check
=========================================

1. RabbitMQ Health (54.245.205.40)
   [OK] RabbitMQ Management: Accessible (HTTP 200)
   [OK] Queues: 21

2. Consumer Health (54.70.61.198)
   [OK] Consumer: Healthy (HTTP 200)
   Messages Processed: 1234567
   Broadcasts Succeeded: 1234567

3. Server Instances
   [OK] Server 1 (44.254.79.143): Running (HTTP 200)
   [OK] Server 2 (50.112.195.157): Running (HTTP 200)
   [OK] Server 3 (54.214.123.172): Running (HTTP 200)
   [OK] Server 4 (54.190.115.9): Running (HTTP 200)

4. Application Load Balancer
   [OK] ALB: Accessible (HTTP 200)

Summary:
System Status: ALL HEALTHY (6/6 components)
```

---

### 2. monitor-rabbitmq.sh
Detailed RabbitMQ queue monitoring script.

**Metrics:**
- Message count per queue
- Ready messages
- Unacked messages
- Message rate (msg/s)
- Connection count

**Usage:**
```bash
./monitor-rabbitmq.sh
```

---

### 3. collect-system-metrics.sh
System resource metrics collection from all instances.

**Collected Metrics:**
- CPU usage (%)
- Memory usage (used/total)
- Disk usage (used/total)
- Network I/O (RX/TX bytes)
- Process count
- Top processes by CPU and Memory

**Usage:**
```bash
./collect-system-metrics.sh
```

**Output:**
Creates a timestamped directory with metrics from each instance:
```
metrics-20251103-010000/
├── rabbitmq.txt
├── consumer.txt
├── server-1.txt
├── server-2.txt
├── server-3.txt
├── server-4.txt
└── summary.txt
```

**Sample Output:**
```
=========================================
RabbitMQ Queue Monitoring
=========================================

Queue Name      Messages   Ready      Unacked    Rate (msg/s)
============================================================
room.1          0          0          0          0.00
room.2          0          0          0          0.00
...
room.20         0          0          0          0.00
============================================================
TOTAL           0          0          0

Queue Health:
  ✓ Queue depth normal (< 1000)
  ✓ Unacked messages normal (< 100)

Active Connections: 24
```

---

## Manual Monitoring Methods

### RabbitMQ Management Console
**URL:** http://54.245.205.40:15672
**Username:** guest
**Password:** guest

**Important Pages:**
- **Overview**: System overview, message rate graphs
- **Queues**: All queue details, message depth
- **Connections**: Active connection list
- **Channels**: Channel usage statistics

### Consumer Health Endpoint
**Health Check:**
```bash
curl http://54.70.61.198:8080/health | python3 -m json.tool
```

**Status Endpoint:**
```bash
curl http://54.70.61.198:8080/status | python3 -m json.tool
```

**Returned Metrics:**
- messagesProcessed - Total messages processed
- broadcastsSucceeded - Successful broadcasts
- broadcastsFailed - Failed broadcasts
- duplicatesDetected - Duplicate messages detected
- totalSessions - Current WebSocket sessions

### AWS CloudWatch (ALB)
**How to Access:**
1. AWS Console → EC2 → Load Balancers
2. Select `cs6650-alb`
3. Click "Monitoring" tab

**Key Metrics:**
- Request Count - Total requests
- Target Response Time - Response latency
- Healthy/Unhealthy Host Count - Target health
- HTTP 4xx/5xx Count - Error statistics

---

## Monitoring Best Practices

### Periodic Checks
```bash
# Check system health every 5 minutes
watch -n 300 ./check-system-health.sh

# Monitor RabbitMQ queues in real-time
watch -n 10 ./monitor-rabbitmq.sh
```

### Key Metric Thresholds

**RabbitMQ:**
- Queue depth < 1000 (Normal)
- Queue depth 1000-5000 (Warning)
- Queue depth > 5000 (Critical)
- Consumer lag < 100ms (Normal)

**Consumer:**
- broadcastsFailed / messagesProcessed < 1% (Normal)
- duplicatesDetected < 5% (Normal)
- Memory usage < 80% (Normal)

**Servers:**
- All 4 servers responding (Normal)
- 3 servers responding (Degraded)
- < 3 servers responding (Critical)

**ALB:**
- Target Response Time < 100ms (Excellent)
- Target Response Time < 500ms (Normal)
- Target Response Time > 1000ms (Needs optimization)

---

## Troubleshooting

### Issue: RabbitMQ Unreachable
```bash
# Check RabbitMQ container status
ssh ec2-user@54.245.205.40 "docker ps | grep rabbitmq"

# Restart RabbitMQ
ssh ec2-user@54.245.205.40 "docker restart rabbitmq"
```

### Issue: Consumer Unhealthy
```bash
# Check Consumer service status
ssh ec2-user@54.70.61.198 "sudo systemctl status chat-consumer"

# View Consumer logs
ssh ec2-user@54.70.61.198 "sudo journalctl -u chat-consumer -f"

# Restart Consumer
ssh ec2-user@54.70.61.198 "sudo systemctl restart chat-consumer"
```

### Issue: Server Not Responding
```bash
# Check Tomcat process
ssh ec2-user@<server-ip> "ps aux | grep tomcat"

# Restart Tomcat
ssh ec2-user@<server-ip> "sudo /opt/tomcat/bin/shutdown.sh && sudo /opt/tomcat/bin/startup.sh"
```

---

## CI/CD Integration

These scripts can be integrated into deployment workflows:

```bash
# Post-deployment verification
./deployment/SETUP_ALL.sh
sleep 30  # Wait for services to start
./monitoring/check-system-health.sh

# Automatic rollback on health check failure
if [ $? -ne 0 ]; then
    echo "Health check failed, rolling back..."
    # Rollback logic
fi
```

---

Generated: 2025-11-03
