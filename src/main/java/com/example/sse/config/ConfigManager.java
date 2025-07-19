package com.example.sse.config;

import com.example.sse.exception.ServiceException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigManager {
    private static ConfigManager instance;
    private Properties properties;

    private ConfigManager() {
        loadProperties();
    }

    public static synchronized ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    private void loadProperties() {
        properties = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (input != null) {
                assert properties != null;
                properties.load(input);
            } else {
                throw new ServiceException(ServiceException.ServiceType.CONFIGURATION, "application.properties file not found");
            }
        } catch (IOException e) {
            throw new ServiceException(ServiceException.ServiceType.CONFIGURATION, "Error loading application.properties", e);
        }
    }

    public String getProperty(String key) {
        if (key == null || key.trim().isEmpty()) {
            return null;
        }
        return properties.getProperty(key);
    }

    public String getProperty(String key, String defaultValue) {
        if (key == null || key.trim().isEmpty()) {
            return defaultValue;
        }
        return properties.getProperty(key, defaultValue);
    }

    public int getIntProperty(String key, int defaultValue) {
        if (key == null || key.trim().isEmpty()) {
            return defaultValue;
        }
        String value = properties.getProperty(key);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    public long getLongProperty(String key, long defaultValue) {
        if (key == null || key.trim().isEmpty()) {
            return defaultValue;
        }
        String value = properties.getProperty(key);
        if (value != null) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    public boolean getBooleanProperty(String key, boolean defaultValue) {
        if (key == null || key.trim().isEmpty()) {
            return defaultValue;
        }
        String value = properties.getProperty(key);
        if (value != null) {
            return Boolean.parseBoolean(value.trim());
        }
        return defaultValue;
    }
}