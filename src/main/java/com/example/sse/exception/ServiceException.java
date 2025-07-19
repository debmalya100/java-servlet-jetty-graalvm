package com.example.sse.exception;

/**
 * Generic service exception for all service-level errors including
 * database, cache, configuration, JWT processing, and resource loading failures.
 * <p>
 * This exception includes a service type to help with error categorization
 * and appropriate HTTP status code mapping in the GlobalExceptionHandler.
 */
public class ServiceException extends RuntimeException {

    /**
     * Enum to categorize different types of service errors
     */
    public enum ServiceType {
        DATABASE("Database"), CACHE("Cache"), CONFIGURATION("Configuration"), JWT("JWT"), RESOURCE("Resource"), GENERAL("Service");

        private final String displayName;

        ServiceType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private final ServiceType serviceType;

    /**
     * Constructs a ServiceException with service type and message.
     *
     * @param serviceType the type of service that failed
     * @param message     the detail message
     */
    public ServiceException(ServiceType serviceType, String message) {
        super(message);
        this.serviceType = serviceType;
    }

    /**
     * Constructs a ServiceException with service type, message and cause.
     *
     * @param serviceType the type of service that failed
     * @param message     the detail message
     * @param cause       the cause of the error
     */
    public ServiceException(ServiceType serviceType, String message, Throwable cause) {
        super(message, cause);
        this.serviceType = serviceType;
    }

    /**
     * Gets the service type that caused this exception.
     *
     * @return the service type
     */
    public ServiceType getServiceType() {
        return serviceType;
    }

    /**
     * Gets a formatted error message including the service type.
     *
     * @return formatted error message
     */
    public String getFormattedMessage() {
        return serviceType.getDisplayName() + " error: " + getMessage();
    }
}
