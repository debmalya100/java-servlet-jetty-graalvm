package com.example.sse.exception;

public class HttpException extends RuntimeException {
    private final int statusCode;

    public HttpException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
