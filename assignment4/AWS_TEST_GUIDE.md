# AWS 上运行 JMeter 测试指南

---

## 你的 AWS 架构

```
JMeter (本地/EC2)
       ↓
AWS ALB (cs6650-alb-631563720.us-west-2.elb.amazonaws.com)
       ↓
EC2 服务器集群 (4台 t3.micro)
       ↓
RabbitMQ (54.245.205.40)
       ↓
Consumer (54.70.61.198)
       ↓
PostgreSQL RDS (cs6650-chat-db.cr6q6mmc0zok.us-west-2.rds.amazonaws.com)
```

---

## JMeter 配置修改

### User Defined Variables

| Name | Value | 说明 |
|------|-------|------|
| SERVER_HOST | cs6650-alb-631563720.us-west-2.elb.amazonaws.com | **改成你的 ALB 地址** |
| SERVER_PORT | 8080 | 或 80（看你的 ALB 配置） |
| ROOM_COUNT | 20 | 聊天室数量 |

### 测试计划结构（AWS版）

```
Test Plan: CS6650 Chat System Test (AWS)
│
├── User Defined Variables
│   ├── SERVER_HOST = cs6650-alb-631563720.us-west-2.elb.amazonaws.com
│   ├── SERVER_PORT = 8080
│   └── ROOM_COUNT = 20
│
├── HTTP Request Defaults
│   ├── Server: ${SERVER_HOST}
│   ├── Port: ${SERVER_PORT}
│   └── Protocol: http
│
├── Thread Group 1: HTTP Read Operations (70%)
│   ├── 线程数: 700
│   ├── Ramp-up: 60秒
│   ├── Duration: 300秒
│   │
│   ├── HTTP Request: GET /health
│   ├── HTTP Request: GET /metrics
│   ├── Response Assertion (200)
│   └── Constant Timer (100ms)
│
├── Thread Group 2: WebSocket Write Operations (30%)
│   ├── 线程数: 300
│   ├── Ramp-up: 60秒
│   ├── Duration: 300秒
│   │
│   ├── WebSocket Open Connection
│   │   └── ws://${SERVER_HOST}:${SERVER_PORT}/chat/room${__Random(1,20)}
│   │
│   ├── Loop Controller (100次)
│   │   └── WebSocket Single Write Sampler
│   │
│   ├── WebSocket Close Connection
│   └── Constant Timer (50ms)
│
└── Listeners
    ├── Summary Report
    ├── Aggregate Report
    └── Response Time Graph
```

---

## 两种测试方式

### 方式1：从本地电脑测试 AWS

**优点**: 简单，不需要额外 EC2
**缺点**: 受本地网络影响

```bash
# 本地运行 JMeter
jmeter -n -t chat-baseline-test.jmx -l results-main.jtl -e -o report-main
```

### 方式2：从 EC2 上测试（推荐）

**优点**: 网络延迟低，结果更准确
**缺点**: 需要额外配置

```bash
# 1. 启动一台 EC2 作为测试机（建议 t3.medium 或更大）
# 2. SSH 连接
ssh -i cs6650-hw-key.pem ec2-user@<测试机IP>

# 3. 安装 JMeter
sudo yum install -y java-11-amazon-corretto
wget https://dlcdn.apache.org//jmeter/binaries/apache-jmeter-5.6.3.tgz
tar -xzf apache-jmeter-5.6.3.tgz
cd apache-jmeter-5.6.3

# 4. 安装 WebSocket 插件
wget https://repo1.maven.org/maven2/org/java-websocket/Java-WebSocket/1.5.3/Java-WebSocket-1.5.3.jar -P lib/
# 从 GitHub 下载 WebSocket Samplers 插件放到 lib/ext/

# 5. 上传测试计划
# 在本地执行:
scp -i cs6650-hw-key.pem chat-baseline-test.jmx ec2-user@<测试机IP>:~/apache-jmeter-5.6.3/

# 6. 运行测试
./bin/jmeter -n -t chat-baseline-test.jmx -l results.jtl -e -o report/

# 7. 下载结果
# 在本地执行:
scp -i cs6650-hw-key.pem -r ec2-user@<测试机IP>:~/apache-jmeter-5.6.3/report/ ./
```

---

## AWS 资源监控

### 方法1：CloudWatch（推荐）

**在 AWS Console 查看:**
1. 打开 CloudWatch
2. Metrics → EC2 → Per-Instance Metrics
3. 选择你的实例，查看 CPUUtilization
4. 同样查看 RDS 的 DatabaseConnections

**命令行获取:**
```bash
# EC2 CPU
aws cloudwatch get-metric-statistics \
  --namespace AWS/EC2 \
  --metric-name CPUUtilization \
  --dimensions Name=InstanceId,Value=i-xxxxxxxxx \
  --start-time $(date -u -v-10M +%Y-%m-%dT%H:%M:%SZ) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%SZ) \
  --period 60 \
  --statistics Average Maximum \
  --region us-west-2

# RDS 连接数
aws cloudwatch get-metric-statistics \
  --namespace AWS/RDS \
  --metric-name DatabaseConnections \
  --dimensions Name=DBInstanceIdentifier,Value=cs6650-chat-db \
  --start-time $(date -u -v-10M +%Y-%m-%dT%H:%M:%SZ) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%SZ) \
  --period 60 \
  --statistics Average Maximum \
  --region us-west-2

# RDS CPU
aws cloudwatch get-metric-statistics \
  --namespace AWS/RDS \
  --metric-name CPUUtilization \
  --dimensions Name=DBInstanceIdentifier,Value=cs6650-chat-db \
  --start-time $(date -u -v-10M +%Y-%m-%dT%H:%M:%SZ) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%SZ) \
  --period 60 \
  --statistics Average Maximum \
  --region us-west-2

# ALB 请求数
aws cloudwatch get-metric-statistics \
  --namespace AWS/ApplicationELB \
  --metric-name RequestCount \
  --dimensions Name=LoadBalancer,Value=app/cs6650-alb/xxxxxxxxx \
  --start-time $(date -u -v-10M +%Y-%m-%dT%H:%M:%SZ) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%SZ) \
  --period 60 \
  --statistics Sum \
  --region us-west-2
```

### 方法2：SSH 到 EC2 运行监控脚本

```bash
# 1. SSH 到你的服务器
ssh -i cs6650-hw-key.pem ec2-user@54.70.61.198

# 2. 运行监控
while true; do
  echo "$(date '+%Y-%m-%d %H:%M:%S'),$(top -bn1 | grep 'Cpu(s)' | awk '{print $2}'),$(free -m | awk 'NR==2{printf "%.1f", $3/$2*100}')"
  sleep 5
done | tee metrics.csv
```

### 方法3：Consumer /metrics 端点

```bash
# 持续采集 metrics
while true; do
  curl -s http://54.70.61.198:8080/metrics >> metrics.log
  echo "" >> metrics.log
  sleep 5
done
```

---

## 完整测试流程（AWS版）

### 测试 main 分支

```bash
# 1. 确保 AWS 服务正常运行
curl http://cs6650-alb-631563720.us-west-2.elb.amazonaws.com:8080/health

# 2. 开始 CloudWatch 监控（记录开始时间）
echo "Test Start: $(date -u +%Y-%m-%dT%H:%M:%SZ)"

# 3. 运行 JMeter 测试
jmeter -n -t chat-baseline-test.jmx -l results-main.jtl -e -o report-main

# 4. 记录结束时间
echo "Test End: $(date -u +%Y-%m-%dT%H:%M:%SZ)"

# 5. 获取 CloudWatch 数据
aws cloudwatch get-metric-statistics \
  --namespace AWS/EC2 \
  --metric-name CPUUtilization \
  --dimensions Name=InstanceId,Value=i-xxxxxxxxx \
  --start-time 2025-12-05T01:00:00Z \
  --end-time 2025-12-05T01:10:00Z \
  --period 60 \
  --statistics Average Maximum \
  --region us-west-2
```

### 切换到 Optimization1 分支

```bash
# 1. SSH 到服务器，切换代码
ssh -i cs6650-hw-key.pem ec2-user@<你的服务器IP>
cd /path/to/your/project
git checkout Optimization1
# 重启服务...

# 2. 重复测试步骤
jmeter -n -t chat-baseline-test.jmx -l results-opt1.jtl -e -o report-opt1
```

### 切换到 Optimization2 分支

```bash
# 同上
git checkout Optimization2
# 重启服务...
jmeter -n -t chat-baseline-test.jmx -l results-opt2.jtl -e -o report-opt2
```

---

## CloudWatch 截图指南

### 获取 CPU/Memory 图表

1. 打开 AWS Console → CloudWatch
2. 左侧菜单 → Metrics → All metrics
3. EC2 → Per-Instance Metrics
4. 勾选你的实例的 `CPUUtilization`
5. 点击 "Graphed metrics" 标签
6. 设置时间范围为测试期间
7. 截图保存

### 获取 RDS 图表

1. CloudWatch → Metrics → RDS
2. 选择 `DatabaseConnections` 和 `CPUUtilization`
3. 设置时间范围
4. 截图保存

### 获取 ALB 图表

1. CloudWatch → Metrics → ApplicationELB
2. 选择 `RequestCount`, `TargetResponseTime`
3. 截图保存

---

## 要收集的 AWS 指标

| 指标 | 来源 | 说明 |
|-----|------|------|
| EC2 CPUUtilization | CloudWatch | 服务器 CPU |
| EC2 NetworkIn/Out | CloudWatch | 网络流量 |
| RDS CPUUtilization | CloudWatch | 数据库 CPU |
| RDS DatabaseConnections | CloudWatch | 数据库连接数 |
| RDS FreeableMemory | CloudWatch | 数据库可用内存 |
| ALB RequestCount | CloudWatch | 请求总数 |
| ALB TargetResponseTime | CloudWatch | 响应时间 |
| ALB HTTPCode_Target_2XX | CloudWatch | 成功请求数 |
| ALB HTTPCode_Target_5XX | CloudWatch | 错误请求数 |

---

## 性能对比表（AWS版）

### 表1：JMeter 指标对比

| 指标 | main | Optimization1 | 提升% | Optimization2 | 提升% |
|-----|------|---------------|-------|---------------|-------|
| 平均响应时间 (ms) | | | | | |
| p95 响应时间 (ms) | | | | | |
| p99 响应时间 (ms) | | | | | |
| 吞吐量 (req/s) | | | | | |
| 错误率 (%) | | | | | |

### 表2：AWS CloudWatch 指标对比

| 指标 | main | Optimization1 | Optimization2 |
|-----|------|---------------|---------------|
| EC2 平均 CPU | | | |
| EC2 峰值 CPU | | | |
| RDS 平均 CPU | | | |
| RDS 峰值连接数 | | | |
| ALB 请求总数 | | | |
| ALB 平均响应时间 | | | |

---

## 常见问题

### Q: 连接 ALB 失败？
```bash
# 检查 ALB 是否正常
curl -v http://cs6650-alb-631563720.us-west-2.elb.amazonaws.com:8080/health

# 检查安全组是否开放 8080 端口
```

### Q: WebSocket 连接失败？
```bash
# 检查 ALB 是否支持 WebSocket
# ALB 需要配置 Sticky Sessions 或使用 NLB
```

### Q: CloudWatch 没有数据？
```bash
# 检查时间范围是否正确（UTC时间）
# 检查 Instance ID 是否正确
# 数据可能有 5 分钟延迟
```

### Q: 本地测试延迟很高？
```bash
# 正常现象，本地到 AWS 有网络延迟
# 建议从 EC2 上运行测试获得更准确结果
```
