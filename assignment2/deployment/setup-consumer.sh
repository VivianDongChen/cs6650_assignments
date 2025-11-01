#!/bin/bash
# Setup Consumer on EC2: 34.214.37.149

set -e

CONSUMER_IP="34.214.37.149"
RABBITMQ_IP="54.190.49.133"
KEY_PATH="/Users/chendong/Desktop/6650/cs6650-hw2-key.pem"
JAR_PATH="/Users/chendong/Desktop/6650/cs6650_assignments/assignment2/consumer/target/chat-consumer.jar"

echo "=========================================="
echo "Setting up Consumer"
echo "IP: $CONSUMER_IP"
echo "RabbitMQ: $RABBITMQ_IP"
echo "=========================================="

echo "[1/3] Uploading Consumer JAR..."
scp -i "$KEY_PATH" -o StrictHostKeyChecking=no "$JAR_PATH" "ec2-user@${CONSUMER_IP}:~/"

echo "[2/3] Configuring Consumer service..."
ssh -i "$KEY_PATH" -o StrictHostKeyChecking=no "ec2-user@${CONSUMER_IP}" << EOFSCRIPT
set -e

echo "Creating consumer directory..."
mkdir -p /home/ec2-user/consumer
mv ~/chat-consumer.jar /home/ec2-user/consumer/

echo "Creating systemd service file..."
sudo tee /etc/systemd/system/chat-consumer.service > /dev/null << 'EOF'
[Unit]
Description=Chat Consumer Service
After=network.target

[Service]
Type=simple
User=ec2-user
WorkingDirectory=/home/ec2-user/consumer

Environment="RABBITMQ_HOST=54.190.49.133"
Environment="RABBITMQ_PORT=5672"
Environment="RABBITMQ_USERNAME=guest"
Environment="RABBITMQ_PASSWORD=guest"
Environment="CONSUMER_THREADS=20"
Environment="MAX_RETRIES=3"
Environment="HEALTH_PORT=8080"
Environment="STATS_INTERVAL=30"

ExecStart=/usr/bin/java -Xmx512m -Xms256m -jar /home/ec2-user/consumer/chat-consumer.jar

Restart=always
RestartSec=10
StartLimitInterval=200
StartLimitBurst=5

StandardOutput=journal
StandardError=journal
SyslogIdentifier=chat-consumer

[Install]
WantedBy=multi-user.target
EOF

echo "Reloading systemd daemon..."
sudo systemctl daemon-reload

echo "Enabling consumer service..."
sudo systemctl enable chat-consumer

EOFSCRIPT

echo "[3/3] Starting Consumer..."
ssh -i "$KEY_PATH" -o StrictHostKeyChecking=no "ec2-user@${CONSUMER_IP}" << 'EOFSCRIPT'
set -e

echo "Starting consumer service..."
sudo systemctl restart chat-consumer

echo "Waiting for consumer to start (10 seconds)..."
sleep 10

echo ""
echo "============================================"
echo "Consumer Status:"
echo "============================================"
sudo systemctl status chat-consumer --no-pager

echo ""
echo "============================================"
echo "Recent Consumer Logs:"
echo "============================================"
sudo journalctl -u chat-consumer -n 20 --no-pager

EOFSCRIPT

echo ""
echo "============================================"
echo "Testing Consumer Health Check..."
echo "============================================"
curl -s http://${CONSUMER_IP}:8080/health | jq . || echo "Health check endpoint not responding yet"

echo ""
echo "✓ Consumer setup completed successfully!"
echo ""
echo "Useful commands:"
echo "  Check status:  ssh -i $KEY_PATH ec2-user@${CONSUMER_IP} 'sudo systemctl status chat-consumer'"
echo "  View logs:     ssh -i $KEY_PATH ec2-user@${CONSUMER_IP} 'sudo journalctl -u chat-consumer -f'"
echo "  Restart:       ssh -i $KEY_PATH ec2-user@${CONSUMER_IP} 'sudo systemctl restart chat-consumer'"
echo ""
