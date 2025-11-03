#!/bin/bash
# Test 1: Single Server - 500K messages
# Using Server 1 only

set -e

echo "========================================="
echo "Test 1: Single Server Performance Test"
echo "========================================="
echo "Configuration:"
echo "  - Threads: 256"
echo "  - Total Messages: 500,000"
echo "  - Server: 18.237.196.134:8080"
echo "========================================="
echo ""

CLIENT_JAR="/Users/chendong/Desktop/6650/cs6650_assignments/assignment1/client-part2/target/client-part2-1.0-SNAPSHOT-jar-with-dependencies.jar"
SERVER_URI="ws://18.237.196.134:8080/chat-server/chat/1"
OUTPUT_DIR="/Users/chendong/Desktop/6650/cs6650_assignments/assignment2/results"

mkdir -p "$OUTPUT_DIR"

echo "Starting test at $(date)"
echo ""

cd "$OUTPUT_DIR"
java -jar "$CLIENT_JAR" \
  --server-uri="$SERVER_URI" \
  --total-messages=500000 \
  --main-threads=256

echo ""
echo "Test completed at $(date)"
echo "Results saved to: $OUTPUT_DIR/test1-single-server.csv"
