package com.cs6650.chat.consumer.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Jedis;

/**
 * Singleton Redis connection pool for distributed caching.
 * Manages connection lifecycle and provides thread-safe access to Redis.
 */
public class RedisConnectionPool {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisConnectionPool.class);
    private static RedisConnectionPool instance;
    private final JedisPool jedisPool;
    private final String host;
    private final int port;
    private final int db;

    // Configuration
    private static final int DEFAULT_TIMEOUT = 2000;
    private static final int DEFAULT_DB = 0;

    private RedisConnectionPool(String host, int port, String password, int db) {
        this.host = host;
        this.port = port;
        this.db = db;

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(20);           // Max connections
        poolConfig.setMaxIdle(10);            // Max idle connections
        poolConfig.setMinIdle(5);             // Min idle connections
        poolConfig.setTestOnBorrow(true);     // Test connections before borrowing
        poolConfig.setTestOnReturn(false);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setMinEvictableIdleTimeMillis(30000);  // 30 seconds
        poolConfig.setTimeBetweenEvictionRunsMillis(10000); // 10 seconds

        try {
            if (password != null && !password.isEmpty()) {
                this.jedisPool = new JedisPool(poolConfig, host, port, DEFAULT_TIMEOUT, password, db);
            } else {
                this.jedisPool = new JedisPool(poolConfig, host, port, DEFAULT_TIMEOUT, null, db);
            }
            LOGGER.info("Redis connection pool initialized: host={}, port={}, db={}", host, port, db);
        } catch (Exception e) {
            LOGGER.error("Failed to initialize Redis connection pool", e);
            throw new RuntimeException("Redis connection pool initialization failed", e);
        }
    }

    /**
     * Initialize Redis connection pool (singleton pattern).
     */
    public static synchronized void initialize(String host, int port, String password, int db) {
        if (instance == null) {
            instance = new RedisConnectionPool(host, port, password, db);
        }
    }

    /**
     * Initialize with default DB (0).
     */
    public static synchronized void initialize(String host, int port, String password) {
        initialize(host, port, password, DEFAULT_DB);
    }

    /**
     * Initialize with default parameters from environment variables.
     */
    public static synchronized void initialize() {
        String host = System.getenv().getOrDefault("REDIS_HOST", "localhost");
        int port = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
        String password = System.getenv().getOrDefault("REDIS_PASSWORD", "");
        int db = Integer.parseInt(System.getenv().getOrDefault("REDIS_DB", "0"));
        initialize(host, port, password, db);
    }

    /**
     * Get singleton instance.
     */
    public static RedisConnectionPool getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Redis pool not initialized. Call initialize() first.");
        }
        return instance;
    }

    /**
     * Get a Jedis connection from the pool.
     */
    public Jedis getConnection() {
        try {
            return jedisPool.getResource();
        } catch (Exception e) {
            LOGGER.error("Failed to get connection from Redis pool", e);
            throw new RuntimeException("Redis connection failed", e);
        }
    }

    /**
     * Set a key-value pair with TTL.
     */
    public void setEx(String key, int ttlSeconds, String value) {
        try (Jedis jedis = getConnection()) {
            jedis.setex(key, ttlSeconds, value);
        } catch (Exception e) {
            LOGGER.warn("Failed to set key in Redis: {}", key, e);
        }
    }

    /**
     * Get a value by key.
     */
    public String get(String key) {
        try (Jedis jedis = getConnection()) {
            return jedis.get(key);
        } catch (Exception e) {
            LOGGER.warn("Failed to get key from Redis: {}", key, e);
            return null;
        }
    }

    /**
     * Delete a key.
     */
    public boolean delete(String key) {
        try (Jedis jedis = getConnection()) {
            long result = jedis.del(key);
            return result > 0;
        } catch (Exception e) {
            LOGGER.warn("Failed to delete key from Redis: {}", key, e);
            return false;
        }
    }

    /**
     * Delete multiple keys by pattern.
     */
    public long deleteByPattern(String pattern) {
        try (Jedis jedis = getConnection()) {
            long deleted = 0;
            var keys = jedis.keys(pattern);
            if (!keys.isEmpty()) {
                deleted = jedis.del(keys.toArray(new String[0]));
            }
            return deleted;
        } catch (Exception e) {
            LOGGER.warn("Failed to delete keys by pattern: {}", pattern, e);
            return 0;
        }
    }

    /**
     * Check if key exists.
     */
    public boolean exists(String key) {
        try (Jedis jedis = getConnection()) {
            return jedis.exists(key);
        } catch (Exception e) {
            LOGGER.warn("Failed to check key existence: {}", key, e);
            return false;
        }
    }

    /**
     * Get TTL of a key (in seconds).
     */
    public long getTTL(String key) {
        try (Jedis jedis = getConnection()) {
            return jedis.ttl(key);
        } catch (Exception e) {
            LOGGER.warn("Failed to get TTL: {}", key, e);
            return -1;
        }
    }

    /**
     * Ping Redis server.
     */
    public boolean ping() {
        try (Jedis jedis = getConnection()) {
            String response = jedis.ping();
            return "PONG".equals(response);
        } catch (Exception e) {
            LOGGER.error("Redis ping failed", e);
            return false;
        }
    }

    /**
     * Get connection pool statistics.
     */
    public String getStats() {
        try {
            return String.format(
                "RedisPool(host=%s, port=%d, db=%d, active=%d, idle=%d)",
                host, port, db, jedisPool.getNumActive(), jedisPool.getNumIdle()
            );
        } catch (Exception e) {
            return "Redis stats unavailable";
        }
    }

    /**
     * Gracefully shutdown the pool.
     */
    public void shutdown() {
        try {
            if (jedisPool != null && !jedisPool.isClosed()) {
                jedisPool.close();
                LOGGER.info("Redis connection pool closed");
            }
        } catch (Exception e) {
            LOGGER.error("Error closing Redis connection pool", e);
        }
    }
}
