# CS6650 Assignment 2 - Distributed Chat System

完整的分布式聊天系统实现，使用RabbitMQ消息队列和AWS负载均衡。

## 📁 项目结构

```
assignment2/
├── 📄 ARCHITECTURE_DOCUMENT.md          # 完整架构文档（用于提交）
├── 📄 SUBMISSION_GUIDE.md               # PDF提交指南
├── 📄 ASSIGNMENT2_REQUIREMENTS_CHECK.md # 需求验证清单
│
├── 📁 server-v2/                        # Server源码（RabbitMQ集成）
│   ├── src/
│   ├── pom.xml
│   └── target/chat-server.war           # 构建产物
│
├── 📁 consumer/                         # Consumer应用源码
│   ├── src/
│   ├── pom.xml
│   └── target/chat-consumer.jar         # 构建产物
│
├── 📁 deployment/                       # 部署脚本
│   ├── SETUP_ALL.sh                     # 一键部署所有组件
│   ├── setup-rabbitmq.sh                # 部署RabbitMQ
│   ├── setup-consumer.sh                # 部署Consumer
│   ├── deploy-all-servers.sh            # 部署4个Server实例
│   └── restart-all-tomcat.sh            # 重启所有Tomcat
│
├── 📁 testing/                          # 性能测试脚本
│   ├── run-test1-single-server.sh       # Test 1: 单服务器基准测试
│   ├── run-test2-alb-2servers.sh        # Test 2: 2服务器负载均衡
│   ├── run-test3-alb-4servers.sh        # Test 3: 4服务器负载均衡
│   ├── run-tuning-tests.sh              # 系统调优测试
│   └── run-quick-test-for-screenshots.sh # 截图助手脚本
│
└── 📁 results/                          # 测试结果
    ├── output/                          # 测试输出文件
    └── tuning/                          # 调优结果
```

## 🎯 快速开始

### 明天需要做的（40-60分钟）：

1. **收集截图** (15-20分钟)
   ```bash
   # 运行截图助手脚本
   cd testing
   ./run-quick-test-for-screenshots.sh
   
   # 同时打开浏览器
   # RabbitMQ: http://18.246.237.223:15672 (guest/guest)
   # AWS Console: EC2 → Load Balancers → cs6650-alb → Monitoring
   ```

2. **创建PDF** (20-30分钟)
   - 打开 `SUBMISSION_GUIDE.md`
   - 按照模板结构创建13页PDF
   - 所有文字内容已准备好，只需复制粘贴
   - 插入5张截图

## 🏗️ 系统架构

```
Client → ALB → [4 Servers] → RabbitMQ → Consumer → WebSocket Broadcast
```

### 组件清单：
- **RabbitMQ**: 18.246.237.223 (Docker)
- **Consumer**: 34.216.219.207 (Systemd service)
- **Server 1-4**: 4x t3.micro EC2实例
- **ALB**: cs6650-alb-631563720.us-west-2.elb.amazonaws.com

## 📊 性能测试结果

- **Test 1** (单服务器): 2960.65 msg/s
- **Test 2** (2服务器): 3512.96 msg/s (+18.7%)
- **Test 3** (4服务器): 3468.66 msg/s (+17.2%)
- **最优线程数**: 256

## ✅ 完成状态

- [x] Part 1: Queue Integration
- [x] Part 2: Consumer Implementation
- [x] Part 3: Load Balancing
- [x] Part 4: System Tuning
- [x] Performance Testing (3 tests)
- [ ] 收集截图（明天）
- [ ] 创建PDF（明天）

## 📝 文档说明

- `ARCHITECTURE_DOCUMENT.md` - 完整的架构文档（6个部分）
- `SUBMISSION_GUIDE.md` - PDF提交指南和模板
- `ASSIGNMENT2_REQUIREMENTS_CHECK.md` - 需求验证清单

所有技术实现100%完成！🎉
