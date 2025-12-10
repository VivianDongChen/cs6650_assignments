package com.cs6650.chat.consumer.cache;

import com.cs6650.chat.consumer.metrics.MetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics cache decorator implementing cache-aside pattern.
 * Wraps MetricsService with Redis caching layer.
 * Provides cache statistics and performance metrics.
 */
public class MetricsCacheDecorator {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetricsCacheDecorator.class);

    private final MetricsService delegate;
    private final RedisConnectionPool redis;

    // Cache statistics
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong cacheEvictions = new AtomicLong(0);
    private final AtomicLong queryErrors = new AtomicLong(0);

    // TTL configuration (in seconds)
    private static final int TTL_METRICS_ALL = 600;          // 10 minutes
    private static final int TTL_TOP_USERS = 180;            // 3 minutes
    private static final int TTL_TOP_ROOMS = 180;            // 3 minutes
    private static final int TTL_HOURLY = 3600;              // 1 hour
    private static final int TTL_BY_ROOM = 300;              // 5 minutes

    // Cache key constants
    private static final String CACHE_KEY_ALL = "metrics:all";
    private static final String CACHE_KEY_TOP_USERS = "metrics:top_users:";
    private static final String CACHE_KEY_TOP_ROOMS = "metrics:top_rooms:";
    private static final String CACHE_KEY_HOURLY = "metrics:hourly:24h";
    private static final String CACHE_KEY_BY_ROOM = "metrics:by_room";

    public MetricsCacheDecorator(MetricsService delegate, RedisConnectionPool redis) {
        this.delegate = delegate;
        this.redis = redis;
        LOGGER.info("MetricsCacheDecorator initialized with Redis caching");
    }

    /**
     * Get all metrics with caching.
     * Implements cache-aside pattern:
     * 1. Check Redis
     * 2. On miss, query database
     * 3. Update cache and return
     */
    public String getMetricsJson() {
        long startTime = System.currentTimeMillis();
        String cached = redis.get(CACHE_KEY_ALL);

        if (cached != null) {
            cacheHits.incrementAndGet();
            long duration = System.currentTimeMillis() - startTime;
            LOGGER.debug("Metrics cache hit ({}ms)", duration);
            return cached;
        }

        // Cache miss - query database
        cacheMisses.incrementAndGet();
        try {
            String result = delegate.getMetricsJson();
            redis.setEx(CACHE_KEY_ALL, TTL_METRICS_ALL, result);
            long duration = System.currentTimeMillis() - startTime;
            LOGGER.info("Metrics queried from DB and cached ({}ms)", duration);
            return result;
        } catch (Exception e) {
            queryErrors.incrementAndGet();
            LOGGER.error("Error getting metrics", e);
            throw new RuntimeException("Failed to get metrics", e);
        }
    }

    /**
     * Invalidate all metrics cache.
     */
    public void invalidateAll() {
        try {
            long deleted = redis.deleteByPattern("metrics:*");
            LOGGER.info("Invalidated {} metrics cache entries", deleted);
            cacheEvictions.addAndGet(deleted);
        } catch (Exception e) {
            LOGGER.warn("Error invalidating metrics cache", e);
        }
    }

    /**
     * Invalidate specific cache entries.
     */
    public void invalidateTopUsers() {
        redis.deleteByPattern(CACHE_KEY_TOP_USERS + "*");
    }

    public void invalidateTopRooms() {
        redis.deleteByPattern(CACHE_KEY_TOP_ROOMS + "*");
    }

    public void invalidateHourly() {
        redis.delete(CACHE_KEY_HOURLY);
    }

    public void invalidateByRoom() {
        redis.delete(CACHE_KEY_BY_ROOM);
    }

    /**
     * Force refresh all metrics (bypass cache).
     */
    public String refreshMetrics() {
        invalidateAll();
        return getMetricsJson();
    }

    /**
     * Get cache statistics.
     */
    public CacheStatistics getStatistics() {
        long total = cacheHits.get() + cacheMisses.get();
        double hitRate = total > 0 ? (double) cacheHits.get() / total * 100 : 0;

        return new CacheStatistics(
            cacheHits.get(),
            cacheMisses.get(),
            cacheEvictions.get(),
            queryErrors.get(),
            hitRate,
            total
        );
    }

    /**
     * Log cache performance metrics.
     */
    public void logCachePerformance() {
        CacheStatistics stats = getStatistics();
        LOGGER.info(
            "=== Metrics Cache Statistics === Hits: {}, Misses: {}, " +
            "Evictions: {}, Errors: {}, HitRate: {:.2f}%, Total: {}",
            stats.hits, stats.misses, stats.evictions, stats.errors,
            stats.hitRate, stats.total
        );
    }

    /**
     * Cache statistics data class.
     */
    public static class CacheStatistics {
        public final long hits;
        public final long misses;
        public final long evictions;
        public final long errors;
        public final double hitRate;
        public final long total;

        public CacheStatistics(long hits, long misses, long evictions, long errors, double hitRate, long total) {
            this.hits = hits;
            this.misses = misses;
            this.evictions = evictions;
            this.errors = errors;
            this.hitRate = hitRate;
            this.total = total;
        }
    }
}
