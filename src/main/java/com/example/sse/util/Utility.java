package com.example.sse.util;

import com.example.sse.exception.ServiceException;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public class Utility {

    private static final ConcurrentMap<String, String> templateCache = new ConcurrentHashMap<>();

    public static String loadTemplate(String path) {
        return templateCache.computeIfAbsent(path, key -> {
            try (InputStream in = Utility.class.getClassLoader().getResourceAsStream(key);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(in), StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            } catch (Exception e) {
                throw new ServiceException(ServiceException.ServiceType.RESOURCE, "Failed to load resource: " + key, e);
            }
        });
    }

    public static String loadTemplate(String path, Map<String, String> tokens) {
        String template = loadTemplate(path);
        for (Map.Entry<String, String> entry : tokens.entrySet()) {
            template = template.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return template;
    }

    public static void clearTemplateCache() {
        templateCache.clear();
    }
}