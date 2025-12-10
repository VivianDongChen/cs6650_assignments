# Redis缓存层 - 快速开始指南

## ⚡ 5分钟快速部署

### 1️⃣ 启动Redis (任选一种)

**本地Docker**
```bash
docker run -d -p 6379:6379 --name redis redis:7-alpine
```

**本地安装**
```bash
# macOS
brew install redis
redis-server

# Linux
sudo apt-get install redis-server
redis-server
```

**AWS ElastiCache** (生产环境)
```bash
# 创建ElastiCache集群后，获取端点
# 例如: my-cache.abc123.ng.0001.use1.cache.amazonaws.com
```

### 2️⃣ 配置环境变量

```bash
# 本地开发
export REDIS_HOST=localhost
export REDIS_PORT=6379
export REDIS_DB=0

# AWS生产
export REDIS_HOST=my-cache.abc123.ng.0001.use1.cache.amazonaws.com
export REDIS_PORT=6379
export REDIS_PASSWORD=your-auth-token
```

### 3️⃣ 编译和运行

```bash
# 编译assignment3 consumer-v3
cd assignment3/consumer-v3
mvn clean package -DskipTests

# 运行应用
java -jar target/chat-consumer-v3.jar
```

### 4️⃣ 验证缓存工作

```bash
# 打开另一个终端

# 第一次请求 (DB查询，~60ms)
curl http://localhost:8080/metrics | head -20

# 第二次请求 (Redis命中，~3ms)
curl http://localhost:8080/metrics | head -20

# 查看缓存统计 (应用日志)
tail -f console.log | grep "Cache Statistics"
```

---

## 🔍 验证缓存是否工作

### 方法1: 查看应用日志

```
[INFO] Redis connection pool initialized: host=localhost, port=6379, db=0
[INFO] RedisPool(host=localhost, port=6379, db=0, active=2, idle=3)
[INFO] Metrics cache hit (2.5ms)
[INFO] === Metrics Cache Statistics === Hits: 10, Misses: 2, HitRate: 83.33%
```

**说明**: 如果看到 "cache hit" 和 hitRate > 80%，说明缓存工作正常

### 方法2: Redis CLI检查

```bash
# 连接Redis
redis-cli -h localhost -p 6379

# 查看缓存的metrics键
KEYS metrics:*

# 查看内容
GET metrics:all
GET metrics:by_room

# 查看过期时间 (秒)
TTL metrics:all
```

### 方法3: 性能对比

```bash
# 安装ab工具 (Apache Bench)
brew install httpd  # macOS
# 或
sudo apt-get install apache2-utils  # Linux

# 测试 (10次请求，1秒间隔)
for i in {1..10}; do
  time curl -s http://localhost:8080/metrics > /dev/null
  sleep 1
done

# 预期结果:
# 第1次: ~60-80ms (DB查询)
# 第2-10次: ~2-4ms (Redis缓存)
```

---

## 🛠️ 常见问题排查

### ❌ "Redis connection pool initialization failed"

**原因**: Redis服务未启动或地址不正确

**解决方案**:
```bash
# 1. 检查Redis是否运行
redis-cli ping
# 输出: PONG (表示正常)

# 2. 检查环境变量
echo $REDIS_HOST
echo $REDIS_PORT

# 3. 重新启动应用
```

### ❌ "Metrics cache miss" 一直出现

**原因**: 缓存未命中（正常现象），但命中率不高

**解决方案**:
```bash
# 1. 检查TTL是否设置过短
# 修改 MetricsCacheDecorator.java 的 TTL_* 常量

# 2. 检查是否有缓存清除操作
grep -r "invalidateAll" src/

# 3. 检查请求间隔是否超过TTL
# 默认TTL: 10分钟，建议10秒内重复请求测试
```

### ❌ Redis内存持续增长

**原因**: 可能有内存泄漏

**解决方案**:
```bash
# 1. 检查Redis内存使用
redis-cli INFO memory

# 2. 清除所有缓存
redis-cli FLUSHDB

# 3. 检查数据大小
redis-cli DBSIZE

# 4. 查看最大缓存项
redis-cli --bigkeys
```

---

## 📊 监控命令

### Redis监控

```bash
# 查看实时命令
redis-cli MONITOR

# 查看慢查询
redis-cli SLOWLOG GET 10

# 查看统计
redis-cli INFO stats

# 查看客户端连接
redis-cli CLIENT LIST
```

### 应用监控

```bash
# 查看缓存统计 (每30秒输出)
tail -f consumer.log | grep "Cache Statistics"

# 实时性能指标
tail -f consumer.log | grep "ms"
```

---

## 🎯 性能基准目标

| 指标 | 目标 | 验证方法 |
|------|------|--------|
| 冷启动(首次) | ~60ms | `time curl /metrics` |
| 缓存命中 | ~3ms | `time curl /metrics` (第2次) |
| 命中率 | >85% | 查看日志 "HitRate" |
| Redis延迟 | <5ms | `redis-cli SLOWLOG GET 10` |
| 应用内存 | <200MB | `top` 或 `jps -l` |

---

## 🔄 完整工作流示例

```bash
# 1. 启动Redis
docker run -d -p 6379:6379 redis:7-alpine

# 2. 配置环境
export REDIS_HOST=localhost
export REDIS_PORT=6379

# 3. 编译
cd assignment3/consumer-v3
mvn clean package -DskipTests

# 4. 启动应用
java -jar target/chat-consumer-v3.jar &

# 5. 等待应用启动
sleep 3

# 6. 第一次请求 (DB查询)
time curl http://localhost:8080/metrics | jq .coreQueries.totalMessages

# 7. 第二次请求 (Redis缓存)
time curl http://localhost:8080/metrics | jq .coreQueries.totalMessages

# 8. 验证缓存
redis-cli KEYS metrics:*
redis-cli GET metrics:all | jq .coreQueries | head

# 9. 查看统计
tail -10 consumer.log | grep Statistics
```

**预期输出**:
```
第一次: real 0m0.068s  (68ms - DB查询)
第二次: real 0m0.003s  (3ms - Redis命中)
改进: 95.6%降低 ✅

日志: Hits: 1, Misses: 1, HitRate: 50.00%
```

---

## 🚀 部署检查清单

- [ ] Redis已启动且可连接 (`redis-cli ping` → PONG)
- [ ] 环境变量已设置 (REDIS_HOST/PORT/DB)
- [ ] Jedis依赖在pom.xml中 (4.4.3)
- [ ] 代码编译成功 (mvn clean package)
- [ ] 应用启动日志无错误
- [ ] 第一次/metrics请求 ~60-80ms
- [ ] 第二次/metrics请求 ~2-4ms
- [ ] 应用日志显示 "cache hit"
- [ ] Redis中有 "metrics:*" 键
- [ ] 命中率 > 85%

---

## 📞 技术支持

如遇到问题：

1. **查看日志**
   ```bash
   grep -i redis consumer.log
   grep -i cache consumer.log
   ```

2. **检查Redis连接**
   ```bash
   redis-cli -h $REDIS_HOST -p $REDIS_PORT ping
   ```

3. **参考完整文档**
   - `REDIS_CACHE_IMPLEMENTATION.md` - 详细设计文档
   - `redis.env.example` - 配置示例

---

## 🎉 成功标志

当你看到以下日志时，说明Redis缓存层已成功运行：

```
✓ Redis connection pool initialized: host=localhost, port=6379, db=0
✓ MetricsCacheDecorator initialized with Redis caching
✓ Redis caching layer initialized for metrics queries
✓ Metrics cached (2.5ms)
✓ === Metrics Cache Statistics === Hits: 100, Misses: 5, HitRate: 95.24%
```

**现在你已经拥有一个高性能的分布式缓存层！** 🚀
