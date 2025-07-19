#!/bin/bash

# Build script for GraalVM native image

set -e

echo "Building GraalVM native image..."

# Clean previous builds
echo "Cleaning previous builds..."
mvn clean

# Build the native image
echo "Building native image with GraalVM..."
mvn package -Pnative -DskipTests --no-transfer-progress

echo "Native image build completed!"
echo "Executable location: target/sse-jetty-server-native"

# Test the native executable
if [ -f "target/sse-jetty-server-native" ]; then
    echo "Native executable created successfully!"
    ls -la target/sse-jetty-server-native
else
    echo "ERROR: Native executable not found!"
    exit 1
fi
