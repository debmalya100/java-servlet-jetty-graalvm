FROM maven:3.9.8-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy pom.xml first for better Docker layer caching
COPY pom.xml .

# Download dependencies
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests --no-transfer-progress

# Runtime stage
FROM openjdk:21-jdk-slim

# Install certificates and networking tools
RUN apt-get update && apt-get install -y \
    ca-certificates \
    curl \
    && rm -rf /var/lib/apt/lists/* \
    && update-ca-certificates

WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /app/target/sse-jetty-server-1.0-SNAPSHOT.jar app.jar

# Create logs directory with proper permissions
RUN mkdir -p logs && chmod 755 logs

# Set JVM options optimized for Cloud Run
ENV JAVA_OPTS="-Xmx1500m -Xms512m \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=100 \
    -Djava.security.egd=file:/dev/./urandom \
    -Dnetworkaddress.cache.ttl=30 \
    -Dnetworkaddress.cache.negative.ttl=10 \
    -Dfile.encoding=UTF-8 \
    -Duser.timezone=UTC"

# Cloud Run will set the PORT environment variable
# Default to 8080 if not set (for local testing)
ENV PORT=8080

# Use exec form for proper signal handling
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]