# CS6650 Assignment 2 - Distributed Chat System

Complete distributed chat system implementation using RabbitMQ message queue and AWS load balancing.

## Project Structure

```
assignment2/
├── ARCHITECTURE_DOCUMENT.md          # Complete architecture documentation
├── Assignment2_Submission.md         # Final submission document (for PDF export)
│
├── server-v2/                        # Server source code (RabbitMQ integration)
│   ├── src/
│   ├── pom.xml
│   └── target/chat-server.war        # Build artifact
│
├── consumer/                         # Consumer application source code
│   ├── src/
│   ├── pom.xml
│   └── target/chat-consumer.jar      # Build artifact
│
├── deployment/                       # Deployment scripts
│   ├── SETUP_ALL.sh                  # One-command deployment
│   ├── setup-rabbitmq.sh             # Deploy RabbitMQ
│   ├── setup-consumer.sh             # Deploy Consumer
│   ├── deploy-all-servers.sh         # Deploy 4 Server instances
│   └── restart-all-tomcat.sh         # Restart all Tomcat servers
│
├── testing/                          # Performance test scripts
│   ├── run-test1-single-server.sh    # Test 1: Single server baseline
│   ├── run-test2-alb-2servers.sh     # Test 2: 2 servers with ALB
│   ├── run-test3-alb-4servers.sh     # Test 3: 4 servers with ALB
│   ├── run-tuning-tests.sh           # System tuning tests
│   └── run-quick-test-for-screenshots.sh # Screenshot helper script
│
├── monitoring/                       # Monitoring scripts
│   ├── check-system-health.sh        # System health verification
│   ├── monitor-rabbitmq.sh           # RabbitMQ monitoring
│   └── collect-system-metrics.sh     # System metrics collection
│
└── results/                          # Test results
    ├── output/                       # Test output files
    └── tuning/                       # Tuning results
```

## System Architecture

```
Client → ALB → [4 Servers] → RabbitMQ → Consumer → WebSocket Broadcast
```

### Component List:
- **RabbitMQ**: 54.245.205.40 (Docker)
- **Consumer**: 54.70.61.198 (Systemd service)
- **Server 1-4**: 4x t3.micro EC2 instances
  - 44.254.79.143
  - 50.112.195.157
  - 54.214.123.172
  - 54.190.115.9
- **ALB**: cs6650-alb-631563720.us-west-2.elb.amazonaws.com

## Performance Test Results

- **Test 1** (Single server): 2,960.65 msg/s (baseline)
- **Test 2** (2 servers): 3,512.96 msg/s (+18.7% improvement)
- **Test 3** (4 servers): 3,468.66 msg/s (+17.2% improvement)
- **Optimal thread count**: 256 client threads

## Quick Start

### Deploy All Components:
```bash
cd deployment
./SETUP_ALL.sh
```

### Run Performance Tests:
```bash
cd testing
./run-test1-single-server.sh
./run-test2-alb-2servers.sh
./run-test3-alb-4servers.sh
```

### Monitor System Health:
```bash
cd monitoring
./check-system-health.sh
./monitor-rabbitmq.sh
```

## Submission

**What to submit:** PDF exported from `Assignment2_Submission.md`

The submission document includes:
1. System architecture diagrams
2. Implementation details
3. Configuration details
4. Performance test results with screenshots

## Implementation Status

- Part 1: Queue Integration (RabbitMQ with topic exchange)
- Part 2: Consumer Implementation (20 threads, one per room)
- Part 3: Load Balancing (ALB with 4 servers, sticky sessions)
- Part 4: System Tuning (optimal: 256 client threads)
- Performance Testing (3 tests completed)
- Documentation (complete submission document ready)

All technical implementation is 100% complete!

## Key Design Decisions

**Message Ordering**: Single consumer thread per room ensures FIFO message delivery

**Scalability**: Topic exchange enables flexible routing, horizontal server scaling via ALB

**Reliability**: Publisher confirms, dead letter queue, systemd auto-restart for consumer

**Performance**: Channel pooling (20 channels), prefetch count tuning (10 messages)

**Monitoring**: Health endpoints, RabbitMQ management console, CloudWatch metrics
