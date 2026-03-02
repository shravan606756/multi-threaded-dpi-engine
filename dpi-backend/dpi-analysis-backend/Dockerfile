FROM openjdk:11-jre-slim

# Install dependencies
RUN apt-get update && apt-get install -y \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy application JAR
COPY target/dpi-analysis-backend-1.0.0.jar app.jar

# Create directories for DPI storage
RUN mkdir -p /tmp/dpi/input /tmp/dpi/output

# Expose application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/api/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
