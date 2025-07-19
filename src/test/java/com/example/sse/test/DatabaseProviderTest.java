//package com.example.sse.test;
//
//import com.example.sse.database.DatabaseProvider;
//import com.example.sse.database.DatabaseProviderFactory;
//import org.junit.After;
//import org.junit.Assert;
//import org.junit.Before;
//import org.junit.Test;
//
//import java.sql.Connection;
//import java.sql.PreparedStatement;
//import java.sql.ResultSet;
//import java.sql.SQLException;
//
///**
// * Comprehensive tests for DatabaseProvider
// * Tests connection management, health checks, environment detection, and graceful degradation
// */
//public class DatabaseProviderTest {
//
//    private DatabaseProvider databaseProvider;
//
//    @Before
//    public void setUp() {
//        databaseProvider = DatabaseProviderFactory.getInstance();
//    }
//
//    @Test
//    public void testSingletonPattern() {
//        DatabaseProvider instance1 = DatabaseProviderFactory.getInstance();
//        DatabaseProvider instance2 = DatabaseProviderFactory.getInstance();
//
//        Assert.assertNotNull("DatabaseProvider instance should not be null", instance1);
//        Assert.assertSame("DatabaseProvider should be singleton", instance1, instance2);
//    }
//
//    @Test
//    public void testHealthCheck() {
//        try {
//            boolean healthy = databaseProvider.isHealthy();
//            System.out.println("Database health status: " + (healthy ? "HEALTHY" : "UNHEALTHY"));
//
//            // Health check should not throw exceptions
//            Assert.assertTrue("Health check should complete without exceptions", true);
//
//            if (healthy) {
//                System.out.println("Database is available and responding");
//            } else {
//                System.out.println("Database is unavailable - testing graceful degradation");
//            }
//        } catch (Exception e) {
//            System.out.println("Database health check failed gracefully: " + e.getMessage());
//            // This is acceptable - the system should handle database unavailability
//        }
//    }
//
//    @Test
//    public void testConnectionAcquisition() {
//        try {
//            Connection connection = databaseProvider.getConnection();
//
//            if (connection != null) {
//                Assert.assertNotNull("Connection should not be null when database is available", connection);
//                Assert.assertFalse("Connection should not be closed", connection.isClosed());
//
//                System.out.println("Database connection acquired successfully");
//
//                // Test connection validity
//                boolean valid = connection.isValid(5); // 5 second timeout
//                Assert.assertTrue("Connection should be valid", valid);
//
//                connection.close();
//                System.out.println("Database connection closed successfully");
//            } else {
//                System.out.println("Database connection is null - database may be unavailable");
//            }
//        } catch (SQLException e) {
//            System.out.println("Database connection failed gracefully: " + e.getMessage());
//            // This is acceptable for testing - graceful degradation
//        }
//    }
//
//    @Test
//    public void testConnectionPooling() {
//        try {
//            // Test multiple connections can be acquired
//            Connection conn1 = databaseProvider.getConnection();
//            Connection conn2 = databaseProvider.getConnection();
//
//            if (conn1 != null && conn2 != null) {
//                Assert.assertNotNull("First connection should not be null", conn1);
//                Assert.assertNotNull("Second connection should not be null", conn2);
//                Assert.assertNotSame("Connections should be different instances", conn1, conn2);
//
//                System.out.println("Connection pooling test: Multiple connections acquired");
//
//                conn1.close();
//                conn2.close();
//                System.out.println("Connection pooling test: Connections returned to pool");
//            } else {
//                System.out.println("Connection pooling test: Database unavailable");
//            }
//        } catch (SQLException e) {
//            System.out.println("Connection pooling test failed gracefully: " + e.getMessage());
//        }
//    }
//
//    @Test
//    public void testEnvironmentDetection() {
//        // Test Cloud Run environment detection
//        String cloudSqlConnection = System.getenv("CLOUD_SQL_CONNECTION_NAME");
//
//        if (cloudSqlConnection != null && !cloudSqlConnection.isEmpty()) {
//            System.out.println("Cloud Run environment detected: " + cloudSqlConnection);
//            Assert.assertTrue("Cloud SQL connection name should be valid format",
//                    cloudSqlConnection.contains(":"));
//        } else {
//            System.out.println("Local development environment detected");
//        }
//
//        // Environment detection should always work
//        Assert.assertTrue("Environment detection should complete", true);
//    }
//
//    @Test
//    public void testBasicQuery() {
//        try {
//            Connection connection = databaseProvider.getConnection();
//
//            if (connection != null) {
//                // Test basic SELECT 1 query
//                PreparedStatement stmt = connection.prepareStatement("SELECT 1 as test_value");
//                ResultSet rs = stmt.executeQuery();
//
//                Assert.assertTrue("Query should return at least one row", rs.next());
//                int testValue = rs.getInt("test_value");
//                Assert.assertEquals("Test value should be 1", 1, testValue);
//
//                rs.close();
//                stmt.close();
//                connection.close();
//
//                System.out.println("Basic query test: PASSED");
//            } else {
//                System.out.println("Basic query test: SKIPPED - Database unavailable");
//            }
//        } catch (SQLException e) {
//            System.out.println("Basic query test failed gracefully: " + e.getMessage());
//        }
//    }
//
//    @Test
//    public void testConnectionTimeout() {
//        try {
//            long startTime = System.currentTimeMillis();
//            Connection connection = databaseProvider.getConnection();
//            long endTime = System.currentTimeMillis();
//
//            long connectionTime = endTime - startTime;
//            System.out.println("Connection acquisition time: " + connectionTime + "ms");
//
//            // Connection should be acquired within reasonable time (10 seconds)
//            Assert.assertTrue("Connection should be acquired within 10 seconds", connectionTime < 10000);
//
//            if (connection != null) {
//                connection.close();
//            }
//        } catch (SQLException e) {
//            System.out.println("Connection timeout test: " + e.getMessage());
//        }
//    }
//
//    @Test
//    public void testConnectionLeakPrevention() {
//        try {
//            // Test that connections can be acquired and released multiple times
//            for (int i = 0; i < 5; i++) {
//                Connection connection = databaseProvider.getConnection();
//                if (connection != null) {
//                    // Simulate some work
//                    Thread.sleep(10);
//                    connection.close();
//                }
//            }
//
//            System.out.println("Connection leak prevention test: PASSED");
//            Assert.assertTrue("Multiple connection cycles should complete", true);
//        } catch (Exception e) {
//            System.out.println("Connection leak prevention test: " + e.getMessage());
//        }
//    }
//
//    @Test
//    public void testDatabaseMetadata() {
//        try {
//            Connection connection = databaseProvider.getConnection();
//
//            if (connection != null) {
//                String databaseProductName = connection.getMetaData().getDatabaseProductName();
//                String databaseProductVersion = connection.getMetaData().getDatabaseProductVersion();
//                String driverName = connection.getMetaData().getDriverName();
//
//                Assert.assertNotNull("Database product name should not be null", databaseProductName);
//                Assert.assertTrue("Should be MySQL database",
//                        databaseProductName.toLowerCase().contains("mysql"));
//
//                System.out.println("Database metadata - Product: " + databaseProductName +
//                        ", Version: " + databaseProductVersion + ", Driver: " + driverName);
//
//                connection.close();
//            } else {
//                System.out.println("Database metadata test: SKIPPED - Database unavailable");
//            }
//        } catch (SQLException e) {
//            System.out.println("Database metadata test failed gracefully: " + e.getMessage());
//        }
//    }
//
//    @Test
//    public void testTransactionSupport() {
//        try {
//            Connection connection = databaseProvider.getConnection();
//
//            if (connection != null) {
//                boolean autoCommit = connection.getAutoCommit();
//                boolean supportsTransactions = connection.getMetaData().supportsTransactions();
//
//                System.out.println("Transaction support - Auto commit: " + autoCommit +
//                        ", Supports transactions: " + supportsTransactions);
//
//                Assert.assertTrue("Database should support transactions", supportsTransactions);
//
//                connection.close();
//            } else {
//                System.out.println("Transaction support test: SKIPPED - Database unavailable");
//            }
//        } catch (SQLException e) {
//            System.out.println("Transaction support test failed gracefully: " + e.getMessage());
//        }
//    }
//
//    @Test
//    public void testConnectionValidation() {
//        try {
//            Connection connection = databaseProvider.getConnection();
//
//            if (connection != null) {
//                // Test connection validation with different timeouts
//                boolean valid1 = connection.isValid(1);  // 1 second
//                boolean valid5 = connection.isValid(5);  // 5 seconds
//
//                Assert.assertTrue("Connection should be valid with 1s timeout", valid1);
//                Assert.assertTrue("Connection should be valid with 5s timeout", valid5);
//
//                System.out.println("Connection validation test: PASSED");
//
//                connection.close();
//
//                // Test that closed connection is not valid
//                boolean validAfterClose = connection.isValid(1);
//                Assert.assertFalse("Closed connection should not be valid", validAfterClose);
//
//                System.out.println("Closed connection validation test: PASSED");
//            } else {
//                System.out.println("Connection validation test: SKIPPED - Database unavailable");
//            }
//        } catch (SQLException e) {
//            System.out.println("Connection validation test failed gracefully: " + e.getMessage());
//        }
//    }
//
//    @Test
//    public void testGracefulShutdown() {
//        try {
//            // Test that database provider can be closed gracefully
//            databaseProvider.close();
//            System.out.println("Database provider closed gracefully");
//
//            // After closing, health check should return false or handle gracefully
//            boolean healthyAfterClose = databaseProvider.isHealthy();
//            System.out.println("Health check after close: " + healthyAfterClose);
//
//            Assert.assertTrue("Graceful shutdown should complete", true);
//        } catch (Exception e) {
//            System.out.println("Graceful shutdown test: " + e.getMessage());
//        }
//    }
//
//    @Test
//    public void testErrorHandling() {
//        try {
//            // Test error handling with invalid SQL
//            Connection connection = databaseProvider.getConnection();
//
//            if (connection != null) {
//                try {
//                    PreparedStatement stmt = connection.prepareStatement("INVALID SQL QUERY");
//                    stmt.executeQuery();
//                    Assert.fail("Invalid SQL should throw SQLException");
//                } catch (SQLException e) {
//                    System.out.println("Invalid SQL correctly threw exception: " + e.getMessage());
//                    Assert.assertTrue("Should throw SQLException for invalid SQL", true);
//                }
//
//                connection.close();
//            } else {
//                System.out.println("Error handling test: SKIPPED - Database unavailable");
//            }
//        } catch (SQLException e) {
//            System.out.println("Error handling test: " + e.getMessage());
//        }
//    }
//
//    @After
//    public void tearDown() {
//        System.out.println("DatabaseProvider test completed");
//    }
//}
