#!/bin/bash

# DPI Analysis Backend - Build & Run Script

echo "🔨 Building DPI Analysis Backend..."
echo "===================================="

# Clean and build
mvn clean package -DskipTests

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Build successful!"
    echo ""
    echo "📦 JAR location: target/dpi-analysis-backend-1.0.0.jar"
    echo ""
    echo "🚀 Starting application..."
    echo ""
    java -jar target/dpi-analysis-backend-1.0.0.jar
else
    echo ""
    echo "❌ Build failed!"
    exit 1
fi
