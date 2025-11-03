#!/bin/bash
# Master script to setup everything in order

set -e

echo "=========================================="
echo "CS6650 Assignment 2 - Complete Setup"
echo "=========================================="
echo ""
echo "This will setup:"
echo "  1. RabbitMQ (18.246.237.223)"
echo "  2. Consumer (34.216.219.207)"
echo "  3. 4 Server instances"
echo ""
echo "Total estimated time: 20-25 minutes"
echo ""
read -p "Press Enter to start..."

echo ""
echo "================================================"
echo "STEP 1/3: Setting up RabbitMQ"
echo "================================================"
./setup-rabbitmq.sh

echo ""
echo "================================================"
echo "STEP 2/3: Setting up Consumer"
echo "================================================"
./setup-consumer.sh

echo ""
echo "================================================"
echo "STEP 3/3: Deploying Servers"
echo "================================================"
./deploy-all-servers.sh

echo ""
echo "=========================================="
echo "ALL SETUP COMPLETE!"
echo "=========================================="
echo ""
echo "Your infrastructure is ready:"
echo ""
echo "RabbitMQ Management: http://18.246.237.223:15672"
echo "Consumer Health:     http://34.216.219.207:8080/health"
echo ""
echo "Server URLs:"
echo "  1. http://18.237.196.134:8080/chat-server/"
echo "  2. http://54.186.55.54:8080/chat-server/"
echo "  3. http://44.251.147.184:8080/chat-server/"
echo "  4. http://34.213.93.17:8080/chat-server/"
echo ""
echo "Next steps:"
echo "  1. Create ALB in AWS Console"
echo "  2. Run performance tests"
echo "  3. Collect screenshots"
echo "  4. Submit to Canvas"
echo ""
