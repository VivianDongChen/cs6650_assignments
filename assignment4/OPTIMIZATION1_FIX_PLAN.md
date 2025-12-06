# Optimization1 性能优化方案

## 问题诊断

### 当前测试结果

| 分支 | 吞吐量 (req/s) | 平均响应时间 | 最大响应时间 | 错误率 |
|-----|---------------|-------------|-------------|--------|
| **Main** (server-v2) | 22,463.2/s | 11ms | 10,889ms | 0.98% |
| **Optimization1** (server-v4) | 19,726.7/s | 12ms | 19,204ms | 0.97% |
| **Optimization2** (server-v2) | 23,429.0/s | 10ms | 10,889ms | 1.02% |

**问题**: Optimization1 性能下降 12%，最大响应时间翻倍

---

## 根本原因分析

### 1. 同步确认阻塞（主要瓶颈）

**位置**: `assignment4/server-v4/src/main/java/com/cs6650/chat/server/queue/MessagePublisher.java`

**问题代码** (第 126-127 行):
```java
// Wait for confirmation
channel.waitForConfirmsOrDie(5000);
```

**影响**: 每发送一条消息，线程都会阻塞等待 RabbitMQ 返回确认，最长等待 5 秒。这是一个同步操作，严重限制了吞吐量。

### 2. 队列数量增加 3 倍

| 版本 | 队列数量 | 队列命名 |
|-----|---------|---------|
| server-v2 (Main) | 20 | `room.1`, `room.2`, ... `room.20` |
| server-v4 (Opt1) | 60 | `room.1.partition.0`, `room.1.partition.1`, `room.1.partition.2`, ... |

**影响**: RabbitMQ 需要管理更多队列，增加内存和 CPU 开销

### 3. 额外计算开销

**问题代码** (第 111-115 行):
```java
// Calculate partition based on userId hash
String userId = String.valueOf(chatMessage.getUserId());
int partition = Math.abs(userId.hashCode()) % PARTITIONS_PER_ROOM;

// Publish to partitioned queue
String routingKey = String.format("room.%s.partition.%d", roomId, partition);
```

**影响**: 每条消息额外执行:
- `hashCode()` 计算
- 取模运算
- `String.format()` 字符串格式化

### 4. 分区无实际收益

**原因**: Consumer 端没有配合优化，没有利用分区实现并行消费，分区的意义没有体现。

---

## 优化方案

### 方案 A：移除同步确认（推荐 - 最简单有效）

#### 改动内容

**文件**: `assignment4/server-v4/src/main/java/com/cs6650/chat/server/queue/MessagePublisher.java`

**删除第 126-127 行**:
```java
// 删除以下两行代码：
// Wait for confirmation
channel.waitForConfirmsOrDie(5000);
```

#### 优缺点

| 优点 | 缺点 |
|-----|------|
| 改动最小，只删除 2 行 | 消息可能丢失 |
| 吞吐量预计提升 30-50% | 无法保证消息一定到达 RabbitMQ |
| 不影响其他逻辑 | 生产环境需要其他可靠性保证 |

#### 适用场景
- 性能测试
- 可接受少量消息丢失的场景
- 有其他重试机制的系统

---

### 方案 B：异步确认（平衡性能和可靠性）

#### 改动内容

**文件**: `assignment4/server-v4/src/main/java/com/cs6650/chat/server/queue/MessagePublisher.java`

**1. 添加异步确认监听器** (在 `ChannelPool.java` 中):
```java
// 在创建 channel 后添加
channel.confirmSelect();
channel.addConfirmListener(
    (deliveryTag, multiple) -> {
        // 消息确认成功的回调
        LOGGER.debug("Message confirmed: {}", deliveryTag);
    },
    (deliveryTag, multiple) -> {
        // 消息确认失败的回调（需要重试）
        LOGGER.warn("Message nacked: {}", deliveryTag);
    }
);
```

**2. 删除同步等待** (在 `MessagePublisher.java` 中):
```java
// 删除这行
// channel.waitForConfirmsOrDie(5000);
```

#### 优缺点

| 优点 | 缺点 |
|-----|------|
| 保持消息可靠性 | 改动较多 |
| 吞吐量提升 | 需要处理确认失败的重试逻辑 |
| 生产级别的解决方案 | 复杂度增加 |

---

### 方案 C：减少分区数 + 移除同步确认（综合优化）

#### 改动内容

**1. 设置环境变量减少分区数**:
```bash
# 在 EC2 服务器上设置
export PARTITIONS_PER_ROOM=1
```

或修改代码默认值:
```java
// MessagePublisher.java 第 26 行
private static final int PARTITIONS_PER_ROOM = Integer.parseInt(
    System.getenv().getOrDefault("PARTITIONS_PER_ROOM", "1")  // 改为 1
);
```

**2. 移除同步确认** (同方案 A)

**3. 简化 routing key 计算**:
```java
// 原代码
String routingKey = String.format("room.%s.partition.%d", roomId, partition);

// 优化后（当 PARTITIONS_PER_ROOM=1 时）
String routingKey = "room." + roomId + ".partition.0";
```

#### 优缺点

| 优点 | 缺点 |
|-----|------|
| 综合性能最优 | 改动较多 |
| 减少队列管理开销 | 分区功能被弱化 |
| 简化系统复杂度 | 需要重新部署 |

---

## 推荐执行步骤

### 执行方案 A（最快见效）

```bash
# 1. 修改代码
cd /Users/chendong/Desktop/6650/cs6650_assignments
git checkout Optimization1

# 2. 编辑文件，删除第 126-127 行
# 文件: assignment4/server-v4/src/main/java/com/cs6650/chat/server/queue/MessagePublisher.java

# 3. 重新构建
cd assignment4/server-v4
mvn clean package -DskipTests

# 4. 部署到 4 台 EC2 服务器
scp -i ~/Desktop/6650/cs6650-hw2-key.pem target/chat-server.war ec2-user@34.212.201.144:/tmp/
scp -i ~/Desktop/6650/cs6650-hw2-key.pem target/chat-server.war ec2-user@34.209.220.207:/tmp/
scp -i ~/Desktop/6650/cs6650-hw2-key.pem target/chat-server.war ec2-user@35.92.155.171:/tmp/
scp -i ~/Desktop/6650/cs6650-hw2-key.pem target/chat-server.war ec2-user@54.201.180.2:/tmp/

# 5. 在每台服务器上部署
ssh -i ~/Desktop/6650/cs6650-hw2-key.pem ec2-user@<SERVER_IP> \
  "rm -rf /opt/tomcat/webapps/chat-server* && \
   cp /tmp/chat-server.war /opt/tomcat/webapps/ && \
   /opt/tomcat/bin/shutdown.sh; sleep 2; /opt/tomcat/bin/startup.sh"

# 6. 等待启动，运行测试
cd /Users/chendong/Desktop/6650/cs6650_assignments/assignment4/jmeter
jmeter -n -t chat-baseline-test.jmx -l results-opt1-fixed.jtl -e -o report-opt1-fixed
```

---

## 预期结果

| 指标 | 优化前 | 优化后（预估） |
|-----|-------|--------------|
| 吞吐量 | 19,726/s | 25,000-30,000/s |
| 平均响应时间 | 12ms | 8-10ms |
| 最大响应时间 | 19,204ms | < 12,000ms |

---

## 附录：代码位置参考

```
cs6650_assignments/
├── assignment2/
│   └── server-v2/                    # Main 分支使用
│       └── src/main/java/.../queue/
│           └── MessagePublisher.java  # 也有同步确认，但队列更少
│
└── assignment4/
    └── server-v4/                    # Optimization1 分支使用
        └── src/main/java/.../queue/
            ├── ChannelPool.java       # Channel 池管理
            └── MessagePublisher.java  # 需要修改的文件
```
