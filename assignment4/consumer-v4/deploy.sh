#!/bin/bash

# ============================================
# CS6650 Consumer v3 - Deployment Script
# Author: Dong Chen
# Date: 2025-11-19
# ============================================

set -e  # Exit on error

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
EC2_IP=""  # TODO: Set your EC2 instance IP
EC2_USER="ec2-user"
SSH_KEY="$HOME/Desktop/6650/cs6650-hw2-key.pem"
REMOTE_DIR="~/consumer-v3"

# Helper functions
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

# Check if EC2_IP is set
if [ -z "$EC2_IP" ]; then
    log_error "EC2_IP is not set. Please edit this script and set your EC2 instance IP."
    exit 1
fi

log_info "=== CS6650 Consumer v3 Deployment ==="
log_info "Target: $EC2_USER@$EC2_IP"
echo ""

# Step 1: Build
log_info "Step 1: Building consumer-v3..."
mvn clean package
if [ $? -ne 0 ]; then
    log_error "Build failed"
    exit 1
fi
log_info "✓ Build successful"
echo ""

# Step 2: Stop existing consumer (if running)
log_info "Step 2: Stopping existing consumer (if running)..."
ssh -i "$SSH_KEY" "$EC2_USER@$EC2_IP" "pkill -f 'chat-consumer-v3.jar' || true"
sleep 2
log_info "✓ Existing consumer stopped"
echo ""

# Step 3: Copy files to EC2
log_info "Step 3: Copying files to EC2..."
ssh -i "$SSH_KEY" "$EC2_USER@$EC2_IP" "mkdir -p $REMOTE_DIR"
scp -i "$SSH_KEY" -r target "$EC2_USER@$EC2_IP:$REMOTE_DIR/"
scp -i "$SSH_KEY" .env.example "$EC2_USER@$EC2_IP:$REMOTE_DIR/"
scp -i "$SSH_KEY" README.md "$EC2_USER@$EC2_IP:$REMOTE_DIR/"
log_info "✓ Files copied"
echo ""

# Step 4: Set up environment and start consumer
log_info "Step 4: Starting consumer on EC2..."
ssh -i "$SSH_KEY" "$EC2_USER@$EC2_IP" << 'ENDSSH'
cd ~/consumer-v3

# Set environment variables
export DB_JDBC_URL="jdbc:postgresql://cs6650-chat-db.cr6q6mmc0zok.us-west-2.rds.amazonaws.com:5432/chatdb"
export DB_USERNAME="postgres"
export DB_PASSWORD="MyPassword123"
export RABBITMQ_HOST="localhost"
export BATCH_SIZE="1000"
export FLUSH_INTERVAL_MS="500"
export CONSUMER_THREADS="20"
export PREFETCH_COUNT="10"

# Start consumer in background
nohup java -jar target/chat-consumer-v3.jar > consumer.log 2>&1 &

# Get PID
CONSUMER_PID=$!
echo $CONSUMER_PID > consumer.pid

echo "Consumer started with PID: $CONSUMER_PID"
ENDSSH

log_info "✓ Consumer started"
echo ""

# Step 5: Wait and check logs
log_info "Step 5: Checking consumer logs..."
sleep 5
ssh -i "$SSH_KEY" "$EC2_USER@$EC2_IP" "tail -n 20 $REMOTE_DIR/consumer.log"
echo ""

# Step 6: Verify consumer is running
log_info "Step 6: Verifying consumer is running..."
CONSUMER_RUNNING=$(ssh -i "$SSH_KEY" "$EC2_USER@$EC2_IP" "ps aux | grep 'chat-consumer-v3.jar' | grep -v grep | wc -l")
if [ "$CONSUMER_RUNNING" -eq "1" ]; then
    log_info "✓ Consumer is running"
else
    log_error "Consumer is not running!"
    exit 1
fi
echo ""

log_info "=== Deployment Complete ==="
echo ""
echo "To view logs:"
echo "  ssh -i $SSH_KEY $EC2_USER@$EC2_IP 'tail -f $REMOTE_DIR/consumer.log'"
echo ""
echo "To stop consumer:"
echo "  ssh -i $SSH_KEY $EC2_USER@$EC2_IP 'pkill -f chat-consumer-v3.jar'"
echo ""
