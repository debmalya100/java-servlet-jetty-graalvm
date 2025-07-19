package com.example.sse.servlet;

import com.example.sse.database.DatabaseManager;
import com.example.sse.database.DatabaseProviderFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Production Health Check - No Background Tasks Required
 * Provides real-time monitoring without background threads
 */
@WebServlet("/health/*")
public class ProductionHealthServlet extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(ProductionHealthServlet.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String pathInfo = request.getPathInfo();
        Map<String, Object> healthResponse;

        try {
            switch (pathInfo != null ? pathInfo : "/") {
                case "/database":
                    healthResponse = getDatabaseHealth();
                    break;
                case "/cache":
                    healthResponse = getCacheHealth();
                    break;
                case "/performance":
                    healthResponse = getPerformanceHealth();
                    break;
                case "/":
                case "/overall":
                default:
                    healthResponse = getOverallHealth();
                    break;
            }

            // Set status based on health
            boolean isHealthy = "healthy".equals(healthResponse.get("status"));
            response.setStatus(isHealthy ? 200 : 503);

        } catch (Exception e) {
            logger.error("Health check failed", e);
            healthResponse = Map.of(
                    "timestamp", System.currentTimeMillis(),
                    "status", "error",
                    "error", e.getMessage());
            response.setStatus(503);
        }

        response.getWriter().write(objectMapper.writeValueAsString(healthResponse));
        response.getWriter().flush();
    }

    /**
     * Overall health - combines all checks
     */
    private Map<String, Object> getOverallHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("timestamp", System.currentTimeMillis());
        health.put("service", "sse-servlet");

        try {
            // Check database
            var dbProvider = DatabaseProviderFactory.getInstance();
            boolean dbHealthy = dbProvider.isHealthy();

            // Get production servlet stats
            SSEServlet servlet = getProductionServlet();
            Map<String, Object> stats = servlet != null ? servlet.getProductionStats() : Map.of();

            // Determine overall health
            boolean overallHealthy = dbHealthy;
            health.put("status", overallHealthy ? "healthy" : "unhealthy");

            // Add component statuses
            Map<String, Object> components = new HashMap<>();
            components.put("database", dbHealthy ? "healthy" : "unhealthy");
            components.put("cache", "healthy"); // Cache doesn't fail, just misses
            components.put("servlet", "healthy");
            health.put("components", components);

            // Add basic stats
            if (!stats.isEmpty()) {
                health.put("uptime_ms", stats.get("uptime_ms"));
                health.put("total_requests", stats.get("total_requests"));
                health.put("cache_hit_rate", stats.get("cache_hit_rate"));
                health.put("timeout_rate", stats.get("timeout_rate"));
            }

        } catch (Exception e) {
            health.put("status", "error");
            health.put("error", e.getMessage());
        }

        return health;
    }

    /**
     * Database health - real-time check
     */
    private Map<String, Object> getDatabaseHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("timestamp", System.currentTimeMillis());
        health.put("component", "database");

        try {
            var dbProvider = DatabaseProviderFactory.getInstance();

            // Real-time health check
            long startTime = System.currentTimeMillis();
            boolean isHealthy = dbProvider.isHealthy();
            long checkTime = System.currentTimeMillis() - startTime;

            health.put("status", isHealthy ? "healthy" : "unhealthy");
            health.put("response_time_ms", checkTime);

            // Get connection pool stats if available
            if (dbProvider instanceof DatabaseManager) {
                DatabaseManager dbManager = (DatabaseManager) dbProvider;
                var poolStats = dbManager.getStats();

                Map<String, Object> connectionPool = new HashMap<>();
                connectionPool.put("total_connections", poolStats.totalConnections());
                connectionPool.put("active_connections", poolStats.activeConnections());
                connectionPool.put("idle_connections", poolStats.idleConnections());
                connectionPool.put("total_requests", poolStats.totalRequests());
                connectionPool.put("connection_errors", poolStats.connectionErrors());

                if (poolStats.totalConnections() > 0) {
                    double utilization = (poolStats.activeConnections() * 100.0) / poolStats.totalConnections();
                    connectionPool.put("utilization_percent", Math.round(utilization * 10) / 10.0);
                }

                if (poolStats.totalRequests() > 0) {
                    double errorRate = (poolStats.connectionErrors() * 100.0) / poolStats.totalRequests();
                    connectionPool.put("error_rate_percent", Math.round(errorRate * 100) / 100.0);
                }

                health.put("connection_pool", connectionPool);

                // Performance indicators
                Map<String, String> indicators = new HashMap<>();
                Double utilization = (Double) connectionPool.get("utilization_percent");
                Double errorRate = (Double) connectionPool.get("error_rate_percent");

                if (utilization != null) {
                    if (utilization > 90) {
                        indicators.put("pool_utilization", "HIGH");
                    } else if (utilization > 70) {
                        indicators.put("pool_utilization", "MODERATE");
                    } else {
                        indicators.put("pool_utilization", "NORMAL");
                    }
                }

                if (errorRate != null) {
                    if (errorRate > 5) {
                        indicators.put("error_rate", "HIGH");
                    } else if (errorRate > 1) {
                        indicators.put("error_rate", "ELEVATED");
                    } else {
                        indicators.put("error_rate", "NORMAL");
                    }
                }

                health.put("performance_indicators", indicators);
            }

        } catch (Exception e) {
            health.put("status", "error");
            health.put("error", e.getMessage());
        }

        return health;
    }

    /**
     * Cache health - real-time stats
     */
    private Map<String, Object> getCacheHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("timestamp", System.currentTimeMillis());
        health.put("component", "cache");
        health.put("status", "healthy"); // Cache doesn't "fail", just misses

        try {
            SSEServlet servlet = getProductionServlet();
            if (servlet != null) {
                Map<String, Object> stats = servlet.getProductionStats();

                health.put("cache_hit_rate", stats.get("cache_hit_rate"));
                health.put("cache_sizes", stats.get("cache_sizes"));

                // Cache performance indicators
                Map<String, String> indicators = new HashMap<>();
                Double hitRate = (Double) stats.get("cache_hit_rate");

                if (hitRate != null) {
                    if (hitRate > 80) {
                        indicators.put("hit_rate", "EXCELLENT");
                    } else if (hitRate > 60) {
                        indicators.put("hit_rate", "GOOD");
                    } else if (hitRate > 40) {
                        indicators.put("hit_rate", "MODERATE");
                    } else {
                        indicators.put("hit_rate", "LOW");
                    }
                }

                health.put("performance_indicators", indicators);
            }

        } catch (Exception e) {
            health.put("status", "error");
            health.put("error", e.getMessage());
        }

        return health;
    }

    /**
     * Performance health - real-time metrics
     */
    private Map<String, Object> getPerformanceHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("timestamp", System.currentTimeMillis());
        health.put("component", "performance");

        try {
            SSEServlet servlet = getProductionServlet();
            if (servlet != null) {
                Map<String, Object> stats = servlet.getProductionStats();

                // Determine performance health
                Double timeoutRate = (Double) stats.get("timeout_rate");
                boolean performanceHealthy = timeoutRate == null || timeoutRate < 5.0;

                health.put("status", performanceHealthy ? "healthy" : "degraded");
                health.putAll(stats);

                // Memory stats
                long usedMemory = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024
                        / 1024;
                long maxMemory = Runtime.getRuntime().maxMemory() / 1024 / 1024;
                double memoryUsage = (usedMemory * 100.0) / maxMemory;

                Map<String, Object> memory = new HashMap<>();
                memory.put("used_mb", usedMemory);
                memory.put("max_mb", maxMemory);
                memory.put("usage_percent", Math.round(memoryUsage * 10) / 10.0);
                health.put("memory", memory);

                // Performance indicators
                Map<String, String> indicators = new HashMap<>();

                if (timeoutRate != null) {
                    if (timeoutRate > 10) {
                        indicators.put("timeout_rate", "HIGH");
                    } else if (timeoutRate > 5) {
                        indicators.put("timeout_rate", "ELEVATED");
                    } else {
                        indicators.put("timeout_rate", "NORMAL");
                    }
                }

                if (memoryUsage > 90) {
                    indicators.put("memory_usage", "HIGH");
                } else if (memoryUsage > 70) {
                    indicators.put("memory_usage", "MODERATE");
                } else {
                    indicators.put("memory_usage", "NORMAL");
                }

                health.put("performance_indicators", indicators);

            } else {
                health.put("status", "unknown");
                health.put("error", "ProductionSSEServlet not found");
            }

        } catch (Exception e) {
            health.put("status", "error");
            health.put("error", e.getMessage());
        }

        return health;
    }

    /**
     * Get reference to ProductionSSEServlet for stats
     */
    private SSEServlet getProductionServlet() {
        try {
            // This would need to be implemented based on your servlet container
            // You could use a static reference or servlet context lookup
            // For now, return null - implement based on your setup
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}