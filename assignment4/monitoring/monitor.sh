#!/bin/bash
# =============================================================================
# CS6650 Assignment 4 - Resource Monitoring Script
# 用于收集 CPU、内存、数据库连接数等指标
# =============================================================================

# 配置
OUTPUT_FILE="${1:-metrics.csv}"
INTERVAL="${2:-5}"  # 采集间隔（秒）
DB_HOST="${DB_HOST:-localhost}"
DB_NAME="${DB_NAME:-chatdb}"
DB_USER="${DB_USER:-postgres}"

echo "============================================"
echo "CS6650 Resource Monitor"
echo "============================================"
echo "Output file: $OUTPUT_FILE"
echo "Interval: ${INTERVAL}s"
echo "Press Ctrl+C to stop"
echo "============================================"

# 写入 CSV 头
echo "timestamp,datetime,cpu_percent,memory_percent,memory_used_mb,load_avg_1m,load_avg_5m,db_connections" > "$OUTPUT_FILE"

# 监控循环
while true; do
    TIMESTAMP=$(date +%s)
    DATETIME=$(date "+%Y-%m-%d %H:%M:%S")

    # CPU 使用率 (macOS 和 Linux 兼容)
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        CPU=$(ps -A -o %cpu | awk '{s+=$1} END {print s}')
        CPU=$(echo "scale=1; $CPU / $(sysctl -n hw.ncpu)" | bc 2>/dev/null || echo "0")
    else
        # Linux
        CPU=$(top -bn1 | grep "Cpu(s)" | awk '{print $2}' | cut -d'%' -f1)
    fi

    # 内存使用率
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        MEM_USED=$(vm_stat | grep "Pages active" | awk '{print $3}' | tr -d '.')
        MEM_TOTAL=$(sysctl -n hw.memsize)
        MEM_PERCENT=$(echo "scale=1; $MEM_USED * 4096 * 100 / $MEM_TOTAL" | bc 2>/dev/null || echo "0")
        MEM_USED_MB=$(echo "scale=0; $MEM_USED * 4096 / 1024 / 1024" | bc 2>/dev/null || echo "0")
    else
        # Linux
        MEM_INFO=$(free -m | awk 'NR==2{printf "%.1f %d", $3/$2*100, $3}')
        MEM_PERCENT=$(echo $MEM_INFO | awk '{print $1}')
        MEM_USED_MB=$(echo $MEM_INFO | awk '{print $2}')
    fi

    # 系统负载
    LOAD_AVG=$(uptime | awk -F'load average:' '{print $2}' | awk -F',' '{print $1, $2}' | tr -d ' ')
    LOAD_1M=$(echo $LOAD_AVG | cut -d' ' -f1 | tr -d ',')
    LOAD_5M=$(echo $LOAD_AVG | cut -d' ' -f2 | tr -d ',' | awk '{print $1}')

    # 数据库连接数 (如果可用)
    DB_CONN="N/A"
    if command -v psql &> /dev/null; then
        DB_CONN=$(PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -U "$DB_USER" -d "$DB_NAME" -t -c "SELECT count(*) FROM pg_stat_activity;" 2>/dev/null | tr -d ' ' || echo "N/A")
    fi

    # 写入 CSV
    echo "$TIMESTAMP,$DATETIME,$CPU,$MEM_PERCENT,$MEM_USED_MB,$LOAD_1M,$LOAD_5M,$DB_CONN" >> "$OUTPUT_FILE"

    # 打印到控制台
    printf "\r[%s] CPU: %s%% | MEM: %s%% (%sMB) | Load: %s | DB Conn: %s" \
        "$DATETIME" "$CPU" "$MEM_PERCENT" "$MEM_USED_MB" "$LOAD_1M" "$DB_CONN"

    sleep "$INTERVAL"
done
