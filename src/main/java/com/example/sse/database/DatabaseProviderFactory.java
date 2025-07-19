package com.example.sse.database;

public class DatabaseProviderFactory {
    private static DatabaseProvider instance;

    public static synchronized DatabaseProvider getInstance() {
        if (instance == null) {
            instance = DatabaseManager.getInstance();
        }
        return instance;
    }

    public static synchronized void setInstance(DatabaseProvider customInstance) {
        instance = customInstance;
    }
}
