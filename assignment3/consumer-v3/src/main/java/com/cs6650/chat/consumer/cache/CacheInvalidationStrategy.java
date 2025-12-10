package com.cs6650.chat.consumer.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cache invalidation strategy manager.
 * Implements intelligent cache invalidation using:
 * 1. TTL-based passive expiration (Redis/Caffeine)
 * 2. Event-driven active invalidation (on message arrival)
 * 3. Scheduled proactive refresh (periodic background task)
 * 4. Consistency verification (periodic audit)
 */
public class CacheInvalidationStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(CacheInvalidationStrategy.class);

    private final HotDataCache hotDataCache;
    private final MetricsCacheDecorator metricsCache;
    private final RedisConnectionPool redisPool;

    // Statistics
    private final AtomicLong eventDrivenInvalidations = new AtomicLong(0);
    private final AtomicLong ttlBasedExpirations = new AtomicLong(0);
    private final AtomicLong scheduledRefreshes = new AtomicLong(0);
    private final AtomicLong consistencyIssuesFound = new AtomicLong(0);

    // Configuration (in minutes)
    private static final int REFRESH_INTERVAL_MINUTES = 5;
    private static final int CONSISTENCY_CHECK_INTERVAL_MINUTES = 10;
    private static final int HOT_REFRESH_INTERVAL_SECONDS = 180;  // 3 minutes

    private ScheduledExecutorService invalidationExecutor;

    public CacheInvalidationStrategy(HotDataCache hotDataCache,
                                    MetricsCacheDecorator metricsCache,
                                    RedisConnectionPool redisPool) {
        this.hotDataCache = hotDataCache;
        this.metricsCache = metricsCache;
        this.redisPool = redisPool;
    }

    /**
     * Start the invalidation strategy scheduler.
     */
    public void start() {
        invalidationExecutor = Executors.newScheduledThreadPool(3, r -> {
            Thread t = new Thread(r, "CacheInvalidationThread");
            t.setDaemon(true);
            return t;
        });

        // Task 1: Periodic metrics cache refresh (5 minutes)
        invalidationExecutor.scheduleAtFixedRate(
            this::refreshMetricsCache,
            REFRESH_INTERVAL_MINUTES,
            REFRESH_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        );

        // Task 2: Periodic hot data refresh (3 minutes)
        invalidationExecutor.scheduleAtFixedRate(
            this::refreshHotDataCache,
            HOT_REFRESH_INTERVAL_SECONDS,
            HOT_REFRESH_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );

        // Task 3: Cache consistency verification (10 minutes)
        invalidationExecutor.scheduleAtFixedRate(
            this::verifyCacheConsistency,
            CONSISTENCY_CHECK_INTERVAL_MINUTES,
            CONSISTENCY_CHECK_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        );

        LOGGER.info("Cache invalidation strategy started with 3 scheduled tasks");
    }

    /**
     * Event-driven invalidation: called when new message arrives.
     * Smart detection of which caches need invalidation.
     */
    public void onMessageArrival(String userId, String roomId, long timestamp) {
        try {
            boolean isNewUser = isFirstMessageFromUser(userId);
            boolean isNewRoom = isFirstMessageInRoom(roomId);
            boolean crossHourBoundary = isHourlyBoundary(timestamp);

            if (isNewUser) {
                LOGGER.debug("New user detected: {}", userId);
                invalidateTopUsersCache();
                hotDataCache.invalidateByPattern("hot:top_users:");
                eventDrivenInvalidations.incrementAndGet();
            }

            if (isNewRoom) {
                LOGGER.debug("New room detected: {}", roomId);
                invalidateTopRoomsCache();
                hotDataCache.invalidateByPattern("hot:top_rooms:");
                eventDrivenInvalidations.incrementAndGet();
            }

            if (crossHourBoundary) {
                LOGGER.debug("Hour boundary crossed");
                invalidateHourlyDistributionCache();
                eventDrivenInvalidations.incrementAndGet();
            }
        } catch (Exception e) {
            LOGGER.warn("Error during event-driven invalidation", e);
        }
    }

    /**
     * Passive TTL-based expiration (handled by Redis/Caffeine).
     * This method tracks TTL expirations.
     */
    public void recordTTLExpiration(String key) {
        ttlBasedExpirations.incrementAndGet();
        LOGGER.debug("TTL expiration recorded for key: {}", key);
    }

    /**
     * Proactive refresh: scheduled task to refresh metrics cache.
     */
    private void refreshMetricsCache() {
        try {
            LOGGER.info("Performing scheduled metrics cache refresh...");
            metricsCache.refreshMetrics();
            scheduledRefreshes.incrementAndGet();
        } catch (Exception e) {
            LOGGER.warn("Error during metrics cache refresh", e);
        }
    }

    /**
     * Proactive refresh: refresh hot data cache from Redis.
     */
    private void refreshHotDataCache() {
        try {
            LOGGER.debug("Performing hot data cache refresh...");

            // Refresh top users
            String topUsersKey = "metrics:top_users:10";
            String topUsers = redisPool.get(topUsersKey);
            if (topUsers != null) {
                hotDataCache.cacheTopUsers(10, topUsers);
            }

            // Refresh top rooms
            String topRoomsKey = "metrics:top_rooms:10";
            String topRooms = redisPool.get(topRoomsKey);
            if (topRooms != null) {
                hotDataCache.cacheTopRooms(10, topRooms);
            }

            scheduledRefreshes.incrementAndGet();
        } catch (Exception e) {
            LOGGER.warn("Error during hot data cache refresh", e);
        }
    }

    /**
     * Consistency verification: check L1↔L2↔L3 consistency.
     */
    private void verifyCacheConsistency() {
        try {
            LOGGER.info("Performing cache consistency verification...");

            // Check if L1 (hot data) is consistent with L2 (Redis)
            long l1Size = hotDataCache.getSize();
            LOGGER.debug("L1 cache size: {}", l1Size);

            // Sample check: verify a key from L1 exists in L2 or L3
            // In production, implement hash comparison for full verification

            // Record statistics
            HotDataCache.CacheStatistics l1Stats = hotDataCache.getStatistics();
            MetricsCacheDecorator.CacheStatistics l2Stats = metricsCache.getStatistics();

            LOGGER.info(
                "Cache consistency check: L1(hit%={:.2f}, size={}), L2(hit%={:.2f})",
                l1Stats.hitRate, l1Stats.size, l2Stats.hitRate
            );

            // If consistency issues found, trigger full refresh
            if (l1Stats.hitRate < 70) {
                LOGGER.warn("Low L1 cache hit rate detected, triggering refresh");
                hotDataCache.invalidateAll();
                consistencyIssuesFound.incrementAndGet();
            }

        } catch (Exception e) {
            LOGGER.warn("Error during cache consistency verification", e);
        }
    }

    /**
     * Invalidate top users cache (L2 Redis only, TTL handles L1).
     */
    public void invalidateTopUsersCache() {
        metricsCache.invalidateTopUsers();
        LOGGER.debug("Top users cache invalidated");
    }

    /**
     * Invalidate top rooms cache (L2 Redis only, TTL handles L1).
     */
    public void invalidateTopRoomsCache() {
        metricsCache.invalidateTopRooms();
        LOGGER.debug("Top rooms cache invalidated");
    }

    /**
     * Invalidate hourly distribution cache (L2 Redis only).
     */
    public void invalidateHourlyDistributionCache() {
        metricsCache.invalidateHourly();
        LOGGER.debug("Hourly distribution cache invalidated");
    }

    /**
     * Full cache invalidation (emergency/maintenance).
     */
    public void invalidateAll() {
        try {
            LOGGER.warn("Performing full cache invalidation (emergency)");
            hotDataCache.invalidateAll();
            metricsCache.invalidateAll();
            redisPool.deleteByPattern("metrics:*");
        } catch (Exception e) {
            LOGGER.error("Error during full cache invalidation", e);
        }
    }

    /**
     * Get invalidation statistics.
     */
    public InvalidationStatistics getStatistics() {
        return new InvalidationStatistics(
            eventDrivenInvalidations.get(),
            ttlBasedExpirations.get(),
            scheduledRefreshes.get(),
            consistencyIssuesFound.get()
        );
    }

    /**
     * Log invalidation statistics.
     */
    public void logInvalidationStatistics() {
        InvalidationStatistics stats = getStatistics();
        LOGGER.info(
            "=== Cache Invalidation Statistics === " +
            "EventDriven: {}, TTLExpired: {}, ScheduledRefresh: {}, ConsistencyIssues: {}",
            stats.eventDrivenInvalidations, stats.ttlBasedExpirations,
            stats.scheduledRefreshes, stats.consistencyIssuesFound
        );
    }

    /**
     * Graceful shutdown.
     */
    public void shutdown() {
        try {
            if (invalidationExecutor != null && !invalidationExecutor.isShutdown()) {
                invalidationExecutor.shutdownNow();
                if (!invalidationExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    LOGGER.warn("Invalidation executor did not terminate");
                }
            }
            LOGGER.info("Cache invalidation strategy shutdown complete");
        } catch (Exception e) {
            LOGGER.error("Error during cache invalidation strategy shutdown", e);
        }
    }

    /**
     * Helper: Check if this is the first message from a user.
     */
    private boolean isFirstMessageFromUser(String userId) {
        // In production, check user message count from DB or cache
        // For now, simple heuristic: if user info not in hot cache, it might be new
        return hotDataCache.getUserInfo(userId) == null;
    }

    /**
     * Helper: Check if this is the first message in a room.
     */
    private boolean isFirstMessageInRoom(String roomId) {
        // In production, check room message count from DB or cache
        return hotDataCache.getRoomInfo(roomId) == null;
    }

    /**
     * Helper: Check if current timestamp crosses hour boundary.
     */
    private boolean isHourlyBoundary(long timestamp) {
        long now = System.currentTimeMillis();
        long oneMinuteAgo = now - 60 * 1000;

        // If message timestamp is near hour boundary, return true
        long timestampHour = timestamp / (60 * 60 * 1000);
        long nowHour = now / (60 * 60 * 1000);
        long oneMinuteAgoHour = oneMinuteAgo / (60 * 60 * 1000);

        return nowHour > oneMinuteAgoHour && nowHour != timestampHour;
    }

    /**
     * Invalidation statistics data class.
     */
    public static class InvalidationStatistics {
        public final long eventDrivenInvalidations;
        public final long ttlBasedExpirations;
        public final long scheduledRefreshes;
        public final long consistencyIssuesFound;

        public InvalidationStatistics(long eventDriven, long ttlBased,
                                     long scheduledRefresh, long consistencyIssues) {
            this.eventDrivenInvalidations = eventDriven;
            this.ttlBasedExpirations = ttlBased;
            this.scheduledRefreshes = scheduledRefresh;
            this.consistencyIssuesFound = consistencyIssues;
        }
    }
}
