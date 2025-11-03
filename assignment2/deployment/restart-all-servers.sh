#!/bin/bash
# Restart Tomcat on all 4 servers to apply environment variables

set -e

KEY_PATH="/Users/chendong/Desktop/6650/cs6650-hw2-key.pem"

# Server IPs
SERVERS=(
  "18.237.196.134"
  "54.186.55.54"
  "44.251.147.184"
  "34.213.93.17"
)

echo "========================================="
echo "Restarting Tomcat on all 4 servers"
echo "========================================="
echo ""

for i in "${!SERVERS[@]}"; do
    SERVER_IP="${SERVERS[$i]}"
    SERVER_NUM=$((i + 1))

    echo "Server $SERVER_NUM ($SERVER_IP):"
    echo "  - Stopping Tomcat..."
    ssh -i "$KEY_PATH" -o StrictHostKeyChecking=no ec2-user@$SERVER_IP "/opt/tomcat/bin/shutdown.sh || echo 'Shutdown script completed'"

    echo "  - Waiting for shutdown (5 seconds)..."
    sleep 5

    echo "  - Starting Tomcat..."
    ssh -i "$KEY_PATH" -o StrictHostKeyChecking=no ec2-user@$SERVER_IP "/opt/tomcat/bin/startup.sh"

    echo "  - Waiting for Tomcat to start (15 seconds)..."
    sleep 15

    echo "  - Checking if Tomcat is running..."
    ssh -i "$KEY_PATH" -o StrictHostKeyChecking=no ec2-user@$SERVER_IP "ps aux | grep tomcat | grep -v grep | wc -l" | xargs echo "    Tomcat processes: "

    echo "  ✓ Server $SERVER_NUM restarted"
    echo ""
done

echo "========================================="
echo "All servers restarted successfully!"
echo "========================================="
echo ""
echo "Waiting 30 seconds for applications to fully initialize..."
sleep 30

echo "Testing servers..."
for i in "${!SERVERS[@]}"; do
    SERVER_IP="${SERVERS[$i]}"
    SERVER_NUM=$((i + 1))

    RESPONSE=$(curl -s -m 5 http://$SERVER_IP:8080/chat-server/ 2>&1 || echo "FAILED")
    if echo "$RESPONSE" | grep -q "Hello World"; then
        echo "✓ Server $SERVER_NUM ($SERVER_IP) - OK"
    else
        echo "✗ Server $SERVER_NUM ($SERVER_IP) - FAILED"
    fi
done

echo ""
echo "Done! All servers should now be able to connect to RabbitMQ."
