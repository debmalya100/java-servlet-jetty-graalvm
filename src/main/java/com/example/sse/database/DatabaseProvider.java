package com.example.sse.database;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Updated DatabaseProvider interface with connection pool support
 */
public interface DatabaseProvider {
    /**
     * Get a database connection from the pool
     */
    Connection getConnection() throws SQLException;

    /**
     * Check if the database is healthy
     */
    boolean isHealthy();

    /**
     * Get the number of active connections
     */
    default int getActiveConnections() {
        return 0;
    }

    /**
     * Get the number of idle connections
     */
    default int getIdleConnections() {
        return 0;
    }

    /**
     * Get the total number of connections
     */
    default int getTotalConnections() {
        return 0;
    }

    /**
     * Get connection pool statistics (if supported)
     */
    default Object getStats() {
        return null;
    }

    /**
     * Log connection statistics
     */
    default void logConnectionStats() {
        // Default implementation does nothing
    }

    /**
     * Close the database connection pool
     */
    void close();

    /**
     * Get the database URL
     */
    String getDbUrl();
}