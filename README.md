# CS6650 - Scalable Distributed Systems

Building a scalable distributed cloud-based chat system through a series of assignments.

## 📂 Repository Structure

```
cs6650_assignments/
├── assignment1/          # WebSocket Chat Server and Client
│   ├── server/          # WebSocket server implementation
│   ├── client-part1/    # Basic load testing client
│   ├── client-part2/    # Client with performance analysis
│   └── results/         # Test results and design document
│
└── assignment2/          # Message Distribution and Queue Management
    ├── server-v2/       # Server with RabbitMQ integration
    ├── consumer/        # Message consumer application
    ├── deployment/      # AWS ALB configuration and scripts
    ├── monitoring/      # Monitoring scripts and tools
    └── results/         # Test results and documentation
```

##  Assignments

### [Assignment 1: WebSocket Chat Server and Client](./assignment1/)
- **Highlights**:
  - Implemented multithreaded WebSocket server with message validation
  - Built high-performance client with connection pooling
  - Achieved 7,941 msg/s throughput on EC2 with 288 threads
  - Comprehensive performance analysis and metrics collection

### [Assignment 2: Message Distribution and Queue Management](./assignment2/)
- **Key Features**:
  - RabbitMQ message queue integration
  - Multi-threaded message consumer
  - AWS Application Load Balancer setup
  - Performance tuning and optimization

##  Quick Start

See individual assignment folders for detailed setup and running instructions.

