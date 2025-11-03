#!/bin/bash

KEY_FILE="/Users/chendong/Desktop/6650/cs6650-hw2-key.pem"
SERVERS=(
  "18.237.196.134"
  "54.186.55.54"
  "44.251.147.184"
  "34.213.93.17"
)

for i in "${!SERVERS[@]}"; do
  SERVER="${SERVERS[$i]}"
  echo "=========================================="
  echo "Restarting Tomcat on Server $((i+1)) ($SERVER)..."
  echo "=========================================="
  
  ssh -i "$KEY_FILE" -o StrictHostKeyChecking=no ec2-user@$SERVER << 'ENDSSH'
    # Kill existing Tomcat process
    sudo pkill -f catalina
    sleep 3
    
    # Remove old work directory to force redeployment
    sudo rm -rf /opt/tomcat/work/Catalina
    
    # Start Tomcat
    cd /opt/tomcat/bin
    sudo ./startup.sh
    
    echo "Tomcat restarted"
ENDSSH
  
  echo ""
done

echo ""
echo "==========================================
"
echo "Waiting 10 seconds for Tomcat startup..."
echo "=========================================="
sleep 10

echo ""
echo "Testing all servers..."
for i in "${!SERVERS[@]}"; do
  SERVER="${SERVERS[$i]}"
  HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://$SERVER:8080/chat-server/ --connect-timeout 5 --max-time 10)
  echo "Server $((i+1)) ($SERVER): HTTP $HTTP_CODE"
done
