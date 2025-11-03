#!/bin/bash
# Setup RabbitMQ on EC2: 18.246.237.223

set -e

RABBITMQ_IP="18.246.237.223"
KEY_PATH="/Users/chendong/Desktop/6650/cs6650-hw2-key.pem"

echo "=========================================="
echo "Setting up RabbitMQ"
echo "IP: $RABBITMQ_IP"
echo "=========================================="

ssh -i "$KEY_PATH" -o StrictHostKeyChecking=no "ec2-user@${RABBITMQ_IP}" << 'EOFSCRIPT'
set -e

echo "[1/6] Checking if Docker is running..."
if ! sudo systemctl is-active --quiet docker; then
    echo "Starting Docker..."
    sudo systemctl start docker
    sudo systemctl enable docker
    sleep 3
fi

echo "[2/6] Stopping old RabbitMQ container if exists..."
sudo docker stop rabbitmq 2>/dev/null || true
sudo docker rm rabbitmq 2>/dev/null || true

echo "[3/6] Starting RabbitMQ container..."
sudo docker run -d \
  --name rabbitmq \
  --hostname rabbitmq-server \
  -p 5672:5672 \
  -p 15672:15672 \
  -e RABBITMQ_DEFAULT_USER=guest \
  -e RABBITMQ_DEFAULT_PASS=guest \
  rabbitmq:3-management

echo "[4/6] Waiting for RabbitMQ to start (30 seconds)..."
sleep 30

echo "[5/6] Installing rabbitmqadmin..."
sudo docker exec rabbitmq bash -c "curl -o /tmp/rabbitmqadmin http://localhost:15672/cli/rabbitmqadmin && chmod +x /tmp/rabbitmqadmin"

sleep 5

echo "[6/6] Creating exchanges, queues, and bindings..."

# Create main exchange
sudo docker exec rabbitmq /tmp/rabbitmqadmin declare exchange name=chat.exchange type=topic durable=true

# Create Dead Letter Exchange and Queue
sudo docker exec rabbitmq /tmp/rabbitmqadmin declare exchange name=chat.dlx type=direct durable=true
sudo docker exec rabbitmq /tmp/rabbitmqadmin declare queue name=chat.dlq durable=true
sudo docker exec rabbitmq /tmp/rabbitmqadmin declare binding source=chat.dlx destination=chat.dlq routing_key=dlq

# Create 20 room queues with DLX configuration
for i in {1..20}; do
    QUEUE_NAME="room.$i"
    ROUTING_KEY="room.$i"

    echo "Creating queue: $QUEUE_NAME"

    # Create queue with dead letter exchange
    sudo docker exec rabbitmq /tmp/rabbitmqadmin declare queue \
        name="$QUEUE_NAME" \
        durable=true \
        arguments='{"x-dead-letter-exchange":"chat.dlx","x-dead-letter-routing-key":"dlq"}'

    # Bind queue to exchange
    sudo docker exec rabbitmq /tmp/rabbitmqadmin declare binding \
        source=chat.exchange \
        destination="$QUEUE_NAME" \
        routing_key="$ROUTING_KEY"
done

echo ""
echo "============================================"
echo "RabbitMQ Setup Complete!"
echo "============================================"
echo ""
echo "Management Console: http://18.246.237.223:15672"
echo "Username: guest"
echo "Password: guest"
echo ""
echo "Queues created:"
sudo docker exec rabbitmq rabbitmqctl list_queues name messages
echo ""

EOFSCRIPT

echo ""
echo "✓ RabbitMQ setup completed successfully!"
echo ""
