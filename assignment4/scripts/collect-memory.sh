#!/bin/bash
# 收集 EC2 服务器内存使用数据

KEY=/Users/chendong/Desktop/6650/cs6650-hw2-key.pem
OUTPUT_DIR=/Users/chendong/Desktop/6650/cs6650_assignments/assignment4/results/memory
SERVERS="54.190.20.200 34.211.48.9 44.250.64.183 35.91.22.76"
BRANCH=${1:-current}
DURATION=${2:-300}  # 默认5分钟
INTERVAL=5

mkdir -p "$OUTPUT_DIR"
OUTPUT="$OUTPUT_DIR/memory-${BRANCH}.csv"

echo "timestamp,server_ip,total_mb,used_mb,free_mb,available_mb,usage_percent" > "$OUTPUT"

echo "=== 收集 $BRANCH 分支内存数据 ==="
echo "持续时间: ${DURATION}秒, 间隔: ${INTERVAL}秒"
echo "输出文件: $OUTPUT"

SAMPLES=$((DURATION / INTERVAL))
for i in $(seq 1 $SAMPLES); do
    TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')
    for ip in $SERVERS; do
        MEM_INFO=$(ssh -i $KEY -o StrictHostKeyChecking=no -o ConnectTimeout=3 ec2-user@$ip "free -m | grep Mem" 2>/dev/null)
        if [ -n "$MEM_INFO" ]; then
            TOTAL=$(echo $MEM_INFO | awk '{print $2}')
            USED=$(echo $MEM_INFO | awk '{print $3}')
            FREE=$(echo $MEM_INFO | awk '{print $4}')
            AVAILABLE=$(echo $MEM_INFO | awk '{print $7}')
            PERCENT=$(echo "scale=1; $USED * 100 / $TOTAL" | bc)
            echo "$TIMESTAMP,$ip,$TOTAL,$USED,$FREE,$AVAILABLE,$PERCENT" >> "$OUTPUT"
        fi
    done
    echo "采样 $i/$SAMPLES: $TIMESTAMP"
    sleep $INTERVAL
done

echo "=== 内存数据收集完成 ==="
echo "数据保存到: $OUTPUT"
