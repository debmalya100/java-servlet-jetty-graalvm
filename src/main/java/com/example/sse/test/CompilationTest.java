package com.example.sse.test;

// Simple compilation test to verify all imports and syntax

import com.example.sse.cache.CacheProvider;
import com.example.sse.cache.CacheProviderFactory;
import com.example.sse.config.ConfigManager;
import com.example.sse.database.DatabaseProvider;
import com.example.sse.database.DatabaseProviderFactory;
import com.example.sse.service.DataService;
import com.example.sse.service.DataServiceImpl;
import com.example.sse.util.JWTUtil;

import static java.lang.System.err;
import static java.lang.System.out;

public class CompilationTest {
    public static void main(String[] args) {
        try {
            out.println("Testing compilation...");

            // Test singleton instances
            ConfigManager config = ConfigManager.getInstance();
            DatabaseProvider db = DatabaseProviderFactory.getInstance();
            CacheProvider redis = CacheProviderFactory.getInstance();
            DataService dataService = DataServiceImpl.getInstance();
            JWTUtil jwt = JWTUtil.getInstance();

            out.println("All classes compiled successfully!");
            out.println("ConfigManager: " + (config != null ? "OK" : "FAIL"));
            out.println("DatabaseManager: " + (db != null ? "OK" : "FAIL"));
            out.println("RedisManager: " + (redis != null ? "OK" : "FAIL"));
            out.println("DataService: " + (dataService != null ? "OK" : "FAIL"));
            out.println("JWTUtil: " + (jwt != null ? "OK" : "FAIL"));

        } catch (Exception e) {
            err.println("Compilation test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}