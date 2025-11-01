#!/bin/bash
# Part 3.4.1: Test ALB Connection Distribution
# Send small number of messages to verify load distribution

set -e

echo "================================================"
echo "Part 3.4.1: Testing ALB Connection Distribution"
echo "================================================"
echo ""
echo "Configuration:"
echo "  - Threads: 4 (one per server)"
echo "  - Messages per thread: 10"
echo "  - Total Messages: 40"
echo "  - Target: ALB"
echo "================================================"
echo ""

CLIENT_JAR="/Users/chendong/Desktop/6650/cs6650_assignments/assignment1/client-part2/target/client-part2-1.0-SNAPSHOT-jar-with-dependencies.jar"
SERVER_URI="ws://cs6650-alb-631563720.us-west-2.elb.amazonaws.com:8080/chat-server/chat/1"
OUTPUT_DIR="/Users/chendong/Desktop/6650/cs6650_assignments/assignment2/results"

mkdir -p "$OUTPUT_DIR"

echo "Step 1: Clear Tomcat logs on all servers"
echo "----------------------------------------"
for ip in 34.212.226.25 44.243.190.97 44.251.238.69 54.190.99.98; do
    echo "Clearing logs on $ip..."
    ssh -i /Users/chendong/Desktop/6650/cs6650-hw2-key.pem -o StrictHostKeyChecking=no ec2-user@$ip "sudo truncate -s 0 /opt/tomcat/logs/catalina.out" 2>/dev/null || echo "  (Log clear skipped)"
done
echo ""

echo "Step 2: Running client test..."
echo "----------------------------------------"
cd "$OUTPUT_DIR"

java -jar "$CLIENT_JAR" \
  --server-uri="$SERVER_URI" \
  --warmup-threads=4 \
  --warmup-messages-per-thread=10 \
  --main-threads=0 \
  --total-messages=0

echo ""
echo "Step 3: Checking connection distribution across servers"
echo "----------------------------------------"
for ip in 34.212.226.25 44.243.190.97 44.251.238.69 54.190.99.98; do
    echo "Server $ip:"
    ssh -i /Users/chendong/Desktop/6650/cs6650-hw2-key.pem -o StrictHostKeyChecking=no ec2-user@$ip "grep -c 'WebSocket.*opened' /opt/tomcat/logs/catalina.out 2>/dev/null || echo '0'" | xargs echo "  WebSocket connections opened: "
done

echo ""
echo "Test completed!"
echo "Expected: Connections should be distributed across multiple servers"
