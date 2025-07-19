package com.example.sse.database;

import com.example.sse.config.ConfigManager;
import com.example.sse.exception.ServiceException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

/**
 * CLOUD RUN OPTIMIZED Database Manager with HikariCP Connection Pooling
 * Replaces your existing DatabaseManager with high-performance connection
 * pooling
 */
public class DatabaseManager implements DatabaseProvider {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private static DatabaseManager instance;
    private HikariDataSource dataSource;
    private volatile boolean initialized = false;

    // Performance monitoring
    private final AtomicLong connectionRequests = new AtomicLong(0);
    private final AtomicLong connectionErrors = new AtomicLong(0);
    private volatile long initTime = 0;

    // Environment Detection
    public static final boolean IS_CLOUD_RUN = System.getenv("K_SERVICE") != null ||
            System.getenv("GAE_APPLICATION") != null ||
            "true".equals(System.getenv("CLOUD_RUN"));
    public static final boolean IS_LOCAL = !IS_CLOUD_RUN;

    private DatabaseManager() {
        initializeDataSource();
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    private void initializeDataSource() {
        if (initialized) {
            logger.info("🔄 Database connection pool already initialized");
            return;
        }

        long startTime = System.currentTimeMillis();
        logger.info("🌤️ Initializing CLOUD RUN Database Connection Pool...");

        try {
            // Load MySQL driver
            Class.forName("com.mysql.cj.jdbc.Driver");
            logger.info("✅ MySQL JDBC driver loaded");
        } catch (ClassNotFoundException e) {
            logger.error("❌ Failed to load JDBC driver: {}", e.getMessage());
            throw new RuntimeException("JDBC Driver not found", e);
        }

        try {
            ConfigManager config = ConfigManager.getInstance();

            // Get database configuration
            String PROJECT_ID = config.getProperty("mysql.cms.project.id");
            String REGION = config.getProperty("mysql.cms.region");
            String INSTANCE_NAME = config.getProperty("mysql.cms.instance.name");
            String USER = config.getProperty("mysql.cms.user");
            String PASSWORD = config.getProperty("mysql.cms.password");
            String DATABASE = config.getProperty("mysql.cms.database");
            String IP_ADDRESS = config.getProperty("mysql.cms.ip.address");

            logger.info("=== Database Configuration ===");
            logger.info("Environment: {}", IS_CLOUD_RUN ? "CLOUD RUN" : "LOCAL");
            if (IS_CLOUD_RUN) {
                logger.info("PROJECT_ID: {}", PROJECT_ID);
                logger.info("REGION: {}", REGION);
                logger.info("INSTANCE_NAME: {}", INSTANCE_NAME);
            } else {
                logger.info("IP_ADDRESS: {}", IP_ADDRESS);
            }
            logger.info("DATABASE: {}", DATABASE);
            logger.info("USER: {}", USER);
            logger.info("PASSWORD: {}", PASSWORD != null ? "***SET***" : "NOT SET");

            // Validate required configuration
            validateConfiguration(PROJECT_ID, REGION, INSTANCE_NAME, USER, PASSWORD, DATABASE, IP_ADDRESS);

            // Create HikariCP configuration
            HikariConfig hikariConfig = new HikariConfig();

            // Build JDBC URL
            String jdbcUrl = buildJdbcUrl(PROJECT_ID, REGION, INSTANCE_NAME, DATABASE, IP_ADDRESS);
            hikariConfig.setJdbcUrl(jdbcUrl);
            hikariConfig.setUsername(USER);
            hikariConfig.setPassword(PASSWORD);
            hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");

            // Configure connection pool settings
            configurePoolSettings(hikariConfig, config);

            // Configure connection properties
            configureConnectionProperties(hikariConfig, PROJECT_ID, REGION, INSTANCE_NAME);

            // Performance and monitoring settings
            configurePerformanceSettings(hikariConfig);

            // Create the data source
            logger.info("🔧 Creating HikariCP DataSource...");
            this.dataSource = new HikariDataSource(hikariConfig);

            // Test the connection pool
            testConnectionPool();

            this.initialized = true;
            this.initTime = System.currentTimeMillis() - startTime;

            logger.info("✅ Database connection pool initialized successfully in {}ms", initTime);
            logPoolConfiguration();

        } catch (Exception e) {
            logger.error("❌ Failed to initialize database connection pool", e);
            throw new ServiceException(ServiceException.ServiceType.DATABASE,
                    "Database initialization failed: " + e.getMessage(), e);
        }
    }

    /**
     * Validate database configuration
     */
    private void validateConfiguration(String projectId, String region, String instanceName,
            String user, String password, String database, String ipAddress) {
        if (user == null || user.trim().isEmpty()) {
            throw new ServiceException(ServiceException.ServiceType.DATABASE,
                    "Database username is missing (mysql.cms.user)");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new ServiceException(ServiceException.ServiceType.DATABASE,
                    "Database password is missing (mysql.cms.password)");
        }
        if (database == null || database.trim().isEmpty()) {
            throw new ServiceException(ServiceException.ServiceType.DATABASE,
                    "Database name is missing (mysql.cms.database)");
        }

        if (IS_CLOUD_RUN) {
            if (projectId == null || projectId.trim().isEmpty()) {
                throw new ServiceException(ServiceException.ServiceType.DATABASE,
                        "PROJECT_ID is missing for Cloud Run (mysql.cms.project.id)");
            }
            if (region == null || region.trim().isEmpty()) {
                throw new ServiceException(ServiceException.ServiceType.DATABASE,
                        "REGION is missing for Cloud Run (mysql.cms.region)");
            }
            if (instanceName == null || instanceName.trim().isEmpty()) {
                throw new ServiceException(ServiceException.ServiceType.DATABASE,
                        "INSTANCE_NAME is missing for Cloud Run (mysql.cms.instance.name)");
            }
        } else {
            if (ipAddress == null || ipAddress.trim().isEmpty()) {
                throw new ServiceException(ServiceException.ServiceType.DATABASE,
                        "IP address is missing for local connection (mysql.cms.ip.address)");
            }
        }
    }

    /**
     * Build JDBC URL for Cloud Run or local environment
     */
    private String buildJdbcUrl(String projectId, String region, String instanceName,
            String database, String ipAddress) {

        String url;

        if (IS_CLOUD_RUN) {
            logger.info("🌤️ Building Cloud SQL connection URL...");
            String connectionName = projectId + ":" + region + ":" + instanceName;

            // Cloud SQL Socket Factory URL (recommended for Cloud Run)
            url = String.format(
                    "jdbc:mysql://google/%s?socketFactory=com.google.cloud.sql.mysql.SocketFactory&cloudSqlInstance=%s&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                    database, connectionName);

            logger.info("🔗 Cloud SQL Connection Name: {}", connectionName);

        } else {
            logger.info("🏠 Building local database connection URL...");

            url = String.format(
                    "jdbc:mysql://%s:3306/%s?useSSL=true&allowPublicKeyRetrieval=true&serverTimezone=UTC&autoReconnect=true",
                    ipAddress, database);
        }

        logger.info("🔗 JDBC URL: {}", url.replaceAll("password=[^&]*", "password=***"));
        return url;
    }

    /**
     * Configure connection pool settings optimized for Cloud Run
     */
    private void configurePoolSettings(HikariConfig config, ConfigManager configManager) {
        logger.info("🔧 Configuring connection pool settings...");

        // Cloud Run optimized pool settings
        int maxPoolSize, minIdle;
        long connectionTimeout, idleTimeout, maxLifetime;

        if (IS_CLOUD_RUN) {
            // Conservative settings for Cloud Run (1 vCPU, 1GB RAM, 500 concurrency)
            maxPoolSize = Integer.parseInt(configManager.getProperty("mysql.cms.pool.max", "10"));
            minIdle = Integer.parseInt(configManager.getProperty("mysql.cms.pool.initial", "3"));
            connectionTimeout = Long.parseLong(configManager.getProperty("mysql.cms.pool.timeout", "15000"));
            idleTimeout = 300000; // 5 minutes
            maxLifetime = 900000; // 20 minutes
        } else {
            // More generous settings for local development
            maxPoolSize = Integer.parseInt(configManager.getProperty("mysql.cms.pool.max", "15"));
            minIdle = Integer.parseInt(configManager.getProperty("mysql.cms.pool.initial", "5"));
            connectionTimeout = Long.parseLong(configManager.getProperty("mysql.cms.pool.timeout", "30000"));
            idleTimeout = 600000; // 10 minutes
            maxLifetime = 1800000; // 30 minutes
        }

        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(minIdle);
        config.setConnectionTimeout(connectionTimeout);
        config.setIdleTimeout(idleTimeout);
        config.setMaxLifetime(maxLifetime);
        config.setLeakDetectionThreshold(90000); // 1.5 minutes

        // Connection validation
        config.setConnectionTestQuery("SELECT 1");
        config.setValidationTimeout(5000);

        logger.info("🔧 Pool Settings:");
        logger.info("   Max Pool Size: {}", maxPoolSize);
        logger.info("   Min Idle: {}", minIdle);
        logger.info("   Connection Timeout: {}ms", connectionTimeout);
        logger.info("   Idle Timeout: {}ms", idleTimeout);
        logger.info("   Max Lifetime: {}ms", maxLifetime);
    }

    /**
     * Configure Cloud SQL specific connection properties
     */
    private void configureConnectionProperties(HikariConfig config, String projectId, String region,
            String instanceName) {
        logger.info("🔧 Configuring connection properties...");

        if (IS_CLOUD_RUN) {
            String connectionName = projectId + ":" + region + ":" + instanceName;

            // Cloud SQL Socket Factory properties
            config.addDataSourceProperty("socketFactory", "com.google.cloud.sql.mysql.SocketFactory");
            config.addDataSourceProperty("cloudSqlInstance", connectionName);
            config.addDataSourceProperty("useSSL", "false");
            config.addDataSourceProperty("allowPublicKeyRetrieval", "true");
        } else {
            // Local connection properties
            config.addDataSourceProperty("useSSL", "true");
            config.addDataSourceProperty("allowPublicKeyRetrieval", "true");
            config.addDataSourceProperty("verifyServerCertificate", "false");
        }

        // Common optimizations
        config.addDataSourceProperty("serverTimezone", "UTC");
        config.addDataSourceProperty("autoReconnect", "true");
        config.addDataSourceProperty("failOverReadOnly", "false");
        config.addDataSourceProperty("maxReconnects", "3");
        config.addDataSourceProperty("initialTimeout", "2");

        // Performance optimizations
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "500");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");
        config.addDataSourceProperty("useLocalSessionState", "true");

        // Timeouts
        config.addDataSourceProperty("connectTimeout", IS_CLOUD_RUN ? "10000" : "5000");
        config.addDataSourceProperty("socketTimeout", IS_CLOUD_RUN ? "30000" : "5000");

        // Character encoding
        config.addDataSourceProperty("characterEncoding", "utf8");
        config.addDataSourceProperty("useUnicode", "true");

        logger.info("🔧 Connection properties configured for {}", IS_CLOUD_RUN ? "Cloud Run" : "Local");
    }

    /**
     * Configure HikariCP performance settings
     */
    private void configurePerformanceSettings(HikariConfig config) {
        // Pool name for monitoring
        config.setPoolName(IS_CLOUD_RUN ? "CloudRunSSEPool" : "LocalSSEPool");

        // Register JMX beans for monitoring
        config.setRegisterMbeans(true);

        // Thread factory for pool threads
        config.setThreadFactory(r -> {
            Thread t = new Thread(r, "hikari-pool-" + System.currentTimeMillis());
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Test connection pool functionality
     */
    private void testConnectionPool() {
        logger.info("🧪 Testing connection pool...");

        try (Connection conn = dataSource.getConnection()) {
            if (conn == null || conn.isClosed()) {
                throw new SQLException("Connection test failed - connection is null or closed");
            }

            // Test simple query
            try (var stmt = conn.createStatement();
                    var rs = stmt.executeQuery("SELECT 1 as test, NOW() as server_time")) {

                if (rs.next()) {
                    int testResult = rs.getInt("test");
                    String serverTime = rs.getString("server_time");

                    if (testResult == 1) {
                        logger.info("✅ Connection pool test PASSED");
                        logger.info("🕐 Database server time: {}", serverTime);
                    } else {
                        throw new SQLException("Test query returned unexpected result: " + testResult);
                    }
                } else {
                    throw new SQLException("Test query returned no results");
                }
            }

            // Log connection metadata
            var meta = conn.getMetaData();
            logger.info("🔗 Connected to: {} {}", meta.getDatabaseProductName(), meta.getDatabaseProductVersion());
            logger.info("🔗 Driver: {} {}", meta.getDriverName(), meta.getDriverVersion());
            logger.info("🔗 URL: {}", meta.getURL().replaceAll("password=[^&]*", "password=***"));

        } catch (SQLException e) {
            logger.error("❌ Connection pool test FAILED: {}", e.getMessage());
            logger.error("❌ SQL State: {}", e.getSQLState());
            logger.error("❌ Error Code: {}", e.getErrorCode());

            if (IS_CLOUD_RUN) {
                logger.error("🔍 Cloud Run Debug Info:");
                logger.error("   K_SERVICE: {}", System.getenv("K_SERVICE"));
                logger.error("   GAE_APPLICATION: {}", System.getenv("GAE_APPLICATION"));
                logger.error("   GOOGLE_CLOUD_PROJECT: {}", System.getenv("GOOGLE_CLOUD_PROJECT"));
            }

            throw new RuntimeException("Connection pool test failed", e);
        }
    }

    /**
     * Log pool configuration
     */
    private void logPoolConfiguration() {
        if (dataSource != null) {
            var config = dataSource.getHikariConfigMXBean();
            logger.info("🔧 === HIKARI POOL CONFIGURATION ===");
            logger.info("🔧 Pool Name: {}", config.getPoolName());
            logger.info("🔧 Max Pool Size: {}", config.getMaximumPoolSize());
            logger.info("🔧 Min Idle: {}", config.getMinimumIdle());
            logger.info("🔧 Connection Timeout: {}ms", config.getConnectionTimeout());
            logger.info("🔧 Idle Timeout: {}ms", config.getIdleTimeout());
            logger.info("🔧 Max Lifetime: {}ms", config.getMaxLifetime());
            logger.info("🔧 ================================");
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (!initialized) {
            throw new SQLException("Database connection pool not initialized");
        }

        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Database connection pool is not available");
        }

        long requestId = connectionRequests.incrementAndGet();
        long startTime = System.currentTimeMillis();

        try {
            Connection conn = dataSource.getConnection();
            long getTime = System.currentTimeMillis() - startTime;

            if (conn == null) {
                throw new SQLException("Failed to obtain connection from pool");
            }

            // Log slow connection acquisitions
            if (getTime > 100) {
                logger.warn("⏰ Slow connection acquisition: {}ms (request #{})", getTime, requestId);
            } else {
                logger.debug("⚡ Fast connection: {}ms (request #{})", getTime, requestId);
            }

            return conn;

        } catch (SQLException e) {
            connectionErrors.incrementAndGet();
            logger.error("❌ Failed to get connection (request #{}): {}", requestId, e.getMessage());
            throw e;
        }
    }

    @Override
    public boolean isHealthy() {
        if (!initialized || dataSource == null || dataSource.isClosed()) {
            logger.debug("Database health check failed - DataSource not available");
            return false;
        }

        try (Connection connection = getConnection()) {
            if (connection == null || connection.isClosed()) {
                logger.debug("Database health check failed - Connection not available");
                return false;
            }

            // Test with a simple query
            try (var stmt = connection.createStatement();
                    var rs = stmt.executeQuery("SELECT 1")) {
                boolean healthy = rs.next();
                if (!healthy) {
                    logger.debug("Database health check failed - Query returned no results");
                }
                return healthy;
            }
        } catch (SQLException e) {
            logger.warn("Database health check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getDbUrl() {
        ConfigManager config = ConfigManager.getInstance();

        if (IS_CLOUD_RUN) {
            String PROJECT_ID = config.getProperty("mysql.cms.project.id");
            String REGION = config.getProperty("mysql.cms.region");
            String INSTANCE_NAME = config.getProperty("mysql.cms.instance.name");
            String DATABASE = config.getProperty("mysql.cms.database");
            String CONNECTION_NAME = PROJECT_ID + ":" + REGION + ":" + INSTANCE_NAME;

            return String.format(
                    "jdbc:mysql://google/%s?socketFactory=com.google.cloud.sql.mysql.SocketFactory&cloudSqlInstance=%s&useSSL=false&allowPublicKeyRetrieval=true",
                    DATABASE, CONNECTION_NAME);
        } else {
            String IP_ADDRESS = config.getProperty("mysql.cms.ip.address");
            String DATABASE = config.getProperty("mysql.cms.database");

            return String.format(
                    "jdbc:mysql://%s:3306/%s?useSSL=true&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                    IP_ADDRESS, DATABASE);
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            logger.info("🛑 Shutting down database connection pool...");

            // Log final statistics
            logConnectionStats();

            dataSource.close();
            initialized = false;

            logger.info("✅ Database connection pool closed");
        }
    }

    /**
     * Get connection pool statistics
     */
    public ConnectionPoolStats getStats() {
        if (!initialized || dataSource == null) {
            return new ConnectionPoolStats(0, 0, 0, 0, 0, 0, 0);
        }

        var poolMXBean = dataSource.getHikariPoolMXBean();

        return new ConnectionPoolStats(
                poolMXBean.getTotalConnections(),
                poolMXBean.getActiveConnections(),
                poolMXBean.getIdleConnections(),
                connectionRequests.get(),
                connectionErrors.get(),
                poolMXBean.getThreadsAwaitingConnection(),
                initTime);
    }

    /**
     * Log connection statistics
     */
    public void logConnectionStats() {
        ConnectionPoolStats stats = getStats();

        logger.info("📊 === CONNECTION POOL STATISTICS ===");
        logger.info("📊 Total Connections: {}", stats.totalConnections());
        logger.info("📊 Active Connections: {}", stats.activeConnections());
        logger.info("📊 Idle Connections: {}", stats.idleConnections());
        logger.info("📊 Total Requests: {}", stats.totalRequests());
        logger.info("📊 Connection Errors: {}", stats.connectionErrors());
        logger.info("📊 Threads Waiting: {}", stats.threadsWaiting());
        logger.info("📊 Initialization Time: {}ms", stats.initTimeMs());

        if (stats.totalConnections() > 0) {
            double utilization = (stats.activeConnections() * 100.0) / stats.totalConnections();
            logger.info("📊 Pool Utilization: {:.1f}%", utilization);
        }

        if (stats.totalRequests() > 0) {
            double errorRate = (stats.connectionErrors() * 100.0) / stats.totalRequests();
            logger.info("📊 Error Rate: {:.2f}%", errorRate);
        }

        logger.info("📊 ===================================");
    }

    /**
     * Get active connections count
     */
    public int getActiveConnections() {
        return dataSource != null ? dataSource.getHikariPoolMXBean().getActiveConnections() : 0;
    }

    /**
     * Get idle connections count
     */
    public int getIdleConnections() {
        return dataSource != null ? dataSource.getHikariPoolMXBean().getIdleConnections() : 0;
    }

    /**
     * Get total connections count
     */
    public int getTotalConnections() {
        return dataSource != null ? dataSource.getHikariPoolMXBean().getTotalConnections() : 0;
    }

    /**
     * Record class for connection pool statistics
     */
    public record ConnectionPoolStats(
            int totalConnections,
            int activeConnections,
            int idleConnections,
            long totalRequests,
            long connectionErrors,
            int threadsWaiting,
            long initTimeMs) {
    }

    /**
     * Get optimized connection properties for cold start (keeping your original
     * method)
     */
    private static Properties getOptimizedConnectionProperties() {
        Properties props = new Properties();
        props.setProperty("useSSL", "true");
        props.setProperty("requireSSL", "false");
        props.setProperty("verifyServerCertificate", "false");
        props.setProperty("allowPublicKeyRetrieval", "true");

        // COLD START OPTIMIZATIONS
        props.setProperty("autoReconnect", "true");
        props.setProperty("failOverReadOnly", "false");
        props.setProperty("maxReconnects", "2");
        props.setProperty("initialTimeout", "2");
        props.setProperty("connectTimeout", "3000");
        props.setProperty("socketTimeout", "5000");
        props.setProperty("useLocalSessionState", "true");
        props.setProperty("elideSetAutoCommits", "true");
        props.setProperty("cachePrepStmts", "true");
        props.setProperty("prepStmtCacheSize", "25");
        props.setProperty("prepStmtCacheSqlLimit", "256");

        return props;
    }
}