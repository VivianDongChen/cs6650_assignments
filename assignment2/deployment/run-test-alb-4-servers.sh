#!/bin/bash
# Test 3: ALB with 4 Servers

set -e

CLIENT_JAR="/Users/chendong/Desktop/6650/cs6650_assignments/assignment1/client-part2/target/client-part2-1.0-SNAPSHOT-jar-with-dependencies.jar"
ALB_URL="ws://<YOUR-ALB-DNS-HERE>:80/chat-server/chat"
OUTPUT_DIR="/Users/chendong/Desktop/6650/test-results/test3-alb-4-servers"

echo "=========================================="
echo "Test 3: ALB with 4 Servers"
echo "=========================================="
echo "ALB: $ALB_URL"
echo "Messages: 500,000"
echo "Threads: 288"
echo ""
echo "NOTE: Replace <YOUR-ALB-DNS-HERE> with actual ALB DNS before running!"
echo ""

if [[ "$ALB_URL" == *"YOUR-ALB-DNS-HERE"* ]]; then
    echo "ERROR: Please edit this script and replace <YOUR-ALB-DNS-HERE> with your actual ALB DNS"
    echo "Example: alb-cs6650-123456789.us-west-2.elb.amazonaws.com"
    exit 1
fi

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Run the test
java -jar "$CLIENT_JAR" \
  --server-uri="${ALB_URL}/1" \
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
