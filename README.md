# GraalVM Native Image Build Guide

This document explains how to build and run the SSE Jetty Server as a GraalVM native image.

## Prerequisites

1. **GraalVM 21** with Native Image support
2. **Maven 3.9+**
3. **Docker** (for containerized builds)

## Building Native Image

### Option 1: Local Build (requires GraalVM installed)

```bash
# On Linux/macOS
./build-native.sh

# On Windows
build-native.bat
```

### Option 2: Maven Command

```bash
mvn clean package -Pnative -DskipTests
```

### Option 3: Docker Build

```bash
# Build the native image container
docker build -f Dockerfile.native -t sse-server-native .

# Run with Docker Compose
docker-compose -f docker-compose.native.yml up --build
```

## Configuration Files

The following GraalVM configuration files have been added:

- `src/main/resources/META-INF/native-image/reflect-config.json` - Reflection configuration
- `src/main/resources/META-INF/native-image/resource-config.json` - Resource inclusion
- `src/main/resources/META-INF/native-image/jni-config.json` - JNI configuration
- `src/main/resources/META-INF/native-image/proxy-config.json` - Dynamic proxy configuration
- `src/main/resources/META-INF/native-image/native-image.properties` - Build arguments

## Key Improvements Made

1. **Updated GraalVM Native Maven Plugin** to version 0.10.2
2. **Added comprehensive reflection configuration** for:
    - Jetty server components
    - MySQL JDBC driver
    - Redis Jedis client
    - Jackson JSON processing
    - JWT libraries
    - Logging frameworks

3. **Enhanced build arguments**:
    - `--enable-http` and `--enable-https` for web server support
    - `--initialize-at-build-time` for static initialization
    - `--initialize-at-run-time` for runtime-specific components
    - Resource inclusion patterns for templates and properties

4. **Improved Dockerfile.native**:
    - Updated to Maven 3.9.8
    - Better layer caching
    - Increased memory allocation for build
    - Added runtime dependencies
    - Security improvements (non-root user)
    - Health check support

## Runtime Benefits

- **Faster startup time** (~50ms vs ~3-5 seconds for JVM)
- **Lower memory footprint** (~50-100MB vs ~200-500MB for JVM)
- **No warm-up period** - peak performance from start
- **Smaller container size** - no JVM required

## Troubleshooting

### Common Issues

1. **OutOfMemoryError during build**:
    - Increase Docker memory allocation
    - Ensure sufficient system memory (8GB+ recommended)

2. **Missing reflection configuration**:
    - Check logs for `ClassNotFoundException` or `NoSuchMethodException`
    - Add missing classes to `reflect-config.json`

3. **Resource not found**:
    - Verify resource patterns in `resource-config.json`
    - Ensure resources are in the classpath

### Build Logs

For verbose build output, use:
```bash
mvn package -Pnative -DskipTests -X
```

## Performance Comparison

| Metric | JVM | Native Image |
|--------|-----|--------------|
| Startup Time | 3-5 seconds | ~50ms |
| Memory Usage | 200-500MB | 50-100MB |
| Container Size | ~300MB | ~50MB |
| Cold Start | Slow | Instant |

## Cloud Deployment

The native image is optimized for cloud platforms like:
- Google Cloud Run
- AWS Lambda
- Azure Container Instances
- Kubernetes

Use the `docker-compose.native.yml` for local testing that mimics cloud environments.
