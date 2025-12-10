package com.cs6650.chat.consumer.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Cache manager for coordinating Redis caching layer.
 * Manages initialization, lifecycle, and periodic maintenance tasks.
 */
public class CacheManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(CacheManager.class);

    private static CacheManager instance;
    private final RedisConnectionPool redisPool;
    private final MetricsCacheDecorator metricsCache;
    private ScheduledExecutorService cacheMaintenanceExecutor;

    private static final int CACHE_REFRESH_INTERVAL_MINUTES = 5;
    private static final int STATS_LOG_INTERVAL_SECONDS = 30;

    private CacheManager(RedisConnectionPool redisPool, MetricsCacheDecorator metricsCache) {
        this.redisPool = redisPool;
        this.metricsCache = metricsCache;
        LOGGER.info("CacheManager initialized");
    }

    /**
     * Initialize cache manager (singleton pattern).
     */
    public static synchronized CacheManager initialize(
            RedisConnectionPool redisPool,
            MetricsCacheDecorator metricsCache) {
        if (instance == null) {
            instance = new CacheManager(redisPool, metricsCache);
        }
        return instance;
    }

    /**
     * Get singleton instance.
     */
    public static CacheManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("CacheManager not initialized");
        }
        return instance;
    }

    /**
     * Start cache maintenance tasks (periodic refresh and monitoring).
     */
    public void startMaintenanceTasks() {
        cacheMaintenanceExecutor = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "CacheMaintenanceThread");
            t.setDaemon(true);
            return t;
        });

        // Schedule cache statistics logging
        cacheMaintenanceExecutor.scheduleAtFixedRate(
            () -> {
                try {
                    metricsCache.logCachePerformance();
                } catch (Exception e) {
                    LOGGER.error("Error logging cache performance", e);
                }
            },
            STATS_LOG_INTERVAL_SECONDS,
            STATS_LOG_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );

        // Schedule periodic metrics cache refresh (optional)
        cacheMaintenanceExecutor.scheduleAtFixedRate(
            () -> {
                try {
                    LOGGER.info("Performing scheduled metrics cache refresh...");
                    metricsCache.refreshMetrics();
                } catch (Exception e) {
                    LOGGER.error("Error refreshing metrics cache", e);
                }
            },
            CACHE_REFRESH_INTERVAL_MINUTES,
            CACHE_REFRESH_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        );

        // Schedule periodic Redis health check
        cacheMaintenanceExecutor.scheduleAtFixedRate(
            () -> {
                try {
                    if (redisPool.ping()) {
                        LOGGER.debug("Redis health check passed");
                    } else {
                        LOGGER.warn("Redis health check failed - Redis unavailable");
                    }
                } catch (Exception e) {
                    LOGGER.error("Error checking Redis health", e);
                }
            },
            30,
            60,
            TimeUnit.SECONDS
        );

        LOGGER.info("Cache maintenance tasks started");
    }

    /**
     * Get metrics cache decorator.
     */
    public MetricsCacheDecorator getMetricsCache() {
        return metricsCache;
    }

    /**
     * Get Redis connection pool.
     */
    public RedisConnectionPool getRedisPool() {
        return redisPool;
    }

    /**
     * Invalidate all caches.
     */
    public void invalidateAll() {
        metricsCache.invalidateAll();
        LOGGER.info("All caches invalidated");
    }

    /**
     * Get cache statistics.
     */
    public MetricsCacheDecorator.CacheStatistics getCacheStatistics() {
        return metricsCache.getStatistics();
    }

    /**
     * Gracefully shutdown cache manager.
     */
    public void shutdown() {
        try {
            if (cacheMaintenanceExecutor != null && !cacheMaintenanceExecutor.isShutdown()) {
                cacheMaintenanceExecutor.shutdownNow();
                if (!cacheMaintenanceExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    LOGGER.warn("Cache maintenance executor did not terminate");
                }
            }
            redisPool.shutdown();
            LOGGER.info("Cache manager shutdown complete");
        } catch (Exception e) {
            LOGGER.error("Error during cache manager shutdown", e);
        }
    }
}
