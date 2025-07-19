//package com.example.sse.test;
//
//import com.example.sse.config.ConfigManager;
//import org.junit.After;
//import org.junit.Assert;
//import org.junit.Before;
//import org.junit.Test;
//
//import java.lang.reflect.Field;
//import java.util.Properties;
//
///**
// * Comprehensive tests for ConfigManager
// * Tests singleton pattern, property loading, type conversions, and error handling
// */
//public class ConfigManagerTest {
//
//    private ConfigManager configManager;
//
//    @Before
//    public void setUp() {
//        configManager = ConfigManager.getInstance();
//    }
//
//    @Test
//    public void testSingletonPattern() {
//        ConfigManager instance1 = ConfigManager.getInstance();
//        ConfigManager instance2 = ConfigManager.getInstance();
//
//        Assert.assertNotNull("ConfigManager instance should not be null", instance1);
//        Assert.assertSame("ConfigManager should be singleton", instance1, instance2);
//    }
//
//    @Test
//    public void testPropertiesLoaded() throws Exception {
//        // Use reflection to access private properties field
//        Field propertiesField = ConfigManager.class.getDeclaredField("properties");
//        propertiesField.setAccessible(true);
//        Properties properties = (Properties) propertiesField.get(configManager);
//
//        Assert.assertNotNull("Properties should be loaded", properties);
//        Assert.assertFalse("Properties should not be empty", properties.isEmpty());
//    }
//
//    @Test
//    public void testDatabaseProperties() {
//        // Test database configuration properties
//        String dbHost = configManager.getProperty("mysql.cms.ip.address");
//        String dbName = configManager.getProperty("mysql.cms.database");
//        String dbUser = configManager.getProperty("mysql.cms.user");
//
//        Assert.assertNotNull("Database host should be configured", dbHost);
//        Assert.assertNotNull("Database name should be configured", dbName);
//        Assert.assertNotNull("Database user should be configured", dbUser);
//
//        System.out.println("Database config - Host: " + dbHost + ", DB: " + dbName + ", User: " + dbUser);
//    }
//
//    @Test
//    public void testRedisProperties() {
//        // Test Redis configuration properties
//        String redisHost = configManager.getProperty("redis.host");
//        String redisPort = configManager.getProperty("redis.port");
//        String redisPassword = configManager.getProperty("redis.password");
//
//        Assert.assertNotNull("Redis host should be configured", redisHost);
//        Assert.assertNotNull("Redis port should be configured", redisPort);
//        Assert.assertNotNull("Redis password should be configured", redisPassword);
//
//        System.out.println("Redis config - Host: " + redisHost + ", Port: " + redisPort);
//    }
//
//    @Test
//    public void testJWTProperties() {
//        // Test JWT configuration properties
//        String jwtSecret = configManager.getProperty("jwt.secret");
//        String jwtExpiration = configManager.getProperty("jwt.expiration.hours");
//
//        Assert.assertNotNull("JWT secret should be configured", jwtSecret);
//        Assert.assertNotNull("JWT expiration should be configured", jwtExpiration);
//        Assert.assertTrue("JWT secret should be long enough", jwtSecret.length() >= 32);
//
//        System.out.println("JWT config - Secret length: " + jwtSecret.length() + ", Expiration: " + jwtExpiration + "h");
//    }
//
//    @Test
//    public void testSSEProperties() {
//        // Test SSE configuration properties
//        String heartbeatInterval = configManager.getProperty("sse.heartbeat.interval");
//        String dataRefreshInterval = configManager.getProperty("sse.data.refresh.interval");
//        String connectionTimeout = configManager.getProperty("sse.connection.timeout");
//
//        Assert.assertNotNull("SSE heartbeat interval should be configured", heartbeatInterval);
//        Assert.assertNotNull("SSE data refresh interval should be configured", dataRefreshInterval);
//        Assert.assertNotNull("SSE connection timeout should be configured", connectionTimeout);
//
//        System.out.println("SSE config - Heartbeat: " + heartbeatInterval + "ms, Refresh: " + dataRefreshInterval + "ms");
//    }
//
//    @Test
//    public void testPropertyWithDefault() {
//        // Test getProperty with default value
//        String existingProperty = configManager.getProperty("jwt.secret", "default");
//        String nonExistentProperty = configManager.getProperty("non.existent.property", "default_value");
//
//        Assert.assertNotNull("Existing property should return actual value", existingProperty);
//        Assert.assertNotEquals("Existing property should not return default", "default", existingProperty);
//        Assert.assertEquals("Non-existent property should return default", "default_value", nonExistentProperty);
//    }
//
//    @Test
//    public void testIntPropertyConversion() {
//        // Test integer property conversion
//        int redisPort = configManager.getIntProperty("redis.port", 6379);
//        int nonExistentInt = configManager.getIntProperty("non.existent.int", 999);
//        int invalidInt = configManager.getIntProperty("jwt.secret", 123); // String value, should return default
//
//        Assert.assertTrue("Redis port should be valid integer", redisPort > 0);
//        Assert.assertEquals("Non-existent int should return default", 999, nonExistentInt);
//        Assert.assertEquals("Invalid int should return default", 123, invalidInt);
//
//        System.out.println("Int conversion test - Redis port: " + redisPort);
//    }
//
//    @Test
//    public void testLongPropertyConversion() {
//        // Test long property conversion
//        long heartbeatInterval = configManager.getLongProperty("sse.heartbeat.interval", 30000L);
//        long nonExistentLong = configManager.getLongProperty("non.existent.long", 999L);
//        long invalidLong = configManager.getLongProperty("jwt.secret", 123L); // String value, should return default
//
//        Assert.assertTrue("Heartbeat interval should be valid long", heartbeatInterval > 0);
//        Assert.assertEquals("Non-existent long should return default", 999L, nonExistentLong);
//        Assert.assertEquals("Invalid long should return default", 123L, invalidLong);
//
//        System.out.println("Long conversion test - Heartbeat interval: " + heartbeatInterval + "ms");
//    }
//
//    @Test
//    public void testCacheProperties() {
//        // Test cache TTL properties
//        String userStatusTTL = configManager.getProperty("cache.user.status.ttl");
//        String regionTTL = configManager.getProperty("cache.region.ttl");
//
//        Assert.assertNotNull("User status TTL should be configured", userStatusTTL);
//        Assert.assertNotNull("Region TTL should be configured", regionTTL);
//
//        // Convert to int to verify they're valid numbers
//        int userTTL = configManager.getIntProperty("cache.user.status.ttl", 3600);
//        int regTTL = configManager.getIntProperty("cache.region.ttl", 86400);
//
//        Assert.assertTrue("User status TTL should be positive", userTTL > 0);
//        Assert.assertTrue("Region TTL should be positive", regTTL > 0);
//
//        System.out.println("Cache TTL - User status: " + userTTL + "s, Region: " + regTTL + "s");
//    }
//
//    @Test
//    public void testThreadPoolProperties() {
//        // Test thread pool configuration
//        int initSize = configManager.getIntProperty("threadpool.init.size", 2);
//
//        Assert.assertTrue("Thread pool init size should be positive", initSize > 0);
//        Assert.assertTrue("Thread pool init size should be reasonable", initSize <= 10);
//
//        System.out.println("Thread pool init size: " + initSize);
//    }
//
//    @Test
//    public void testConnectionPoolProperties() {
//        // Test database connection pool properties
//        int maxPool = configManager.getIntProperty("mysql.cms.pool.max", 2);
//        long timeout = configManager.getLongProperty("mysql.cms.pool.timeout", 10000L);
//
//        Assert.assertTrue("Max pool size should be positive", maxPool > 0);
//        Assert.assertTrue("Connection timeout should be positive", timeout > 0);
//
//        System.out.println("Connection pool - Max: " + maxPool + ", Timeout: " + timeout + "ms");
//    }
//
//    @Test
//    public void testRedisPoolProperties() {
//        // Test Redis pool configuration
//        int maxTotal = configManager.getIntProperty("redis.pool.max.total", 3);
//        int maxIdle = configManager.getIntProperty("redis.pool.max.idle", 1);
//        int minIdle = configManager.getIntProperty("redis.pool.min.idle", 0);
//        long connectionTimeout = configManager.getLongProperty("redis.connection.timeout", 2000L);
//
//        Assert.assertTrue("Redis max total should be positive", maxTotal > 0);
//        Assert.assertTrue("Redis max idle should be non-negative", maxIdle >= 0);
//        Assert.assertTrue("Redis min idle should be non-negative", minIdle >= 0);
//        Assert.assertTrue("Redis connection timeout should be positive", connectionTimeout > 0);
//
//        System.out.println("Redis pool - Max total: " + maxTotal + ", Max idle: " + maxIdle +
//                ", Min idle: " + minIdle + ", Timeout: " + connectionTimeout + "ms");
//    }
//
//    @Test
//    public void testPropertyKeyValidation() {
//        // Test that null/empty keys are handled gracefully
//        String nullKey = configManager.getProperty(null);
//        String emptyKey = configManager.getProperty("");
//
//        Assert.assertNull("Null key should return null", nullKey);
//        Assert.assertNull("Empty key should return null", emptyKey);
//    }
//
//    @Test
//    public void testNumberFormatExceptionHandling() {
//        // Test that invalid number formats return defaults
//        System.setProperty("test.invalid.int", "not_a_number");
//        System.setProperty("test.invalid.long", "not_a_long");
//
//        try {
//            // Create a new config manager to test with system properties
//            int invalidInt = configManager.getIntProperty("test.invalid.int", 42);
//            long invalidLong = configManager.getLongProperty("test.invalid.long", 42L);
//
//            // These should return defaults due to NumberFormatException
//            Assert.assertEquals("Invalid int should return default", 42, invalidInt);
//            Assert.assertEquals("Invalid long should return default", 42L, invalidLong);
//        } finally {
//            System.clearProperty("test.invalid.int");
//            System.clearProperty("test.invalid.long");
//        }
//    }
//
//    @After
//    public void tearDown() {
//        // Clean up any test properties
//        System.out.println("ConfigManager test completed");
//    }
//}
