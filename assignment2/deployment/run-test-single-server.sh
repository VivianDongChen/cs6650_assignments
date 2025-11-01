#!/bin/bash
# Test 1: Single Server Performance Test

set -e

CLIENT_JAR="/Users/chendong/Desktop/6650/cs6650_assignments/assignment1/client-part2/target/client-part2-1.0-SNAPSHOT-jar-with-dependencies.jar"
SERVER_URL="ws://34.212.226.25:8080/chat-server/chat"
OUTPUT_DIR="/Users/chendong/Desktop/6650/test-results/test1-single-server"

echo "=========================================="
echo "Test 1: Single Server Performance"
echo "=========================================="
echo "Server: $SERVER_URL"
echo "Messages: 500,000"
echo "Threads: 288"
echo ""

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Run the test
java -jar "$CLIENT_JAR" \
  --server-uri="${SERVER_URL}/1" \
  --total-messages=500000 \
  --main-threads=288 \
  --warmup-threads=32 \
  --warmup-messages-per-thread=1000 \
  --metrics-output="$OUTPUT_DIR/metrics.csv" \
  --throughput-output="$OUTPUT_DIR/throughput.csv" \
  | tee "$OUTPUT_DIR/client-output.txt"

echo ""
echo "=========================================="
echo "Test Complete!"
echo "=========================================="
echo "Results saved to: $OUTPUT_DIR"
echo ""
echo "Next steps:"
echo "1. Take screenshot of this terminal output"
echo "2. Open RabbitMQ Management: http://54.190.49.133:15672"
echo "3. Take screenshot of Queues tab"
echo "4. Check Consumer health: curl http://34.214.37.149:8080/health | jq"
echo ""
