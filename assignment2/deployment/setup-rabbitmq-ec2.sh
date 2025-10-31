#!/bin/bash

################################################################################
# RabbitMQ EC2 Setup Script for Assignment 2
#
# This script sets up RabbitMQ on an Amazon Linux 2 EC2 instance
# Run this script after SSH into your EC2 instance
################################################################################

set -e

echo "========================================="
echo "CS6650 Assignment 2 - RabbitMQ Setup"
echo "========================================="

# Update system packages
echo ""
echo "[1/6] Updating system packages..."
sudo yum update -y

# Install Docker
echo ""
echo "[2/6] Installing Docker..."
sudo yum install -y docker
sudo service docker start
sudo usermod -a -G docker ec2-user

# Configure Docker to start on boot
echo ""
echo "[3/6] Configuring Docker to start on boot..."
sudo systemctl enable docker

# Wait for Docker service to be ready
echo ""
echo "[4/6] Waiting for Docker to be ready..."
sleep 3

# Pull RabbitMQ image
echo ""
echo "[5/6] Pulling RabbitMQ Docker image..."
sudo docker pull rabbitmq:3-management

# Start RabbitMQ container
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

# Wait for RabbitMQ to start
echo ""
echo "Waiting for RabbitMQ to start (30 seconds)..."
sleep 30

# Check RabbitMQ status
echo ""
echo "========================================="
echo "RabbitMQ Status:"
echo "========================================="
sudo docker ps | grep rabbitmq

# Get EC2 instance information
echo ""
echo "========================================="
echo "EC2 Instance Information:"
echo "========================================="
PRIVATE_IP=$(hostname -I | awk '{print $1}')
echo "Private IP: $PRIVATE_IP"
echo ""
echo "To get Public IP, run:"
echo "  curl http://169.254.169.254/latest/meta-data/public-ipv4"
echo ""

PUBLIC_IP=$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4)
echo "Public IP: $PUBLIC_IP"

# Display connection information
echo ""
echo "========================================="
echo "RabbitMQ Installation Complete"
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
echo "Next Steps:"
echo "  1. Verify security group allows inbound traffic on ports 5672 and 15672"
echo "  2. Access management console: http://$PUBLIC_IP:15672"
echo "  3. Deploy server-v2 and consumer with RABBITMQ_HOST=$PUBLIC_IP"
echo ""
echo "========================================="
