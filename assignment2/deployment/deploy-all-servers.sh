#!/bin/bash
# Deploy Server-v2 to all 4 EC2 instances

set -e

KEY_PATH="/Users/chendong/Desktop/6650/cs6650-hw2-key.pem"
WAR_PATH="/Users/chendong/Desktop/6650/cs6650_assignments/assignment2/server-v2/target/chat-server.war"
RABBITMQ_HOST="54.190.49.133"

# Server IPs
SERVERS=(
  "34.212.226.25"
  "44.243.190.97"
  "44.251.238.69"
  "54.190.99.98"
)

echo "=========================================="
echo "Deploying Server-v2 to 4 EC2 Instances"
echo "=========================================="

for i in "${!SERVERS[@]}"; do
  SERVER_IP="${SERVERS[$i]}"
  SERVER_NUM=$((i + 1))

  echo ""
  echo "================================================"
  echo "SERVER ${SERVER_NUM}: ${SERVER_IP}"
  echo "================================================"

  echo "[Step 1/3] Uploading WAR file..."
  scp -i "$KEY_PATH" -o StrictHostKeyChecking=no \
    "$WAR_PATH" \
    "ec2-user@${SERVER_IP}:~/"

  echo "[Step 2/3] Creating deployment script..."
  ssh -i "$KEY_PATH" -o StrictHostKeyChecking=no "ec2-user@${SERVER_IP}" << 'EOFSCRIPT'
cat > deploy.sh << 'EOF'
#!/bin/bash
set -e
RABBITMQ_HOST="54.188.26.217"
TOMCAT_VERSION="9.0.82"
TOMCAT_DIR="/opt/tomcat"

echo "[1/8] Updating system..."
sudo yum update -y -q

echo "[2/8] Installing Java 11..."
sudo yum install -y java-11-amazon-corretto

echo "[3/8] Downloading Tomcat..."
cd /tmp
wget -q https://archive.apache.org/dist/tomcat/tomcat-9/v${TOMCAT_VERSION}/bin/apache-tomcat-${TOMCAT_VERSION}.tar.gz

echo "[4/8] Installing Tomcat..."
sudo mkdir -p $TOMCAT_DIR
sudo tar -xzf apache-tomcat-${TOMCAT_VERSION}.tar.gz -C $TOMCAT_DIR --strip-components=1
sudo chown -R ec2-user:ec2-user $TOMCAT_DIR
rm apache-tomcat-${TOMCAT_VERSION}.tar.gz

echo "[5/8] Configuring environment..."
cat > $TOMCAT_DIR/bin/setenv.sh <<ENVEOF
#!/bin/bash
export CATALINA_OPTS="\$CATALINA_OPTS -Xms512m -Xmx1024m"
export RABBITMQ_HOST="54.190.49.133"
export RABBITMQ_PORT="5672"
export RABBITMQ_USERNAME="guest"
export RABBITMQ_PASSWORD="guest"
export CHANNEL_POOL_SIZE="20"
ENVEOF
chmod +x $TOMCAT_DIR/bin/setenv.sh

echo "[6/8] Deploying WAR..."
cp ~/chat-server.war $TOMCAT_DIR/webapps/

echo "[7/8] Starting Tomcat..."
$TOMCAT_DIR/bin/startup.sh

echo "[8/8] Waiting for startup (30 seconds)..."
sleep 30

echo ""
echo "============================================"
echo "Deployment Complete!"
echo "============================================"
echo "Testing endpoint..."
curl -s http://localhost:8080/chat-server/ || echo "Warning: Endpoint not responding yet"
EOF

chmod +x deploy.sh
EOFSCRIPT

  echo "[Step 3/3] Running deployment..."
  ssh -i "$KEY_PATH" -o StrictHostKeyChecking=no "ec2-user@${SERVER_IP}" './deploy.sh'

  echo ""
  echo "✓ SERVER ${SERVER_NUM} DEPLOYMENT COMPLETE"
  echo ""
done

echo ""
echo "=========================================="
echo "ALL 4 SERVERS DEPLOYED SUCCESSFULLY!"
echo "=========================================="
echo ""
echo "Server URLs:"
echo "  1. http://34.212.226.25:8080/chat-server/"
echo "  2. http://44.243.190.97:8080/chat-server/"
echo "  3. http://44.251.238.69:8080/chat-server/"
echo "  4. http://54.190.99.98:8080/chat-server/"
echo ""
echo "WebSocket URLs:"
echo "  1. ws://34.212.226.25:8080/chat-server/chat"
echo "  2. ws://44.243.190.97:8080/chat-server/chat"
echo "  3. ws://44.251.238.69:8080/chat-server/chat"
echo "  4. ws://54.190.99.98:8080/chat-server/chat"
echo ""
