#!/bin/bash
# Test 3: ALB with 4 Servers - 500K messages
# Using ALB load balancing across all 4 servers

set -e

echo "========================================="
echo "Test 3: ALB with 4 Servers Performance Test"
echo "========================================="
echo "Configuration:"
echo "  - Threads: 64"
echo "  - Total Messages: 200,000"
echo "  - ALB: cs6650-alb-631563720.us-west-2.elb.amazonaws.com:8080"
echo "  - Servers: 4 (All servers)"
echo "========================================="
echo ""

CLIENT_JAR="/Users/chendong/Desktop/6650/cs6650_assignments/assignment1/client-part2/target/client-part2-1.0-SNAPSHOT-jar-with-dependencies.jar"
SERVER_URI="ws://cs6650-alb-631563720.us-west-2.elb.amazonaws.com:8080/chat-server/chat/1"
OUTPUT_DIR="/Users/chendong/Desktop/6650/cs6650_assignments/assignment2/results"

mkdir -p "$OUTPUT_DIR"

echo "Starting test at $(date)"
echo ""

cd "$OUTPUT_DIR"
java -jar "$CLIENT_JAR" \
  --server-uri="$SERVER_URI" \
  --total-messages=200000 \
  --main-threads=64 \
  --warmup-threads=0

echo ""
echo "Test completed at $(date)"
echo "Results saved to: $OUTPUT_DIR/test3-alb-4servers.csv"
