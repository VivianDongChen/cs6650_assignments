# CS6650 Assignment 3 - Persistence and Data Management

High-performance distributed chat system with PostgreSQL database persistence, achieving **7,880 msg/sec peak throughput** and **100% success rate** across 6.9M messages.

---

## 📊 Key Performance Metrics

| Test | Messages | Duration | Throughput | Success Rate |
|------|----------|----------|------------|--------------|
| Test 1 (Baseline) | 500K | 64 sec | 7,813 msg/sec | 100% |
| Test 2 (Stress) | 1M | 127 sec | 7,880 msg/sec | 100% |
| Test 3 (Endurance) | 5.4M | 15 min | 5,988 msg/sec | 100% |

**Database Performance**: 8-10% CPU, <10ms query latency, 5 optimized indexes

---

## 📂 Repository Structure

```
assignment3/
├── ASSIGNMENT3_REPORT.md       # Complete technical report (export to PDF)
│
├── database/                   # PostgreSQL setup
│   ├── schema.sql             # Table schema, indexes, materialized views
│   └── connection-info.txt    # RDS connection details
│
├── consumer-v3/                # Consumer with database persistence
│   ├── src/                   # Java source code
│   ├── pom.xml               # Maven dependencies
│   └── README.md             # Consumer setup guide
│
├── monitoring/                 # Metrics collection scripts
│   ├── collect_db_stats.py
│   ├── display_batch_analysis.py
│   ├── display_metrics.py
│   └── display_part3_evidence.py
│
├── load-tests/                 # Test scripts and results
│   ├── load_test.py           # Test 1 & 2 script
│   ├── auto_endurance_test.py # Test 3 script
│   ├── verify_queries.py      # Query performance validation
│   ├── test_summary.txt       # Test 1 & 2 results
│   ├── endurance_*.txt/json   # Test 3 results
│   └── screenshots/           # AWS CloudWatch screenshots
│
└── config/                     # Configuration files
    ├── database.properties    # Database & connection pool config
    └── consumer.env.example   # Consumer environment template
```

---

## 🚀 Quick Start

### Prerequisites

- **Java 11+** (for consumer)
- **Python 3.8+** (for load tests)
- **PostgreSQL client** (optional, for manual DB access)
- **AWS CLI** (optional, for AWS operations)

### 1. Database Setup

#### Option A: Use Existing RDS Instance

Connection details are in `database/connection-info.txt`:

```bash
Host: cs6650-chat-db.cr6q6mmc0zok.us-west-2.rds.amazonaws.com
Port: 5432
Database: chatdb
Username: postgres
Password: [See connection-info.txt]
```

#### Option B: Create New Database

```bash
# Connect to PostgreSQL
psql -h <your-host> -U postgres -d postgres

# Create database
CREATE DATABASE chatdb;

# Apply schema
psql -h <your-host> -U postgres -d chatdb -f database/schema.sql
```

### 2. Run Consumer

```bash
cd consumer-v3

# Copy environment template
cp .env.example .env

# Edit .env with your database credentials
nano .env

# Build and run
mvn clean package
java -jar target/chat-consumer-v3.jar
```

**Consumer Endpoints**:
- Health Check: `http://localhost:8080/health`
- Metrics API: `http://localhost:8080/metrics`

### 3. Run Load Tests

```bash
# Install dependencies
python3 -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate
pip install -r requirements.txt

# Test 1: 500K messages (baseline)
python3 load-tests/load_test.py --messages 500000 --workers 20

# Test 2: 1M messages (stress test)
python3 load-tests/load_test.py --messages 1000000 --workers 20

# Test 3: 15-minute endurance test
python3 load-tests/auto_endurance_test.py
```

**Test Configuration** (edit script before running):
- RabbitMQ host
- Number of rooms (default: 20)
- Number of workers (default: 16-20)

---

## 📈 Monitoring

### Real-Time Metrics

```bash
# Check consumer health
curl http://localhost:8080/health

# Get database metrics
curl http://localhost:8080/metrics | python3 -m json.tool

# Display metrics (formatted)
python3 monitoring/display_metrics.py http://localhost:8080/metrics
```

### Database Performance

```bash
# Collect database statistics
python3 monitoring/collect_db_stats.py

# View Part 3 evidence
python3 monitoring/display_part3_evidence.py
```

### AWS CloudWatch

Monitor RDS metrics in AWS Console:
- **CPU Utilization**: Should be <15% during peak load
- **Database Connections**: Typically 10-11 connections
- **Write IOPS**: 80-150 IOPS during tests
- **Freeable Memory**: Should remain stable (>500 MB)

---

## 🗄️ Database Design

### Schema

- **messages** table: Core message storage with 6 columns
- **3 materialized views**: user_stats, room_stats, hourly_stats

### Indexes (5 total)

1. **Primary Key** (message_id): Uniqueness constraint
2. **Room Timeline** (room_id, timestamp DESC): Covering index
3. **User History** (user_id, timestamp DESC): Covering index
4. **BRIN Index** (timestamp): Time-series queries
5. **Composite** (user_id, room_id, timestamp): Analytics

**Performance**:
- Room message query: 8ms (target: <100ms)
- User history query: 10ms (target: <200ms)
- Active users count: 25ms (target: <500ms)

### Connection Pool (HikariCP)

```properties
Min Idle: 10
Max Pool Size: 50
Connection Timeout: 30 seconds
Idle Timeout: 10 minutes
Max Lifetime: 30 minutes
```

---

## 🧪 Query Validation

Verify query performance against targets:

```bash
python3 load-tests/verify_queries.py
```

Expected output:
```
✓ Room messages query: 8ms (target: <100ms)
✓ User history query: 10ms (target: <200ms)
✓ Active users count: 25ms (target: <500ms)
✓ User's rooms query: 12ms (target: <50ms)
```

---

## 📊 Performance Optimization

### Batch Processing

```java
Batch Size: 1,000 messages
Flush Interval: 500ms
Throughput: 8,000 msg/sec
```

**Trade-off**: 500ms latency for 16x throughput gain

### Write Performance

| Strategy | Throughput | CPU | Network RTT |
|----------|-----------|-----|-------------|
| Individual Inserts | 500 msg/sec | 80% | 1 per message |
| Batch (100) | 3,000 msg/sec | 40% | 1 per 100 |
| **Batch (1000)** | **8,000 msg/sec** | **25%** | **1 per 1,000** |

---

## 🔧 Configuration

### Database Configuration

Edit `config/database.properties`:

```properties
# Database Connection
DB_HOST=your-rds-endpoint.rds.amazonaws.com
DB_PORT=5432
DB_NAME=chatdb
DB_USERNAME=postgres
DB_PASSWORD=your-password

# Connection Pool
DB_POOL_MIN_IDLE=10
DB_POOL_MAX_SIZE=50

# Batch Processing
BATCH_SIZE=1000
FLUSH_INTERVAL_MS=500
```

### Consumer Configuration

Edit `consumer-v3/.env`:

```bash
DB_JDBC_URL=jdbc:postgresql://your-host:5432/chatdb
DB_USERNAME=postgres
DB_PASSWORD=your-password
BATCH_SIZE=1000
FLUSH_INTERVAL_MS=500
```

---

## 📝 Documentation

- **[ASSIGNMENT3_REPORT.md](ASSIGNMENT3_REPORT.md)**: Complete technical report
  - Part A: Database Design (2 pages)
  - Part B: Performance Report (comprehensive analysis)
  
- **[database/schema.sql](database/schema.sql)**: PostgreSQL schema definition

- **[consumer-v3/README.md](consumer-v3/README.md)**: Consumer setup guide

---

## 🎯 Load Test Results

### Test 1: Baseline (500K Messages)

```
Duration: 64 seconds
Throughput: 7,813 msg/sec
Success Rate: 100%
Database CPU: 8-10%
```

### Test 2: Stress (1M Messages)

```
Duration: 127 seconds
Throughput: 7,880 msg/sec
Success Rate: 100%
Database CPU: 8-12%
```

### Test 3: Endurance (15 Minutes)

```
Duration: 15.0 minutes
Messages: 5,397,372
Throughput: 5,988 msg/sec (sustained)
Success Rate: 100%
CPU: 3-10% (stable)
Memory: 60-80 MB freeable (no leaks)
```

**Conclusion**: System meets all performance targets with significant headroom for scaling.

---

## 🐛 Troubleshooting

### Consumer Won't Start

```bash
# Check database connectivity
cd consumer-v3
./test-db-connection.sh

# Check Java version
java -version  # Should be 11+

# Rebuild consumer
mvn clean package
```

### Database Connection Errors

```bash
# Test connection manually
psql -h <host> -U postgres -d chatdb

# Check connection pool settings in .env
# Verify RDS security group allows inbound on port 5432
```

### Load Test Failures

```bash
# Check RabbitMQ is running
curl http://<rabbitmq-host>:15672

# Check consumer is running
curl http://localhost:8080/health

# Verify Python dependencies
pip install -r requirements.txt
```

---

## 📚 Additional Documentation

For more details, see:
- **[ASSIGNMENT3_REPORT.md](ASSIGNMENT3_REPORT.md)**: Complete technical report with database design and performance analysis
- **[Consumer README](consumer-v3/README.md)**: Detailed consumer setup and deployment guide
- **[Database Schema](database/schema.sql)**: PostgreSQL table definitions, indexes, and materialized views
