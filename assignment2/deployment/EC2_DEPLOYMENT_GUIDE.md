# EC2 RabbitMQ 部署指南

## 📋 准备工作

### 1. AWS EC2实例要求

创建EC2实例时的配置：

- **AMI**: Amazon Linux 2023 或 Amazon Linux 2
- **Instance Type**:
  - 开发/测试: `t2.micro` (免费套餐)
  - 生产环境: `t2.small` 或更大
- **Storage**: 8GB+ (默认即可)
- **Key Pair**: `cs6650-hw2-key` (你已经有了)

### 2. 安全组配置 ⚠️ 重要！

在创建EC2实例时，配置Security Group允许以下入站规则：

| Type | Protocol | Port Range | Source | Description |
|------|----------|------------|--------|-------------|
| SSH | TCP | 22 | My IP (你的IP) | SSH访问 |
| Custom TCP | TCP | 5672 | Anywhere (0.0.0.0/0) | RabbitMQ AMQP |
| Custom TCP | TCP | 15672 | Anywhere (0.0.0.0/0) | RabbitMQ Management |

**注意**：如果忘记配置，可以在EC2 Console → Security Groups → Edit Inbound Rules 中添加。

---

## 🚀 部署步骤

### 步骤 1: 获取EC2信息

从AWS Console获取：

1. 进入 EC2 Dashboard
2. 点击你的实例
3. 记录以下信息：
   - **公网 IPv4 地址** (例如: `54.123.45.67`)
   - **公网 IPv4 DNS** (例如: `ec2-54-123-45-67.compute-1.amazonaws.com`)

### 步骤 2: SSH连接到EC2

在本地终端运行：

```bash
# 进入密钥所在目录
cd /Users/chendong/Desktop/6650

# 连接到EC2 (替换 YOUR-EC2-PUBLIC-IP)
ssh -i cs6650-hw2-key.pem ec2-user@YOUR-EC2-PUBLIC-IP
```

**示例**：
```bash
ssh -i cs6650-hw2-key.pem ec2-user@54.123.45.67
```

**首次连接会提示**：
```
The authenticity of host '54.123.45.67' can't be established.
Are you sure you want to continue connecting (yes/no)?
```
输入 `yes` 并回车。

**成功连接后会看到**：
```
   __|  __|_  )
   _|  (     /   Amazon Linux 2
  ___|\___|___|
```

### 步骤 3: 上传并运行安装脚本

#### 方法 A: 直接在EC2上运行（推荐）

在EC2实例上（SSH连接后），复制粘贴以下完整脚本：

```bash
# 创建安装脚本
cat > setup-rabbitmq.sh << 'EOF'
#!/bin/bash

set -e

echo "========================================="
echo "CS6650 Assignment 2 - RabbitMQ Setup"
echo "========================================="

echo ""
echo "[1/6] Updating system packages..."
sudo yum update -y

echo ""
echo "[2/6] Installing Docker..."
sudo yum install -y docker
sudo service docker start
sudo usermod -a -G docker ec2-user

echo ""
echo "[3/6] Configuring Docker to start on boot..."
sudo systemctl enable docker

echo ""
echo "[4/6] Waiting for Docker to be ready..."
sleep 3

echo ""
echo "[5/6] Pulling RabbitMQ Docker image..."
sudo docker pull rabbitmq:3-management

echo ""
echo "[6/6] Starting RabbitMQ container..."
sudo docker run -d \
  --name rabbitmq \
  --restart=always \
  -p 5672:5672 \
  -p 15672:15672 \
  -e RABBITMQ_DEFAULT_USER=guest \
  -e RABBITMQ_DEFAULT_PASS=guest \
  rabbitmq:3-management

echo ""
echo "Waiting for RabbitMQ to start (30 seconds)..."
sleep 30

echo ""
echo "========================================="
echo "RabbitMQ Status:"
echo "========================================="
sudo docker ps | grep rabbitmq

PUBLIC_IP=$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4)

echo ""
echo "========================================="
echo "✅ RabbitMQ Installation Complete!"
echo "========================================="
echo ""
echo "Connection Details:"
echo "  AMQP Port:    5672"
echo "  Management:   http://$PUBLIC_IP:15672"
echo "  Username:     guest"
echo "  Password:     guest"
echo ""
echo "Environment Variables for Server/Consumer:"
echo "  export RABBITMQ_HOST=$PUBLIC_IP"
echo "  export RABBITMQ_PORT=5672"
echo "  export RABBITMQ_USERNAME=guest"
echo "  export RABBITMQ_PASSWORD=guest"
echo ""
EOF

# 运行脚本
chmod +x setup-rabbitmq.sh
./setup-rabbitmq.sh
```

#### 方法 B: 从本地上传脚本

如果你想从本地上传脚本：

```bash
# 在本地终端运行（不是在EC2上）
cd /Users/chendong/Desktop/6650/cs6650_assignments/assignment2/deployment

# 上传脚本到EC2
scp -i /Users/chendong/Desktop/6650/cs6650-hw2-key.pem \
  setup-rabbitmq-ec2.sh \
  ec2-user@YOUR-EC2-PUBLIC-IP:~/

# 然后SSH到EC2并运行
ssh -i /Users/chendong/Desktop/6650/cs6650-hw2-key.pem ec2-user@YOUR-EC2-PUBLIC-IP
chmod +x setup-rabbitmq-ec2.sh
./setup-rabbitmq-ec2.sh
```

### 步骤 4: 验证安装

安装完成后，验证RabbitMQ是否正常运行：

```bash
# 在EC2上运行
sudo docker ps | grep rabbitmq

# 应该看到类似输出：
# CONTAINER ID   IMAGE                   STATUS          PORTS
# abc123def456   rabbitmq:3-management   Up 2 minutes    0.0.0.0:5672->5672/tcp, 0.0.0.0:15672->15672/tcp
```

### 步骤 5: 访问管理控制台

在浏览器中打开（替换为你的EC2公网IP）：

```
http://YOUR-EC2-PUBLIC-IP:15672
```

**登录信息**：
- 用户名: `guest`
- 密码: `guest`

**如果无法访问**：
1. 检查安全组是否允许端口15672
2. 检查RabbitMQ容器是否运行: `sudo docker ps`
3. 检查防火墙: `sudo iptables -L`

---

## 🔧 配置Server和Consumer连接到EC2 RabbitMQ

### 获取EC2公网IP

在EC2实例上运行：
```bash
curl http://169.254.169.254/latest/meta-data/public-ipv4
```

假设输出是: `54.123.45.67`

### 配置Server-v2

在server-v2部署时设置环境变量：

```bash
export RABBITMQ_HOST=54.123.45.67
export RABBITMQ_PORT=5672
export RABBITMQ_USERNAME=guest
export RABBITMQ_PASSWORD=guest

# 然后部署server-v2
```

### 配置Consumer

在consumer部署时设置相同的环境变量：

```bash
export RABBITMQ_HOST=54.123.45.67
export RABBITMQ_PORT=5672
export RABBITMQ_USERNAME=guest
export RABBITMQ_PASSWORD=guest

# 然后运行consumer
java -jar chat-consumer.jar
```

---

## 🧪 测试连接

### 从本地测试连接

在本地终端测试是否能连接到EC2的RabbitMQ：

```bash
# 使用telnet测试AMQP端口
telnet 54.123.45.67 5672

# 如果连接成功会看到乱码（这是正常的AMQP协议握手）
# 按 Ctrl+] 然后输入 quit 退出

# 测试管理端口
curl http://54.123.45.67:15672
# 应该返回HTML页面
```

---

## 📊 监控和管理

### 查看RabbitMQ日志

```bash
# SSH到EC2后运行
sudo docker logs rabbitmq

# 实时查看日志
sudo docker logs -f rabbitmq
```

### 重启RabbitMQ

```bash
sudo docker restart rabbitmq
```

### 停止RabbitMQ

```bash
sudo docker stop rabbitmq
```

### 查看RabbitMQ统计信息

访问管理控制台: http://YOUR-EC2-PUBLIC-IP:15672

或使用命令行：
```bash
sudo docker exec rabbitmq rabbitmqctl status
```

---

## ⚠️ 常见问题

### 问题1: 无法SSH连接

**错误**: `Permission denied (publickey)`

**解决方案**:
```bash
# 1. 检查密钥权限
ls -la cs6650-hw2-key.pem
# 应该是 -r--------

# 2. 如果不是，设置权限
chmod 400 cs6650-hw2-key.pem

# 3. 确认用户名正确（Amazon Linux用ec2-user）
ssh -i cs6650-hw2-key.pem ec2-user@YOUR-EC2-IP
```

### 问题2: 无法访问管理控制台

**错误**: 浏览器无法打开 `http://EC2-IP:15672`

**解决方案**:
1. 检查安全组是否开放端口15672
2. 检查RabbitMQ是否运行: `sudo docker ps`
3. 在EC2上测试: `curl localhost:15672`

### 问题3: Server/Consumer无法连接

**错误**: `Connection refused`

**解决方案**:
1. 检查安全组是否开放端口5672
2. 验证RABBITMQ_HOST环境变量是否正确
3. 在EC2上测试: `telnet localhost 5672`
4. 检查RabbitMQ日志: `sudo docker logs rabbitmq`

### 问题4: Docker权限问题

**错误**: `permission denied while trying to connect to the Docker daemon socket`

**解决方案**:
```bash
# 重新登录以应用用户组更改
exit
ssh -i cs6650-hw2-key.pem ec2-user@YOUR-EC2-IP

# 或立即应用
sudo usermod -a -G docker ec2-user
newgrp docker
```

---

## 🎯 部署检查清单

完成部署后，确认以下所有项：

- [ ] EC2实例正在运行
- [ ] 安全组允许端口22, 5672, 15672
- [ ] RabbitMQ Docker容器正在运行
- [ ] 可以访问管理控制台 (http://EC2-IP:15672)
- [ ] 可以用guest/guest登录管理控制台
- [ ] Server-v2配置了正确的RABBITMQ_HOST
- [ ] Consumer配置了正确的RABBITMQ_HOST
- [ ] 在管理控制台中看到chat.exchange
- [ ] 在管理控制台中看到20个队列(room.1到room.20)

---

## 📞 下一步

完成RabbitMQ部署后：

1. ✅ 部署server-v2到另一个EC2实例
2. ✅ 部署consumer到另一个EC2实例
3. ✅ 运行客户端测试
4. ✅ 配置Application Load Balancer (Part 3)

---

**需要帮助？** 如果遇到任何问题，检查：
1. RabbitMQ日志: `sudo docker logs rabbitmq`
2. EC2系统日志: `sudo journalctl -xe`
3. 安全组配置
4. 网络连接: `ping`, `telnet`, `curl`
