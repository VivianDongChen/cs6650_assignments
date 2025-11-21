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
├── assignment2/          # Message Distribution and Queue Management
│   ├── server-v2/       # Server with RabbitMQ integration
│   ├── consumer/        # Message consumer application
│   ├── deployment/      # AWS ALB configuration and scripts
│   ├── monitoring/      # Monitoring scripts and tools
│   └── results/         # Test results and documentation
│
└── assignment3/          # Persistence and Data Management
    ├── database/        # PostgreSQL schema and setup
    ├── consumer-v3/     # Consumer with database persistence
    ├── monitoring/      # Metrics collection scripts
    ├── load-tests/      # Test scripts, results, and screenshots
    ├── config/          # Configuration files
    └── ASSIGNMENT3_REPORT.md  # Complete technical report
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

### [Assignment 3: Persistence and Data Management](./assignment3/)
- **Highlights**:
  - PostgreSQL 16.6 database with optimized schema design
  - 5 strategic indexes (B-tree, BRIN, covering indexes)
  - HikariCP connection pooling (10-50 connections)
  - Batch processing: 1,000 messages per batch, 8,000 msg/s throughput
  - Achieved 7,880 msg/s peak throughput with 100% success rate
  - 15-minute endurance test: 5.4M messages, 5,988 msg/s sustained
  - Database performance: 8-10% CPU, sub-10ms query latency
  - Complete technical report with database design and performance analysis

## 🎯 Quick Start

See individual assignment folders for detailed setup and running instructions.

