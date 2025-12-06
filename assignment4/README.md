# CS6650 Assignment 4 - System Optimization

## 目录结构

```
assignment4/
├── jmeter/                          # JMeter 测试计划
│   ├── chat-baseline-test.jmx       # 基准性能测试 (1000用户, 5分钟)
│   └── chat-stress-test.jmx         # 压力测试 (500用户, 30分钟)
│
├── monitoring/                      # 资源监控工具
│   ├── monitor.sh                   # 资源采集脚本 (CPU, Memory, DB)
│   └── plot_metrics.py              # 生成资源使用图表
│
├── report/                          # 报告模板
│   ├── bottleneck_analysis_template.md    # 瓶颈分析模板
│   └── performance_comparison_template.md # 性能对比模板
│
└── README.md                        # 本文件
```

---

## 快速开始

### 1. 安装 JMeter

```bash
# macOS
brew install jmeter

# 或手动下载
# https://jmeter.apache.org/download_jmeter.cgi
```

### 2. 安装 JMeter WebSocket 插件

```bash
# 1. 启动 JMeter GUI
jmeter

# 2. Options → Plugins Manager → Available Plugins
# 3. 搜索 "WebSocket Samplers by Peter Doornbosch"
# 4. 安装并重启
```

### 3. 配置测试参数

编辑 `.jmx` 文件中的 User Defined Variables:

```
SERVER_HOST = localhost          # 或你的服务器地址
SERVER_PORT = 8080               # 服务器端口
ROOM_COUNT = 20                  # 聊天室数量
```

---

## 测试流程

### Step 1: 测试 Baseline (main 分支)

```bash
# 1. 部署 main 分支代码
git checkout main
# ... 部署步骤 ...

# 2. 启动资源监控 (在服务器上运行)
chmod +x monitoring/monitor.sh
./monitoring/monitor.sh metrics-main.csv 5

# 3. 运行 JMeter 测试 (在另一个终端)
jmeter -n -t jmeter/chat-baseline-test.jmx \
       -l results-main.jtl \
       -e -o report-main/

# 4. 停止监控 (Ctrl+C)
# 5. 生成资源图表
python3 monitoring/plot_metrics.py metrics-main.csv
```

### Step 2: 测试 Optimization 1

```bash
# 1. 部署 Optimization1 分支
git checkout Optimization1
# ... 部署步骤 ...

# 2. 启动监控
./monitoring/monitor.sh metrics-opt1.csv 5

# 3. 运行测试
jmeter -n -t jmeter/chat-baseline-test.jmx \
       -l results-opt1.jtl \
       -e -o report-opt1/

# 4. 生成图表
python3 monitoring/plot_metrics.py metrics-opt1.csv
```

### Step 3: 测试 Optimization 2

```bash
# 1. 部署 Optimization2 分支
git checkout Optimization2
# ... 部署步骤 ...

# 2. 启动监控
./monitoring/monitor.sh metrics-opt2.csv 5

# 3. 运行测试
jmeter -n -t jmeter/chat-baseline-test.jmx \
       -l results-opt2.jtl \
       -e -o report-opt2/

# 4. 生成图表
python3 monitoring/plot_metrics.py metrics-opt2.csv
```

### Step 4: 生成对比图

```bash
python3 monitoring/plot_metrics.py --compare \
    metrics-main.csv \
    metrics-opt1.csv \
    metrics-opt2.csv
```

---

## 测试参数说明

### Baseline Performance Test

| 参数 | 值 | 说明 |
|-----|-----|------|
| 并发用户 | 1000 | 700读 + 300写 |
| 持续时间 | 300秒 | 5分钟 |
| 读写比例 | 70%/30% | 符合作业要求 |
| API调用 | ~100K | 每用户约100次请求 |

### Stress Test

| 参数 | 值 | 说明 |
|-----|-----|------|
| 并发用户 | 500 | 持续压力 |
| 持续时间 | 1800秒 | 30分钟 |
| 目标吞吐量 | 6000 req/min | 通过 Throughput Timer 控制 |

---

## 收集的指标

### JMeter 指标
- Average Response Time (平均响应时间)
- p95 / p99 Response Time (百分位响应时间)
- Throughput (吞吐量 req/s)
- Error Rate (错误率)

### 资源指标
- CPU Utilization (CPU 使用率)
- Memory Utilization (内存使用率)
- Database Connections (数据库连接数)
- System Load Average (系统负载)

---

## 报告模板使用

1. 复制 `report/` 目录下的模板
2. 填入实际测试数据
3. 插入截图和图表
4. 导出为 PDF

---

## 常见问题

### Q: JMeter 报 Connection Refused?
确保服务器正在运行，检查 SERVER_HOST 和 SERVER_PORT 配置。

### Q: WebSocket Sampler 找不到?
安装 WebSocket 插件后需要重启 JMeter。

### Q: 如何在 AWS 上运行监控脚本?
```bash
# SSH 到 EC2
ssh -i your-key.pem ec2-user@your-ip

# 上传脚本
scp -i your-key.pem monitoring/monitor.sh ec2-user@your-ip:~/

# 运行
./monitor.sh metrics.csv 5
```

### Q: 数据库连接数显示 N/A?
需要设置环境变量:
```bash
export DB_HOST=your-rds-endpoint
export DB_NAME=chatdb
export DB_USER=postgres
export DB_PASSWORD=your-password
```
