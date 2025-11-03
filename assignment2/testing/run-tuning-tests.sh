#!/bin/bash
# Part 4: System Tuning - Test different thread counts

set -e

CLIENT_JAR="/Users/chendong/Desktop/6650/cs6650_assignments/assignment1/client-part2/target/client-part2-1.0-SNAPSHOT-jar-with-dependencies.jar"
SERVER_URI="ws://44.252.86.205:8080/chat-server/chat/1"
OUTPUT_DIR="/Users/chendong/Desktop/6650/cs6650_assignments/assignment2/results/tuning"
TOTAL_MESSAGES=100000  # 100K messages per test for faster tuning

mkdir -p "$OUTPUT_DIR"

echo "========================================"
echo "Part 4: System Tuning Tests"
echo "Testing thread counts: 64, 128, 256, 512"
echo "Total messages per test: $TOTAL_MESSAGES"
echo "========================================"
echo ""

THREAD_COUNTS=(64 128 256 512)

for THREADS in "${THREAD_COUNTS[@]}"; do
    echo "========================================"
    echo "Testing with $THREADS threads"
    echo "Started at: $(date)"
    echo "========================================"
    
    cd "$OUTPUT_DIR"
    java -jar "$CLIENT_JAR" \
      --server-uri="$SERVER_URI" \
      --total-messages=$TOTAL_MESSAGES \
      --main-threads=$THREADS \
      > "tuning-${THREADS}threads.log" 2>&1
    
    # Extract key metrics
    THROUGHPUT=$(grep "Throughput (msg/s)" "tuning-${THREADS}threads.log" | awk '{print $4}')
    RUNTIME=$(grep "Runtime (ms)" "tuning-${THREADS}threads.log" | awk '{print $4}')
    
    echo "Results: Threads=$THREADS, Throughput=$THROUGHPUT msg/s, Runtime=$RUNTIME ms"
    echo ""
done

echo "========================================"
echo "Tuning Tests Complete!"
echo "========================================"
echo ""
echo "Summary of Results:"
echo "Threads | Throughput (msg/s) | Runtime (ms)"
echo "--------|-------------------|-------------"

for THREADS in "${THREAD_COUNTS[@]}"; do
    THROUGHPUT=$(grep "Throughput (msg/s)" "$OUTPUT_DIR/tuning-${THREADS}threads.log" | awk '{print $4}')
    RUNTIME=$(grep "Runtime (ms)" "$OUTPUT_DIR/tuning-${THREADS}threads.log" | awk '{print $4}')
    printf "%7s | %17s | %11s\n" "$THREADS" "$THROUGHPUT" "$RUNTIME"
done

echo ""
echo "Detailed logs saved in: $OUTPUT_DIR/"
