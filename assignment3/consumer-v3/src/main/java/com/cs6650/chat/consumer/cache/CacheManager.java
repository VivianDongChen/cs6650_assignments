package com.cs6650.chat.consumer.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Cache manager for coordinating Redis caching layer.
 * Manages initialization, lifecycle, and periodic maintenance tasks.
 * Integrates Level 1 (Redis), Level 2 (Caffeine), and Level 3 (invalidation strategies).
 */
public class CacheManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(CacheManager.class);

    private static CacheManager instance;
    private final RedisConnectionPool redisPool;
    private final MetricsCacheDecorator metricsCache;
    private final HotDataCache hotDataCache;
    private CacheInvalidationStrategy invalidationStrategy;
    private ScheduledExecutorService cacheMaintenanceExecutor;

    private static final int CACHE_REFRESH_INTERVAL_MINUTES = 5;
    private static final int STATS_LOG_INTERVAL_SECONDS = 30;

    private CacheManager(RedisConnectionPool redisPool, MetricsCacheDecorator metricsCache, 
                        HotDataCache hotDataCache) {
        this.redisPool = redisPool;
        this.metricsCache = metricsCache;
        this.hotDataCache = hotDataCache;
        this.invalidationStrategy = new CacheInvalidationStrategy(hotDataCache, metricsCache, redisPool);
        LOGGER.info("CacheManager initialized with 3-layer cache strategy");
    }

    /**
     * Initialize cache manager (singleton pattern).
     */
    public static synchronized CacheManager initialize(
            RedisConnectionPool redisPool,
            MetricsCacheDecorator metricsCache,
            HotDataCache hotDataCache) {
        if (instance == null) {
            instance = new CacheManager(redisPool, metricsCache, hotDataCache);
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
     * Start cache maintenance tasks (periodic refresh, invalidation, and monitoring).
     * Integrates all 3 cache layers with their respective maintenance strategies.
     */
    public void startMaintenanceTasks() {
        cacheMaintenanceExecutor = Executors.newScheduledThreadPool(3, r -> {
            Thread t = new Thread(r, "CacheMaintenanceThread");
            t.setDaemon(true);
            return t;
        });

        // Start invalidation strategy (Level 3: smart cache invalidation)
        if (invalidationStrategy != null) {
            invalidationStrategy.start();
        }

        // Schedule cache statistics logging (all layers)
        cacheMaintenanceExecutor.scheduleAtFixedRate(
            () -> {
                try {
                    // Log L2 (Redis) statistics
                    metricsCache.logCachePerformance();
                    // Log L1 (Caffeine) statistics
                    if (hotDataCache != null) {
                        hotDataCache.logCachePerformance();
                    }
                    // Log L3 (Invalidation) statistics
                    if (invalidationStrategy != null) {
                        invalidationStrategy.logInvalidationStatistics();
                    }
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

        LOGGER.info("Cache maintenance tasks started (all 3 layers)");
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
     * Get hot data cache (Level 1: Caffeine).
     */
    public HotDataCache getHotDataCache() {
        return hotDataCache;
    }

    /**
     * Get invalidation strategy (Level 3: smart invalidation).
     */
    public CacheInvalidationStrategy getInvalidationStrategy() {
        return invalidationStrategy;
    }

    /**
     * Invalidate all caches (emergency).
     */
    public void invalidateAll() {
        metricsCache.invalidateAll();
        if (hotDataCache != null) {
            hotDataCache.invalidateAll();
        }
        LOGGER.info("All caches invalidated (emergency operation)");
    }

    /**
     * Get cache statistics.
     */
    public MetricsCacheDecorator.CacheStatistics getCacheStatistics() {
        return metricsCache.getStatistics();
    }

    /**
     * Event-driven cache invalidation (called when message arrives).
     */
    public void onMessageArrival(String userId, String roomId, long timestamp) {
        if (invalidationStrategy != null) {
            invalidationStrategy.onMessageArrival(userId, roomId, timestamp);
        }
    }

    /**
     * Gracefully shutdown cache manager.
     */
    public void shutdown() {
        try {
            // Shutdown invalidation strategy
            if (invalidationStrategy != null) {
                invalidationStrategy.shutdown();
            }

            // Shutdown maintenance executor
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
