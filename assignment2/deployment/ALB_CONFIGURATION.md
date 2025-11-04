# AWS Application Load Balancer Configuration

## ALB Details

**Load Balancer:**
- Name: `cs6650-alb`
- DNS: `cs6650-alb-631563720.us-west-2.elb.amazonaws.com`
- Type: Application Load Balancer
- Scheme: Internet-facing
- IP address type: IPv4

**Availability Zones:**
- us-west-2a
- us-west-2b
- us-west-2c

---

## Listener Configuration

**HTTP Listener:**
- Protocol: HTTP
- Port: 8080
- Default Action: Forward to target group `cs6650-tg`

**WebSocket Support:**
- Automatic HTTP to WebSocket upgrade
- Maintains persistent connections
- Bidirectional frame forwarding

---

## Target Group Configuration

**Target Group Name:** `cs6650-tg`

**Basic Settings:**
- Target type: Instance
- Protocol: HTTP
- Port: 8080
- VPC: [Your VPC]

**Health Check Settings:**
- Protocol: HTTP
- Path: `/chat-server/`
- Port: Traffic port (8080)
- Success codes: 200

**Health Check Timing:**
- Interval: 30 seconds
- Timeout: 5 seconds
- Healthy threshold: 2 consecutive checks
- Unhealthy threshold: 2 consecutive checks

**Registered Targets:**
```
Target ID              Availability Zone    Health Status
44.254.79.143:8080     us-west-2c          healthy
50.112.195.157:8080    us-west-2c          healthy
54.214.123.172:8080    us-west-2c          healthy
54.190.115.9:8080      us-west-2c          healthy
```

---

## Sticky Sessions Configuration

**Session Affinity (Stickiness):**
- Enabled: Yes
- Duration: 86400 seconds (24 hours)
- Cookie name: AWSALB (Load balancer generated)

**Why Required for WebSocket:**
- WebSocket connections are stateful
- Each client maintains persistent connection to specific server
- Without stickiness, subsequent requests could route to different server
- Server wouldn't recognize the client session

---

## Advanced Settings

**Connection Settings:**
- Idle timeout: 120 seconds
  - Allows long-lived WebSocket connections
  - Prevents premature connection termination
  
- Connection draining: 300 seconds
  - Graceful shutdown when deregistering targets
  - Existing connections given 5 minutes to complete

**Cross-Zone Load Balancing:**
- Enabled: Yes
- Distributes traffic evenly across all AZs

**Deletion Protection:**
- Disabled (for easier testing/cleanup)

---

## Security Group

**Inbound Rules:**
```
Type        Protocol    Port Range    Source          Description
HTTP        TCP         8080          0.0.0.0/0       Allow HTTP from anywhere
HTTP        TCP         8080          ::/0            Allow HTTP from anywhere (IPv6)
```

**Outbound Rules:**
```
Type        Protocol    Port Range    Destination     Description
All traffic  All        All           0.0.0.0/0       Allow all outbound
```

---

## Load Balancing Algorithm

**Algorithm:** Round Robin with Session Affinity

**Behavior:**
1. **New Client Connection:**
   - ALB uses round robin to select target
   - Assigns sticky session cookie (AWSALB)
   - Client maintains connection to assigned server

2. **Subsequent Requests:**
   - Client includes AWSALB cookie
   - ALB routes to same target server
   - Maintains WebSocket session state

3. **Target Failure:**
   - Health check marks target unhealthy
   - ALB stops routing new connections
   - Drains existing connections (300s timeout)
   - New connections distributed among healthy targets

**Expected Traffic Distribution:**
- 4 servers sharing load
- Approximately 25% traffic per server (ideal case)
- Actual distribution varies based on:
  - Client connection timing
  - Session duration
  - Sticky session cookies

---

## Performance Metrics

**Test Results:**
- Single Server: 2960.65 msg/s
- 2 Servers + ALB: 3512.96 msg/s (+18.7%)
- 4 Servers + ALB: 3468.66 msg/s (+17.2%)

**Observations:**
- ALB adds minimal latency (~1-2ms)
- Sticky sessions work correctly
- Load distribution effective for 2 servers
- Slight overhead with 4 servers (coordination, network)

---

## Setup Instructions

### 1. Create Load Balancer

```bash
# In AWS Console: EC2 → Load Balancers → Create Load Balancer
# Select: Application Load Balancer
# Name: cs6650-alb
# Scheme: Internet-facing
# IP address type: IPv4
# Network mapping: Select 3+ availability zones
```

### 2. Create Target Group

```bash
# In AWS Console: EC2 → Target Groups → Create target group
# Target type: Instances
# Protocol: HTTP, Port: 8080
# Health check path: /chat-server/
# Register 4 server instances
```

### 3. Configure Sticky Sessions

```bash
# Target Group → Attributes → Edit
# Enable stickiness: Yes
# Stickiness duration: 86400 seconds
# Stickiness type: Load balancer generated cookie
```

### 4. Adjust Timeouts

```bash
# Load Balancer → Attributes → Edit
# Idle timeout: 120 seconds
# Connection draining: 300 seconds
```

### 5. Verify Configuration

```bash
# Check health status
aws elbv2 describe-target-health \
  --target-group-arn <your-target-group-arn>

# Test WebSocket connection
wscat -c ws://cs6650-alb-631563720.us-west-2.elb.amazonaws.com:8080/chat-server/chat/1
```

---

## Monitoring

**CloudWatch Metrics:**
- Request Count
- Target Response Time
- Healthy/Unhealthy Host Count
- HTTP 4xx/5xx Counts

**Access Logs:**
- S3 bucket: (optional, not configured)
- Captures all requests to load balancer
- Useful for debugging and analysis

---

## Troubleshooting

**Common Issues:**

1. **502 Bad Gateway**
   - Check target health status
   - Verify security group allows ALB → Target traffic
   - Ensure targets are listening on port 8080

2. **WebSocket Connection Drops**
   - Increase idle timeout (current: 120s)
   - Verify sticky sessions enabled
   - Check client-side keep-alive implementation

3. **Uneven Load Distribution**
   - Verify sticky session configuration
   - Check session duration setting
   - Monitor CloudWatch metrics for distribution

---

Generated: 2025-11-03
Last Updated: 2025-11-03
