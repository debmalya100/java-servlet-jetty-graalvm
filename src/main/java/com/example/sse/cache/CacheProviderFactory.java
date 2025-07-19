package com.example.sse.cache;

public class CacheProviderFactory {
    private static CacheProvider instance;

    public static synchronized CacheProvider getInstance() {
        if (instance == null) {
            instance = RedisManager.getInstance();
        }
        return instance;
    }

    public static synchronized void setInstance(CacheProvider customInstance) {
        instance = customInstance;
    }
}