# Assignment 2: Message Distribution and Queue Management

Building on Assignment 1, this implements real message distribution using RabbitMQ message queues and AWS load balancing.

## 🎯 Objectives

- Integrate RabbitMQ for message distribution
- Implement message consumer application
- Configure AWS Application Load Balancer
- Performance tuning and optimization
- Achieve stable queue profiles under load

## 📁 Project Structure

```
assignment2/
├── server-v2/       # WebSocket server with RabbitMQ publisher
├── consumer/        # Message consumer and broadcaster
├── deployment/      # AWS deployment scripts and configs
├── monitoring/      # Queue monitoring and metrics tools
└── results/         # Test results and performance analysis
```

## 🏗️ Architecture

```
Client → ALB → [Server1, Server2, Server3, Server4] → RabbitMQ → Consumer → WebSocket Broadcast
```

### Components

1. **server-v2**: Modified WebSocket server that publishes messages to RabbitMQ
2. **consumer**: Multi-threaded application that consumes messages and broadcasts to room participants
3. **RabbitMQ**: Message queue system (20 queues, one per room)
4. **AWS ALB**: Load balancer distributing connections across 4 server instances

## 🚀 Getting Started

### Prerequisites

- Java 11 or higher
- Maven 3.6+
- Docker (for local RabbitMQ)
- AWS Account (for deployment)

### Local Development

1. **Start RabbitMQ**:
```bash
docker run -d --name rabbitmq \
  -p 5672:5672 \
  -p 15672:15672 \
  rabbitmq:3-management
```

2. **Build server-v2**:
```bash
cd server-v2
mvn clean package
```

3. **Build consumer**:
```bash
cd consumer
mvn clean package
```

4. **Run locally**:
```bash
# Terminal 1: Start server
java -jar server-v2/target/server-v2.jar

# Terminal 2: Start consumer
java -jar consumer/target/consumer.jar

# Terminal 3: Run client
cd ../assignment1/client-part2
java -jar target/client-part2.jar --server-url=ws://localhost:8080/chat-server/chat/1
```

### AWS Deployment

See [deployment/README.md](./deployment/README.md) for detailed deployment instructions.

## 📊 Performance Testing

### Test Scenarios

1. **Single Server Baseline**: 500K messages, 1 server instance
2. **Load Balanced (2 instances)**: 500K messages, 2 server instances
3. **Load Balanced (4 instances)**: 500K messages, 4 server instances

### Target Metrics

- Queue depth: < 1000 messages
- Consumer lag: < 100ms
- No message loss
- Stable queue profile (plateau, not sawtooth)

## 📈 Monitoring

RabbitMQ Management Console: `http://localhost:15672` (guest/guest)

Monitor:
- Queue depths over time
- Message rates (publish/consume)
- Connection details
- Consumer performance

## 🛠️ Configuration

Key configuration parameters:

- **Channel Pool Size**: 20 (adjustable)
- **Consumer Threads**: 40 (tunable based on load)
- **Prefetch Count**: 10-50 (optimized for throughput)
- **ALB Sticky Session**: 3600 seconds

## 📝 Submission

- **Deadline**: October 31, 2025, 5:00 PM PST
- **Deliverables**:
  - Architecture document (2 pages max)
  - Test results with screenshots
  - Performance analysis
  - Configuration details

## 🎓 Grading Criteria

- Queue Integration: 15 points
- System Design: 10 points
- Single Instance Performance: 10 points
- Load Balanced Performance: 10 points
- **Bonus**: Fastest implementations (+1 or +2 points)

## 📚 References

- [RabbitMQ Java Client Guide](https://www.rabbitmq.com/java-client.html)
- [AWS Application Load Balancer](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/)
- [Assignment 2 Specification](../../cs6650-fall-2025/assignments/assignment2.md)

## 👤 Author

Vivian Dong Chen

---

**Status**: 🚧 In Development
**Last Updated**: October 24, 2025
