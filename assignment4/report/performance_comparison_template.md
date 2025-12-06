# Performance Comparison Report

## CS6650 Assignment 4 - System Optimization

---

## 1. Test Configuration

### Baseline Performance Test
| Parameter | Value |
|-----------|-------|
| Concurrent Users | 1000 |
| Total API Calls | 100,000 |
| Duration | 5 minutes |
| Read Operations | 70% |
| Write Operations | 30% |

### Stress Test
| Parameter | Value |
|-----------|-------|
| Concurrent Users | 500 |
| Total API Calls | 200,000 - 500,000 |
| Duration | 30 minutes |

---

## 2. Performance Metrics Comparison

### 2.1 Response Time

| Metric | Baseline (main) | Optimization 1 | Change | Optimization 2 | Change |
|--------|-----------------|----------------|--------|----------------|--------|
| Average | ___ ms | ___ ms | ↓/↑ __% | ___ ms | ↓/↑ __% |
| Median | ___ ms | ___ ms | ↓/↑ __% | ___ ms | ↓/↑ __% |
| p90 | ___ ms | ___ ms | ↓/↑ __% | ___ ms | ↓/↑ __% |
| p95 | ___ ms | ___ ms | ↓/↑ __% | ___ ms | ↓/↑ __% |
| p99 | ___ ms | ___ ms | ↓/↑ __% | ___ ms | ↓/↑ __% |
| Min | ___ ms | ___ ms | - | ___ ms | - |
| Max | ___ ms | ___ ms | - | ___ ms | - |

### 2.2 Throughput

| Metric | Baseline | Optimization 1 | Change | Optimization 2 | Change |
|--------|----------|----------------|--------|----------------|--------|
| Avg Throughput | ___ req/s | ___ req/s | ↑ __% | ___ req/s | ↑ __% |
| Peak Throughput | ___ req/s | ___ req/s | ↑ __% | ___ req/s | ↑ __% |
| Total Requests | ___ | ___ | - | ___ | - |

### 2.3 Error Rate

| Metric | Baseline | Optimization 1 | Change | Optimization 2 | Change |
|--------|----------|----------------|--------|----------------|--------|
| Error Rate | ___ % | ___ % | ↓ __% | ___ % | ↓ __% |
| Failed Requests | ___ | ___ | - | ___ | - |

### 2.4 Resource Utilization

| Resource | Baseline | Optimization 1 | Change | Optimization 2 | Change |
|----------|----------|----------------|--------|----------------|--------|
| Avg CPU | ___ % | ___ % | ↓/↑ __% | ___ % | ↓/↑ __% |
| Peak CPU | ___ % | ___ % | ↓/↑ __% | ___ % | ↓/↑ __% |
| Avg Memory | ___ % | ___ % | ↓/↑ __% | ___ % | ↓/↑ __% |
| Peak Memory | ___ % | ___ % | ↓/↑ __% | ___ % | ↓/↑ __% |
| DB Connections (avg) | ___ | ___ | - | ___ | - |
| DB Connections (max) | ___ | ___ | - | ___ | - |

---

## 3. Stress Test Results

### 3.1 System Stability (30-minute test)

| Metric | Baseline | Optimization 1 | Optimization 2 |
|--------|----------|----------------|----------------|
| Breaking Point (users) | ___ | ___ | ___ |
| Max Sustainable Throughput | ___ req/s | ___ req/s | ___ req/s |
| Error Rate at Peak | ___ % | ___ % | ___ % |
| Memory Leak Detected | Yes/No | Yes/No | Yes/No |

### 3.2 Degradation Pattern

| Time | Baseline Throughput | Opt1 Throughput | Opt2 Throughput |
|------|--------------------:|----------------:|----------------:|
| 0-5 min | ___ req/s | ___ req/s | ___ req/s |
| 5-10 min | ___ req/s | ___ req/s | ___ req/s |
| 10-20 min | ___ req/s | ___ req/s | ___ req/s |
| 20-30 min | ___ req/s | ___ req/s | ___ req/s |

---

## 4. Optimization Impact Summary

### Optimization 1: RabbitMQ Queue Partitioning

| Aspect | Impact |
|--------|--------|
| **Throughput** | ↑ __% improvement |
| **Response Time** | ↓ __% reduction |
| **CPU Efficiency** | ↓/↑ __% |
| **Overall Assessment** | ⭐⭐⭐⭐⭐ / ⭐⭐⭐⭐⭐ |

**Key Benefits**:
1. ___
2. ___
3. ___

**Trade-offs**:
1. ___
2. ___

### Optimization 2: Caffeine Cache Monitoring

| Aspect | Impact |
|--------|--------|
| **Cache Hit Rate** | __% |
| **Visibility** | Improved |
| **Optimization Insights** | ___ |
| **Overall Assessment** | ⭐⭐⭐⭐⭐ / ⭐⭐⭐⭐⭐ |

**Key Benefits**:
1. ___
2. ___
3. ___

**Trade-offs**:
1. ___
2. ___

---

## 5. Improvement Percentage Calculation

### Formula
```
Improvement % = ((New Value - Baseline) / Baseline) × 100

For Response Time (lower is better):
Improvement % = ((Baseline - New Value) / Baseline) × 100

For Throughput (higher is better):
Improvement % = ((New Value - Baseline) / Baseline) × 100
```

### Calculation Examples

**Throughput Improvement (Optimization 1)**:
```
Baseline: ___ req/s
After Opt1: ___ req/s
Improvement = ((___ - ___) / ___) × 100 = ___%
```

**Response Time Improvement (Optimization 1)**:
```
Baseline: ___ ms
After Opt1: ___ ms
Improvement = ((___ - ___) / ___) × 100 = ___%
```

---

## 6. Conclusion

### Overall Performance Improvement

| Optimization | Primary Benefit | Improvement |
|--------------|-----------------|-------------|
| Queue Partitioning | Throughput | +__% |
| Cache Monitoring | Visibility | N/A (observability) |

### Recommendation

Based on the performance analysis:
1. ___
2. ___
3. ___

---

## Appendix: Raw Data

### A. JMeter Aggregate Report - Baseline
```
Label,# Samples,Average,Median,90% Line,95% Line,99% Line,Min,Max,Error %,Throughput
___
```

### B. JMeter Aggregate Report - Optimization 1
```
Label,# Samples,Average,Median,90% Line,95% Line,99% Line,Min,Max,Error %,Throughput
___
```

### C. JMeter Aggregate Report - Optimization 2
```
Label,# Samples,Average,Median,90% Line,95% Line,99% Line,Min,Max,Error %,Throughput
___
```
