package com.example.sse.exception;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;

public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public static void handle(Exception e, HttpServletResponse response) {
        int statusCode = mapToHttpStatus(e);
        String message = buildErrorMessage(e, statusCode);

        logException(e, statusCode);
        writeJsonResponse(response, statusCode, message);
    }

    private static int mapToHttpStatus(Exception e) {
        if (e instanceof HttpException httpEx) {
            return httpEx.getStatusCode();
        } else if (e instanceof ServiceException serviceEx) {
            return mapServiceExceptionToHttpStatus(serviceEx);
        } else if (e instanceof IllegalArgumentException) {
            return HttpServletResponse.SC_BAD_REQUEST;
        } else if (e instanceof SQLException) {
            return HttpServletResponse.SC_SERVICE_UNAVAILABLE;
        } else if (e instanceof SecurityException) {
            return HttpServletResponse.SC_UNAUTHORIZED;
        }
        return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    }

    private static int mapServiceExceptionToHttpStatus(ServiceException serviceEx) {
        return switch (serviceEx.getServiceType()) {
            case DATABASE, CACHE -> HttpServletResponse.SC_SERVICE_UNAVAILABLE;
            case CONFIGURATION, RESOURCE -> HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            case JWT -> HttpServletResponse.SC_UNAUTHORIZED;
            case GENERAL -> HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        };
    }

    private static String buildErrorMessage(Exception e, int statusCode) {
        if (e instanceof HttpException) return e.getMessage();
        if (e instanceof ServiceException serviceEx) return serviceEx.getFormattedMessage();

        String prefix;
        switch (statusCode) {
            case HttpServletResponse.SC_BAD_REQUEST -> prefix = "Invalid request";
            case HttpServletResponse.SC_SERVICE_UNAVAILABLE -> prefix = "Service unavailable";
            case HttpServletResponse.SC_UNAUTHORIZED -> prefix = "Unauthorized";
            default -> prefix = "Internal server error";
        }
        return String.format("%s: %s", prefix, e.getMessage() != null ? e.getMessage() : "Unexpected error");
    }

    private static void logException(Exception e, int statusCode) {
        if (statusCode >= 500) {
            logger.error("Server error handled: {}", e.getMessage(), e);
        } else {
            logger.warn("Client error handled: {}", e.getMessage());
        }
    }

    private static void writeJsonResponse(HttpServletResponse response, int statusCode, String message) {
        response.setStatus(statusCode);
        response.setContentType("application/json");
        try {
            String json = String.format("{\"error\": \"%s\"}", escapeJson(message));
            response.getWriter().write(json);
        } catch (IOException ioException) {
            logger.error("Failed to write JSON error response", ioException);
        }
    }

    private static String escapeJson(String input) {
        return input == null ? "Unknown error" : input.replace("\"", "\\\"").replace("\n", "").replace("\r", "");
    }
}
