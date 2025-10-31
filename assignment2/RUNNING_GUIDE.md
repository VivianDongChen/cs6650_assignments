# Assignment 2 - 运行指南

## 🎯 项目概述

Assignment 2 在 Assignment 1 的基础上实现了真实的消息分发系统：
- **server-v2**: WebSocket服务器，接收消息并发布到RabbitMQ
- **consumer**: 消费者应用，从RabbitMQ读取消息并广播到房间内所有用户

## 📋 前提条件

### 1. 确认RabbitMQ正在运行

```bash
# 检查RabbitMQ容器状态
docker ps | grep rabbitmq

# 应该看到类似这样的输出：
# hitch-rabbitmq ... Up 7 days ... 0.0.0.0:5672->5672/tcp, 0.0.0.0:15672->15672/tcp
```

✅ 你的RabbitMQ已经在运行了！
- **AMQP端口**: localhost:5672
- **管理控制台**: http://localhost:15672 (guest/guest)

### 2. 已构建的项目

✅ 两个项目都已成功构建：
- `server-v2/target/chat-server.war`
- `consumer/target/chat-consumer.jar`

## 🚀 运行步骤

### 步骤 1: 启动 Consumer 应用

Consumer需要先启动，因为它负责从RabbitMQ消费消息并广播。

```bash
cd /Users/chendong/Desktop/6650/cs6650_assignments/assignment2/consumer

# 运行consumer
java -jar target/chat-consumer.jar
```

**期望输出**:
```
=== Starting CS6650 Chat Consumer Application ===
Connected to RabbitMQ at localhost:5672
RoomManager initialized with 20 rooms
Starting 40 consumer threads
Thread 0 started consuming from room.1
Thread 0 started consuming from room.2
...
Consumer application started successfully
Consumer application is running. Press Ctrl+C to stop.
```

### 步骤 2: 部署 server-v2 到 Tomcat

**选项 A: 使用现有的Tomcat实例**

如果你在Assignment 1中已经部署过Tomcat：

```bash
# 1. 停止现有的Tomcat
# 找到你的Tomcat安装目录，例如：
# /path/to/tomcat/bin/shutdown.sh

# 2. 删除旧的war和部署文件
rm -rf /path/to/tomcat/webapps/chat-server*

# 3. 复制新的war文件
cp /Users/chendong/Desktop/6650/cs6650_assignments/assignment2/server-v2/target/chat-server.war /path/to/tomcat/webapps/

# 4. 启动Tomcat
/path/to/tomcat/bin/startup.sh
```

**选项 B: 使用Maven Tomcat插件 (本地测试)**

在 `server-v2/pom.xml` 中添加Tomcat插件，然后：

```bash
cd /Users/chendong/Desktop/6650/cs6650_assignments/assignment2/server-v2
mvn tomcat7:run
```

**选项 C: 使用Docker Tomcat**

```bash
# 启动Tomcat容器
docker run -d --name tomcat-server \
  -p 8080:8080 \
  -v /Users/chendong/Desktop/6650/cs6650_assignments/assignment2/server-v2/target/chat-server.war:/usr/local/tomcat/webapps/chat-server.war \
  tomcat:9.0
```

### 步骤 3: 验证服务器启动

```bash
# 检查健康端点
curl http://localhost:8080/chat-server/health

# 应该返回: {"status":"UP"}
```

### 步骤 4: 运行客户端测试

使用Assignment 1的客户端来测试：

```bash
cd /Users/chendong/Desktop/6650/cs6650_assignments/assignment1/client-part2

# 运行单个房间测试
java -jar target/client-part2.jar \
  --server-url=ws://localhost:8080/chat-server/chat/1 \
  --num-threads=10 \
  --messages-per-thread=100

# 或运行多房间测试
java -jar target/client-part2.jar \
  --server-url=ws://localhost:8080/chat-server/chat \
  --num-threads=20 \
  --messages-per-thread=100 \
  --num-rooms=5
```

## 📊 监控和观察

### 1. RabbitMQ管理控制台

访问 http://localhost:15672
- 用户名: `guest`
- 密码: `guest`

在控制台中你可以看到：
- **Queues**: 20个房间队列 (room.1 到 room.20)
- **Message rates**: 发布和消费速率
- **Queue depths**: 队列深度随时间的变化

### 2. Consumer日志

Consumer应用会每30秒输出统计信息：

```
=== RoomManager Statistics ===
Total sessions: 0
Messages processed: 1234
Broadcasts succeeded: 5678
Broadcasts failed: 0
Room 1: 5 sessions
Room 2: 3 sessions
...
```

### 3. Server日志

Server会记录：
- 发布到RabbitMQ的消息
- WebSocket连接/断开
- 验证错误

## 🔧 配置参数

### Consumer环境变量

```bash
# 修改consumer线程数
export CONSUMER_THREADS=80

# 修改prefetch数量
export PREFETCH_COUNT=20

# 修改统计输出间隔（秒）
export STATS_INTERVAL=60

# 然后运行consumer
java -jar target/chat-consumer.jar
```

### Server环境变量

```bash
# 修改Channel Pool大小
export CHANNEL_POOL_SIZE=50

# 修改RabbitMQ主机（如果不是localhost）
export RABBITMQ_HOST=192.168.1.100
export RABBITMQ_PORT=5672
```

## ❓ 故障排除

### 问题1: Consumer无法连接到RabbitMQ

**错误**: `Connection refused`

**解决方案**:
```bash
# 检查RabbitMQ是否运行
docker ps | grep rabbitmq

# 如果没有运行，启动它
docker start hitch-rabbitmq
```

### 问题2: Server无法发布消息

**错误**: `Failed to setup RabbitMQ`

**解决方案**:
- 确保RabbitMQ正在运行
- 检查防火墙设置
- 验证端口5672可访问

### 问题3: 没有消息被消费

**可能原因**:
1. Consumer没有运行 - 先启动Consumer
2. 队列没有正确绑定 - 检查RabbitMQ管理控制台
3. Server没有发布消息 - 检查server日志

## 📝 测试场景

### 基本功能测试

```bash
# Terminal 1: 启动consumer
cd consumer && java -jar target/chat-consumer.jar

# Terminal 2: 启动server (如果使用独立Tomcat)

# Terminal 3: 运行客户端
cd ../assignment1/client-part2
java -jar target/client-part2.jar \
  --server-url=ws://localhost:8080/chat-server/chat/1 \
  --num-threads=5 \
  --messages-per-thread=20
```

### 性能测试 (500K消息)

```bash
# 确保consumer和server都在运行

cd ../assignment1/client-part2
java -jar target/client-part2.jar \
  --server-url=ws://localhost:8080/chat-server/chat \
  --num-threads=128 \
  --messages-per-thread=3906 \
  --num-rooms=20
```

### 多房间测试

```bash
# 测试消息是否正确路由到不同房间
java -jar target/client-part2.jar \
  --server-url=ws://localhost:8080/chat-server/chat \
  --num-threads=40 \
  --messages-per-thread=100 \
  --num-rooms=10
```

## 🎯 下一步

1. **性能调优**: 调整CONSUMER_THREADS和CHANNEL_POOL_SIZE
2. **AWS部署**: 部署到EC2并配置ALB
3. **负载测试**: 测试2个和4个服务器实例
4. **监控**: 收集队列深度和吞吐量指标

## 📞 获取帮助

如果遇到问题：
1. 检查RabbitMQ管理控制台
2. 查看consumer和server日志
3. 验证所有端口都可访问
4. 确保启动顺序正确（Consumer -> Server -> Client）

---

**祝测试顺利！** 🚀
