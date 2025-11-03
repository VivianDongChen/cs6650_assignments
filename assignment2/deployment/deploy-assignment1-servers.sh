#!/bin/bash
# Deploy Assignment 1 WAR (simple echo server) to all 4 servers

set -e

KEY_PATH="/Users/chendong/Desktop/6650/cs6650-hw2-key.pem"
WAR_PATH="/Users/chendong/Desktop/6650/cs6650_assignments/assignment1/server/target/chat-server.war"

SERVERS=(
  "18.237.196.134"
  "54.186.55.54"
  "44.251.147.184"
  "34.213.93.17"
)

echo "==========================================="
echo "Deploying Assignment 1 WAR to all servers"
echo "==========================================="
echo ""

for i in "${!SERVERS[@]}"; do
    SERVER_IP="${SERVERS[$i]}"
    SERVER_NUM=$((i + 1))
    
    echo "=== Server $SERVER_NUM ($SERVER_IP) ==="
    
    # Upload WAR file
    echo "  [1/5] Uploading WAR file..."
    scp -i "$KEY_PATH" -o StrictHostKeyChecking=no "$WAR_PATH" ec2-user@$SERVER_IP:/tmp/chat-server.war
    
    # Deploy WAR
    echo "  [2/5] Deploying WAR..."
    ssh -i "$KEY_PATH" -o StrictHostKeyChecking=no ec2-user@$SERVER_IP 'sudo /opt/tomcat/bin/shutdown.sh 2>/dev/null || echo "Tomcat not running"'
    sleep 3
    ssh -i "$KEY_PATH" -o StrictHostKeyChecking=no ec2-user@$SERVER_IP 'sudo rm -rf /opt/tomcat/webapps/chat-server*'
    ssh -i "$KEY_PATH" -o StrictHostKeyChecking=no ec2-user@$SERVER_IP 'sudo cp /tmp/chat-server.war /opt/tomcat/webapps/'
    
    echo "  [3/5] Starting Tomcat..."
    ssh -i "$KEY_PATH" -o StrictHostKeyChecking=no ec2-user@$SERVER_IP 'sudo /opt/tomcat/bin/startup.sh'
    
    echo "  [4/5] Waiting for Tomcat to start (20 seconds)..."
    sleep 20
    
    echo "  [5/5] Testing endpoint..."
    RESPONSE=$(ssh -i "$KEY_PATH" -o StrictHostKeyChecking=no ec2-user@$SERVER_IP 'curl -s http://localhost:8080/chat-server/')
    if echo "$RESPONSE" | grep -q "Hello World"; then
        echo "  ✓ Server $SERVER_NUM deployed successfully!"
    else
        echo "  ✗ Server $SERVER_NUM deployment may have issues"
        echo "  Response: $RESPONSE"
    fi
    
    echo ""
done

echo "==========================================="
echo "All servers deployed!"
echo "==========================================="
