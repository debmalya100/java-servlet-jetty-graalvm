@echo off

REM Build script for GraalVM native image on Windows

echo Building GraalVM native image...

REM Clean previous builds
echo Cleaning previous builds...
mvn clean

REM Build the native image
echo Building native image with GraalVM...
mvn package -Pnative -DskipTests --no-transfer-progress

echo Native image build completed!
echo Executable location: target\sse-jetty-server-native.exe

REM Test the native executable
if exist "target\sse-jetty-server-native.exe" (
    echo Native executable created successfully!
    dir target\sse-jetty-server-native.exe
) else (
    echo ERROR: Native executable not found!
    exit /b 1
)
