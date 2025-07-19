package com.example.sse.cache;

import com.example.sse.config.ConfigManager;
import com.example.sse.exception.ServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class RedisManager implements CacheProvider {
    private static final Logger logger = LoggerFactory.getLogger(RedisManager.class);
    private static RedisManager instance;
    private JedisPool jedisPool;
    private final ObjectMapper objectMapper;
    private String cachePrefix;

    private RedisManager() {
        initializeRedisPool();
        objectMapper = new ObjectMapper();
    }

    public static synchronized RedisManager getInstance() {
        if (instance == null) {
            instance = new RedisManager();
        }
        return instance;
    }

    private void initializeRedisPool() {
        ConfigManager config = ConfigManager.getInstance();

        // Check if running in Cloud Run environment
        boolean isCloudRun = System.getenv("REDIS_HOST") != null;

        String host;
        int port;
        String password;
        int database;
        int timeout;

        if (isCloudRun) {
            // Cloud Run environment - use environment variables
            host = System.getenv("REDIS_HOST");
            port = Integer.parseInt(System.getenv("REDIS_PORT"));
            password = System.getenv("REDIS_PASSWORD");
            database = Integer.parseInt(System.getenv().getOrDefault("REDIS_DATABASE", "0"));
            timeout = Integer.parseInt(System.getenv().getOrDefault("REDIS_TIMEOUT", "5000"));
            cachePrefix = System.getenv().getOrDefault("REDIS_CACHE_PREFIX", "prod__");

            logger.info("Using Cloud Run Redis configuration for: {}:{}", host, port);
        } else {
            // Local/development environment - use properties file
            host = config.getProperty("redis.host", "localhost");
            port = config.getIntProperty("redis.port", 6379);
            password = config.getProperty("redis.password", "");
            database = config.getIntProperty("redis.database", 0);
            timeout = config.getIntProperty("redis.connection.timeout", 5000);
            cachePrefix = config.getProperty("redis.cache.prefix", "dev__");

            logger.info("Using local Redis configuration for: {}:{}", host, port);
        }

        // Validate required parameters
        if (host == null || host.trim().isEmpty()) {
            throw new ServiceException(ServiceException.ServiceType.CACHE, "Redis host is required");
        }

        JedisPoolConfig poolConfig = new JedisPoolConfig();

        if (isCloudRun) {
            // Cloud Run optimized settings - minimal pools for cold starts
            poolConfig.setMaxTotal(3);
            poolConfig.setMaxIdle(1);
            poolConfig.setMinIdle(0);
        } else {
            // Local/development settings
            poolConfig.setMaxTotal(config.getIntProperty("redis.pool.max.total", 10));
            poolConfig.setMaxIdle(config.getIntProperty("redis.pool.max.idle", 5));
            poolConfig.setMinIdle(config.getIntProperty("redis.pool.min.idle", 1));
        }

        // Common pool settings
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setNumTestsPerEvictionRun(3);
        poolConfig.setTimeBetweenEvictionRunsMillis(30000);
        poolConfig.setMinEvictableIdleTimeMillis(60000);
        poolConfig.setBlockWhenExhausted(true);
        poolConfig.setMaxWaitMillis(timeout);

        try {
            if (password == null || password.trim().isEmpty()) {
                jedisPool = new JedisPool(poolConfig, host, port, timeout);
            } else {
                jedisPool = new JedisPool(poolConfig, host, port, timeout, password.trim(), database);
            }

            // Test connection
            try (Jedis jedis = jedisPool.getResource()) {
                String pong = jedis.ping();
                logger.info("Redis connection test successful: {}", pong);
            }

            logger.info("Redis pool initialized successfully with host: {}, port: {}, database: {}", host, port,
                    database);
        } catch (Exception e) {
            logger.error("Failed to initialize Redis pool", e);
            throw new ServiceException(ServiceException.ServiceType.CACHE, "Redis initialization failed", e);
        }
    }

    private String prefixKey(String key) {
        return cachePrefix + key;
    }

    @Override
    public String get(String key) {
        String prefixedKey = prefixKey(key);
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.get(prefixedKey);
        } catch (Exception e) {
            logger.error("Error getting value from Redis for key: {}", prefixedKey, e);
            return null;
        }
    }

    @Override
    public void set(String key, String value) {
        String prefixedKey = prefixKey(key);
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set(prefixedKey, value);
        } catch (Exception e) {
            logger.error("Error setting value in Redis for key: {}", prefixedKey, e);
        }
    }

    @Override
    public void set(String key, String value, long ttlSeconds) {
        String prefixedKey = prefixKey(key);
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.setex(prefixedKey, (int) ttlSeconds, value);
        } catch (Exception e) {
            logger.error("Error setting value with TTL in Redis for key: {}", prefixedKey, e);
        }
    }

    @Override
    public <T> T getObject(String key, Class<T> clazz) {
        try {
            String json = get(key);
            if (json != null) {
                return objectMapper.readValue(json, clazz);
            }
        } catch (Exception e) {
            logger.error("Error deserializing object from Redis for key: {}", prefixKey(key), e);
        }
        return null;
    }

    @Override
    public void setObject(String key, Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            set(key, json);
        } catch (Exception e) {
            logger.error("Error serializing object to Redis for key: {}", prefixKey(key), e);
        }
    }

    @Override
    public void setObject(String key, Object value, long ttlSeconds) {
        try {
            String json = objectMapper.writeValueAsString(value);
            set(key, json, ttlSeconds);
        } catch (Exception e) {
            logger.error("Error serializing object with TTL to Redis for key: {}", prefixKey(key), e);
        }
    }

    @Override
    public boolean exists(String key) {
        String prefixedKey = prefixKey(key);
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.exists(prefixedKey);
        } catch (Exception e) {
            logger.error("Error checking existence in Redis for key: {}", prefixedKey, e);
            return false;
        }
    }

    @Override
    public void delete(String key) {
        String prefixedKey = prefixKey(key);
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(prefixedKey);
        } catch (Exception e) {
            logger.error("Error deleting key from Redis: {}", prefixedKey, e);
        }
    }

    @Override
    public boolean isHealthy() {
        try (Jedis jedis = jedisPool.getResource()) {
            String response = jedis.ping();
            return "PONG".equals(response);
        } catch (Exception e) {
            logger.error("Redis health check failed", e);
            return false;
        }
    }

    @Override
    public void close() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
            logger.info("Redis pool closed");
        }
    }

    // Additional utility methods
    @Override
    public int getActiveConnections() {
        return jedisPool != null ? jedisPool.getNumActive() : 0;
    }

    @Override
    public int getIdleConnections() {
        return jedisPool != null ? jedisPool.getNumIdle() : 0;
    }

    @Override
    public void flushByPattern(String pattern) {
        String prefixedPattern = prefixKey(pattern);
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.eval(
                    "local keys = redis.call('keys', ARGV[1]) " +
                            "if #keys > 0 then " +
                            "  return redis.call('del', unpack(keys)) " +
                            "else " +
                            "  return 0 " +
                            "end",
                    0,
                    prefixedPattern);
        } catch (Exception e) {
            logger.error("Error flushing keys by pattern: {}", prefixedPattern, e);
        }
    }
}