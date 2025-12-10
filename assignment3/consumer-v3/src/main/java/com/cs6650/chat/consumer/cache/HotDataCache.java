package com.cs6650.chat.consumer.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * L1 Application-level hot data cache using Caffeine.
 * Caches frequently accessed metrics in memory to reduce Redis network overhead.
 * 
 * Cache Strategy:
 * - Hot data: Top10 users, top10 rooms (accessed frequently)
 * - TTL: 2-3 minutes (balance between freshness and cache efficiency)
 * - Eviction: LRU when size exceeds 1000 entries
 * - Response time: 0.1-0.5ms (in-memory lookup)
 */
public class HotDataCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(HotDataCache.class);

    // Cache keys
    private static final String CACHE_KEY_TOP_USERS = "hot:top_users:";
    private static final String CACHE_KEY_TOP_ROOMS = "hot:top_rooms:";
    private static final String CACHE_KEY_USER_INFO = "hot:user:";
    private static final String CACHE_KEY_ROOM_INFO = "hot:room:";
    private static final String CACHE_KEY_STATS = "hot:stats";

    // In-memory cache (Caffeine)
    private final Cache<String, Object> hotDataCache;

    // Statistics
    private final AtomicLong localHits = new AtomicLong(0);
    private final AtomicLong localMisses = new AtomicLong(0);
    private final AtomicLong localEvictions = new AtomicLong(0);

    public HotDataCache() {
        // Configure Caffeine cache for hot data
        this.hotDataCache = Caffeine.newBuilder()
                .maximumSize(1000)                    // Max 1000 entries
                .expireAfterWrite(3, TimeUnit.MINUTES) // 3 minute TTL
                .recordStats()                         // Enable statistics
                .removalListener((key, value, cause) -> {
                    if (cause != null && cause.wasEvicted()) {
                        localEvictions.incrementAndGet();
                    }
                })
                .build();

        LOGGER.info("HotDataCache initialized with 1000 max entries, 3min TTL");
    }

    /**
     * Get hot data from L1 cache (in-memory).
     * Returns null if not found (caller should check L2 Redis).
     */
    public Object getHotData(String key) {
        Object value = hotDataCache.getIfPresent(key);
        if (value != null) {
            localHits.incrementAndGet();
            return value;
        }
        localMisses.incrementAndGet();
        return null;
    }

    /**
     * Put hot data into L1 cache.
     */
    public void putHotData(String key, Object value) {
        hotDataCache.put(key, value);
    }

    /**
     * Cache top N active users.
     */
    public void cacheTopUsers(int limit, Object data) {
        String key = CACHE_KEY_TOP_USERS + limit;
        putHotData(key, data);
    }

    /**
     * Get cached top N active users.
     */
    public Object getTopUsers(int limit) {
        String key = CACHE_KEY_TOP_USERS + limit;
        return getHotData(key);
    }

    /**
     * Cache top N active rooms.
     */
    public void cacheTopRooms(int limit, Object data) {
        String key = CACHE_KEY_TOP_ROOMS + limit;
        putHotData(key, data);
    }

    /**
     * Get cached top N active rooms.
     */
    public Object getTopRooms(int limit) {
        String key = CACHE_KEY_TOP_ROOMS + limit;
        return getHotData(key);
    }

    /**
     * Cache user info by userId.
     */
    public void cacheUserInfo(String userId, Object data) {
        String key = CACHE_KEY_USER_INFO + userId;
        putHotData(key, data);
    }

    /**
     * Get cached user info.
     */
    public Object getUserInfo(String userId) {
        String key = CACHE_KEY_USER_INFO + userId;
        return getHotData(key);
    }

    /**
     * Cache room info by roomId.
     */
    public void cacheRoomInfo(String roomId, Object data) {
        String key = CACHE_KEY_ROOM_INFO + roomId;
        putHotData(key, data);
    }

    /**
     * Get cached room info.
     */
    public Object getRoomInfo(String roomId) {
        String key = CACHE_KEY_ROOM_INFO + roomId;
        return getHotData(key);
    }

    /**
     * Cache system statistics.
     */
    public void cacheStats(Object data) {
        putHotData(CACHE_KEY_STATS, data);
    }

    /**
     * Get cached system statistics.
     */
    public Object getStats() {
        return getHotData(CACHE_KEY_STATS);
    }

    /**
     * Invalidate specific hot data entry.
     */
    public void invalidate(String key) {
        hotDataCache.invalidate(key);
    }

    /**
     * Invalidate by key pattern (prefix).
     */
    public void invalidateByPattern(String pattern) {
        var keys = hotDataCache.asMap().keySet();
        keys.stream()
            .filter(k -> k.startsWith(pattern))
            .forEach(hotDataCache::invalidate);
    }

    /**
     * Invalidate all hot data.
     */
    public void invalidateAll() {
        long size = hotDataCache.asMap().size();
        hotDataCache.invalidateAll();
        LOGGER.info("Invalidated {} hot data entries", size);
    }

    /**
     * Get cache size.
     */
    public long getSize() {
        return hotDataCache.asMap().size();
    }

    /**
     * Get cache statistics.
     */
    public CacheStatistics getStatistics() {
        long total = localHits.get() + localMisses.get();
        double hitRate = total > 0 ? (double) localHits.get() / total * 100 : 0;

        return new CacheStatistics(
            localHits.get(),
            localMisses.get(),
            localEvictions.get(),
            hitRate,
            total,
            getSize()
        );
    }

    /**
     * Log cache performance.
     */
    public void logCachePerformance() {
        CacheStatistics stats = getStatistics();
        LOGGER.info(
            "=== L1 HotDataCache Statistics === Hits: {}, Misses: {}, " +
            "Evictions: {}, HitRate: {:.2f}%, Size: {}, Total: {}",
            stats.hits, stats.misses, stats.evictions, stats.hitRate,
            stats.size, stats.total
        );
    }

    /**
     * Cache statistics data class.
     */
    public static class CacheStatistics {
        public final long hits;
        public final long misses;
        public final long evictions;
        public final double hitRate;
        public final long total;
        public final long size;

        public CacheStatistics(long hits, long misses, long evictions, 
                              double hitRate, long total, long size) {
            this.hits = hits;
            this.misses = misses;
            this.evictions = evictions;
            this.hitRate = hitRate;
            this.total = total;
            this.size = size;
        }
    }
}
