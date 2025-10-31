#!/bin/bash

################################################################################
# Create RabbitMQ Exchange and Queues for Assignment 2
# Run this script on your EC2 instance after RabbitMQ is installed
################################################################################

set -e

echo "========================================="
echo "Creating RabbitMQ Exchange and Queues"
echo "========================================="

# Wait for RabbitMQ to be fully ready
echo ""
echo "[1/3] Waiting for RabbitMQ to be fully ready..."
sleep 10

# Download rabbitmqadmin tool
echo ""
echo "[2/3] Setting up rabbitmqadmin tool..."
sudo docker exec rabbitmq sh -c 'wget -q -O /tmp/rabbitmqadmin http://localhost:15672/cli/rabbitmqadmin && chmod +x /tmp/rabbitmqadmin'

# Create Dead Letter Exchange (DLX) and DLQ
echo ""
echo "[3/5] Creating Dead Letter Exchange..."
sudo docker exec rabbitmq /tmp/rabbitmqadmin declare exchange name=chat.dlx type=direct durable=true
echo "Dead Letter Exchange 'chat.dlx' created"

echo ""
echo "[4/5] Creating Dead Letter Queue..."
sudo docker exec rabbitmq /tmp/rabbitmqadmin declare queue name=chat.dlq durable=true
sudo docker exec rabbitmq /tmp/rabbitmqadmin declare binding source=chat.dlx destination=chat.dlq routing_key=dlq
echo "Dead Letter Queue 'chat.dlq' created and bound"

# Create Main Exchange
echo ""
echo "[5/5] Creating main exchange and queues..."
sudo docker exec rabbitmq /tmp/rabbitmqadmin declare exchange name=chat.exchange type=topic durable=true
echo "Exchange 'chat.exchange' created"

# Create 20 queues with DLX configuration and bind them
for i in {1..20}; do
  QUEUE_NAME="room.$i"
  ROUTING_KEY="room.$i"

  # Declare queue with dead-letter-exchange argument
  sudo docker exec rabbitmq /tmp/rabbitmqadmin declare queue name=$QUEUE_NAME durable=true arguments='{"x-dead-letter-exchange":"chat.dlx","x-dead-letter-routing-key":"dlq"}'

  # Bind queue to exchange
  sudo docker exec rabbitmq /tmp/rabbitmqadmin declare binding source=chat.exchange destination=$QUEUE_NAME routing_key=$ROUTING_KEY

  echo "Queue '$QUEUE_NAME' created with DLX and bound with routing key '$ROUTING_KEY'"
done

# Verify setup
echo ""
echo "========================================="
echo "Verification"
echo "========================================="
echo ""
echo "Queues:"
sudo docker exec rabbitmq rabbitmqctl list_queues name messages

echo ""
echo "Exchanges:"
sudo docker exec rabbitmq rabbitmqctl list_exchanges name type

echo ""
echo "========================================="
echo "Setup Complete"
echo "========================================="
echo ""
echo "Created:"
echo "  - 1 Dead Letter Exchange: chat.dlx (direct)"
echo "  - 1 Dead Letter Queue: chat.dlq"
echo "  - 1 Main Exchange: chat.exchange (topic)"
echo "  - 20 Queues: room.1 to room.20 (with DLX configured)"
echo "  - 20 Bindings with routing keys"
echo ""
PUBLIC_IP=$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4)
echo "Verify in management console:"
echo "  http://$PUBLIC_IP:15672"
echo ""
