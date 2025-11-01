#!/bin/bash
# Test 2: ALB with 2 Servers - 500K messages
# Using ALB load balancing across 2 servers

set -e

echo "========================================="
echo "Test 2: ALB with 2 Servers Performance Test"
echo "========================================="
echo "Configuration:"
echo "  - Threads: 256"
echo "  - Total Messages: 500,000"
echo "  - ALB: cs6650-alb-631563720.us-west-2.elb.amazonaws.com:8080"
echo "  - Servers: 2 (Server 1 & 2)"
echo "========================================="
echo ""

CLIENT_JAR="/Users/chendong/Desktop/6650/cs6650_assignments/assignment1/client-part2/target/client-part2-1.0-SNAPSHOT-jar-with-dependencies.jar"
SERVER_URI="ws://cs6650-alb-631563720.us-west-2.elb.amazonaws.com:8080/chat-server/chat/1"
OUTPUT_DIR="/Users/chendong/Desktop/6650/cs6650_assignments/assignment2/results"

mkdir -p "$OUTPUT_DIR"

echo "NOTE: Before running this test, ensure you have:"
echo "  1. Deregistered Server 3 & 4 from the Target Group"
echo "  2. Only Server 1 & 2 are in 'Healthy' state"
echo ""
read -p "Press Enter to continue once you've configured the Target Group..."

echo "Starting test at $(date)"
echo ""

cd "$OUTPUT_DIR"
java -jar "$CLIENT_JAR" \
  --server-uri="$SERVER_URI" \
  --total-messages=500000 \
  --main-threads=256

echo ""
echo "Test completed at $(date)"
echo "Results saved to: $OUTPUT_DIR/test2-alb-2servers.csv"
