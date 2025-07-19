package com.example.sse.cache;

public interface CacheProvider {

    String get(String key);

    <T> T getObject(String key, Class<T> clazz);

    void setObject(String key, Object value, long ttlSeconds);

    void set(String key, String value);

    void set(String key, String value, long ttlSeconds);

    void setObject(String key, Object value);

    boolean exists(String key);

    void delete(String key);

    boolean isHealthy();

    void flushByPattern(String pattern);

    void close();

    int getActiveConnections();

    int getIdleConnections();
}
