#!/bin/bash

################################################################################
# Consumer Deployment Script for Assignment 2
# Run this script on the Consumer EC2 instance
################################################################################

set -e

RABBITMQ_HOST="${RABBITMQ_HOST:-54.188.26.217}"
CONSUMER_JAR="chat-consumer.jar"

echo "========================================="
echo "CS6650 Consumer Deployment"
echo "========================================="

# Update system
echo ""
echo "[1/5] Updating system packages..."
sudo yum update -y

# Install Java
echo ""
echo "[2/5] Installing Java 11..."
sudo yum install -y java-11-amazon-corretto
java -version

# Create application directory
echo ""
echo "[3/5] Setting up application directory..."
mkdir -p ~/consumer
cd ~/consumer

# Note: JAR file should be uploaded manually via SCP
if [ ! -f "$CONSUMER_JAR" ]; then
    echo ""
    echo "ERROR: $CONSUMER_JAR not found in current directory"
    echo "Please upload the JAR file first:"
    echo "  scp -i your-key.pem chat-consumer.jar ec2-user@YOUR-EC2-IP:~/consumer/"
    exit 1
fi

# Create systemd service
echo ""
echo "[4/5] Creating systemd service..."
sudo tee /etc/systemd/system/chat-consumer.service > /dev/null <<EOF
[Unit]
Description=CS6650 Chat Consumer Application
After=network.target

[Service]
Type=simple
User=ec2-user
WorkingDirectory=/home/ec2-user/consumer
Environment="RABBITMQ_HOST=$RABBITMQ_HOST"
Environment="RABBITMQ_PORT=5672"
Environment="RABBITMQ_USERNAME=guest"
Environment="RABBITMQ_PASSWORD=guest"
Environment="CONSUMER_THREADS=40"
Environment="PREFETCH_COUNT=10"
ExecStart=/usr/bin/java -jar /home/ec2-user/consumer/$CONSUMER_JAR

# Auto-restart on failure
Restart=always
RestartSec=10

# Logging
StandardOutput=journal
StandardError=journal
SyslogIdentifier=chat-consumer

[Install]
WantedBy=multi-user.target
EOF

# Start service
echo ""
echo "[5/5] Starting consumer service..."
sudo systemctl daemon-reload
sudo systemctl enable chat-consumer
sudo systemctl start chat-consumer

# Wait for service to start
sleep 5

# Check status
echo ""
echo "========================================="
echo "Deployment Complete"
echo "========================================="
echo ""
sudo systemctl status chat-consumer --no-pager

echo ""
echo "Useful commands:"
echo "  Check status:  sudo systemctl status chat-consumer"
echo "  View logs:     sudo journalctl -u chat-consumer -f"
echo "  Restart:       sudo systemctl restart chat-consumer"
echo "  Stop:          sudo systemctl stop chat-consumer"
echo ""
