# Assignment 2 Submission Guide

## What I've Completed For You

✅ **Architecture Document** - [ARCHITECTURE_DOCUMENT.md](ARCHITECTURE_DOCUMENT.md)
✅ **Configuration Details** - See below
✅ **Test Results Organization** - All in `/results` folder
✅ **Requirements Verification** - [ASSIGNMENT2_REQUIREMENTS_CHECK.md](ASSIGNMENT2_REQUIREMENTS_CHECK.md)

## What You Need To Do Manually

### 1. Collect Screenshots (15-20 minutes)

#### RabbitMQ Management Console Screenshots (3 screenshots needed)

**Step 1:** Open RabbitMQ Management UI
- URL: `http://18.246.237.223:15672`
- Username: `guest`
- Password: `guest`

**Screenshot 1 - Overview Page:**
1. Click "Overview" tab
2. Take screenshot showing:
   - Connections count
   - Channels count
   - Queues count (should show 20)
   - Message rates graph

**Screenshot 2 - Queues Page:**
1. Click "Queues" tab
2. Take screenshot showing:
   - All 20 room queues (room.1 through room.20)
   - Queue depth for each
   - Message rates

**Screenshot 3 - Live Message Activity:**
1. Run a quick test to generate activity:
   ```bash
   cd /Users/chendong/Desktop/6650/cs6650_assignments/assignment2/testing
   ./run-quick-test-for-screenshots.sh
   ```
2. While test is running, go to RabbitMQ "Overview" → "Message rates" graph
3. Take screenshot showing the live graph with activity

#### AWS ALB Monitoring Screenshots (2 screenshots needed)

**Step 1:** Open AWS Console
1. Go to AWS Console → EC2 → Load Balancers
2. Select `cs6650-alb`
3. Click "Monitoring" tab

**Screenshot 1 - Request Count:**
- Take screenshot of "Request Count" graph
- Shows traffic distribution over time

**Screenshot 2 - Target Health:**
- Scroll down to "Healthy target count" graph
- Take screenshot showing 4 healthy targets

---

### 2. Create PDF Submission

Use the provided template structure below to create your PDF.

#### PDF Page Structure:

**Page 1: Cover Page**
```
CS6650 Assignment 2
Distributed Chat System with Message Queues

Student: [Your Name]
Email: [Your Email]
Date: November 3, 2025

Git Repository: https://github.com/[your-username]/cs6650_assignments
```

**Pages 2-3: Architecture Document**
- Copy content from `ARCHITECTURE_DOCUMENT.md`
- Include the ASCII diagrams
- All 6 sections

**Page 4: Configuration Details**
```
CONFIGURATION DETAILS

Instance Types:
- All EC2 instances: t3.micro (2 vCPU, 1GB RAM)

Component IP Addresses:
- Server 1: 18.237.196.134
- Server 2: 54.186.55.54
- Server 3: 44.251.147.184
- Server 4: 34.213.93.17
- RabbitMQ: 18.246.237.223
- Consumer: 34.216.219.207
- ALB: cs6650-alb-631563720.us-west-2.elb.amazonaws.com

RabbitMQ Configuration:
- Version: 3.13.7 (Docker)
- Exchange: chat.exchange (topic)
- Routing Pattern: room.{roomId}
- Queues: 20 (room.1 - room.20)
- Message TTL: 1 hour
- Max Queue Length: 10,000
- Channel Pool Size: 20

Consumer Configuration:
- Consumer Threads: 20 (one per room)
- Prefetch Count: 10
- ACK Mode: Manual
- WebSocket Server: Jetty 11.0.20
- Health Check Port: 8080

ALB Configuration:
- Sticky Sessions: Enabled (AWSALB cookie, 24h)
- Health Check Path: /chat-server/
- Health Check Interval: 30 seconds
- Idle Timeout: 120 seconds
- Target Group: 4 servers

Client Threading (Optimal):
- Threads: 256
- Messages per test: 500,000
```

**Pages 5-7: Test Results**

**Page 5: Test 1 - Single Server**
```
TEST 1: SINGLE SERVER BASELINE

Configuration:
- Server: 18.237.196.134 (direct connection)
- Messages: 500,000
- Client Threads: 256
- Room Distribution: 20 rooms

Results:
- Throughput: 2960.65 msg/s
- Total Duration: 168.84 seconds
- Success Rate: 100% (500,000/500,000)
- Connections Opened: 256
- Connection Failures: 0

[Insert screenshot of client output from results/test1_output.txt]
```

**Page 6: Test 2 - Load Balanced (2 Servers)**
```
TEST 2: LOAD BALANCED - 2 SERVERS

Configuration:
- ALB URL: cs6650-alb-631563720.us-west-2.elb.amazonaws.com
- Active Servers: Server 1 & Server 2
- Messages: 500,000
- Client Threads: 256
- Room Distribution: 20 rooms

Results:
- Throughput: 3512.96 msg/s
- Total Duration: 142.33 seconds
- Success Rate: 100% (500,000/500,000)
- Improvement vs Single Server: +18.7%

[Insert screenshot of client output from results/test2_output.txt]
```

**Page 7: Test 3 - Load Balanced (4 Servers)**
```
TEST 3: LOAD BALANCED - 4 SERVERS

Configuration:
- ALB URL: cs6650-alb-631563720.us-west-2.elb.amazonaws.com
- Active Servers: All 4 servers
- Messages: 500,000
- Client Threads: 256
- Room Distribution: 20 rooms

Results:
- Throughput: 3468.66 msg/s
- Total Duration: 144.19 seconds
- Success Rate: 99.6% (498,208/500,000)
- Improvement vs Single Server: +17.2%

Analysis:
4 servers showed slightly lower throughput than 2 servers likely due to:
- Increased coordination overhead
- Network latency across more instances
- Connection distribution challenges

[Insert screenshot of client output from results/test3_output.txt]
```

**Page 8: System Tuning Results**
```
SYSTEM TUNING - THREAD OPTIMIZATION

Tested thread counts: 64, 128, 256, 512
Test configuration: Single server, 500K messages

Results:
- 64 threads:  ~1800 msg/s
- 128 threads: ~2200 msg/s
- 256 threads: 2466.58 msg/s  ← OPTIMAL
- 512 threads: ~2100 msg/s (degradation due to overhead)

Optimal Configuration: 256 threads
Rationale:
- Balances concurrency with resource usage
- Avoids context switching overhead
- Matches well with t3.micro capacity (2 vCPU)

[Insert screenshot from results/tuning_test_results.txt]
```

**Pages 9-11: RabbitMQ Screenshots**
- Page 9: Overview page screenshot
- Page 10: Queues page screenshot
- Page 11: Live message activity screenshot

**Pages 12-13: ALB Screenshots**
- Page 12: Request count graph
- Page 13: Target health graph

---

### 3. Helper Script for Screenshots

I've created a script to run a quick test for capturing live RabbitMQ activity:

```bash
#!/bin/bash
# Run a 30-second test to generate RabbitMQ activity for screenshots

cd /Users/chendong/Desktop/6650/cs6650_assignments/assignment2/results

echo "Starting quick test for RabbitMQ screenshots..."
echo "Test duration: ~30 seconds"
echo ""
echo "While this runs:"
echo "1. Open http://18.246.237.223:15672 in browser"
echo "2. Login: guest / guest"
echo "3. Go to Overview tab"
echo "4. Take screenshot of the Message rates graph"
echo ""

sleep 5

java -jar /Users/chendong/Desktop/6650/cs6650_assignments/assignment1/client-part2/target/client-part2-1.0-SNAPSHOT-jar-with-dependencies.jar \
  --server-uri="ws://cs6650-alb-631563720.us-west-2.elb.amazonaws.com:8080/chat-server/chat/1" \
  --warmup-threads=0 \
  --main-threads=64 \
  --messages-per-thread=1000 \
  --total-messages=64000

echo ""
echo "Test complete! Take your screenshots now."
```

Save this as `run-quick-test-for-screenshots.sh` and run it when ready to capture RabbitMQ activity.

---

## Files Already Prepared

All in `/Users/chendong/Desktop/6650/cs6650_assignments/assignment2/`:

✅ `ARCHITECTURE_DOCUMENT.md` - Complete architecture documentation
✅ `ASSIGNMENT2_REQUIREMENTS_CHECK.md` - Verification that all requirements met
✅ `results/test1_output.txt` - Test 1 results
✅ `results/test2_output.txt` - Test 2 results
✅ `results/test3_output.txt` - Test 3 results
✅ `results/tuning_test_results.txt` - System tuning results

---

## Submission Checklist

Before submitting, verify:

- [ ] Git repository URL is correct and accessible
- [ ] Architecture document includes all 6 sections
- [ ] Configuration details page complete
- [ ] All 3 test results included with screenshots
- [ ] 3 RabbitMQ screenshots included
- [ ] 2 ALB screenshots included
- [ ] PDF is properly formatted and readable
- [ ] File size < 25MB (Canvas limit)
- [ ] Submitted to Canvas before deadline

---

## Quick Commands Reference

**Check RabbitMQ status:**
```bash
curl http://18.246.237.223:15672/api/overview -u guest:guest | python3 -m json.tool
```

**Check Consumer status:**
```bash
curl http://34.216.219.207:8080/health | python3 -m json.tool
```

**Check all servers are running:**
```bash
for ip in 18.237.196.134 54.186.55.54 44.251.147.184 34.213.93.17; do
  echo "Testing $ip..."
  curl -s -o /dev/null -w "HTTP %{http_code}\n" http://$ip:8080/chat-server/
done
```

**View existing test results:**
```bash
ls -lh /Users/chendong/Desktop/6650/cs6650_assignments/assignment2/results/
```

---

Good luck with your submission!
