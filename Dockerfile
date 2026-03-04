# ========================================
# Stage 1: Build C++ DPI Engine (The Ferrari)
# ========================================
FROM ubuntu:22.04 AS cpp-builder
RUN apt-get update && apt-get install -y g++ cmake make libpcap-dev && rm -rf /var/lib/apt/lists/*
WORKDIR /build

# Points to your specific nested C++ source folder
COPY dpi-engine/Packet_analyzer-main/Packet_analyzer-main/ /build/

RUN mkdir -p build && cd build && cmake .. && make && chmod +x dpi_engine

# ========================================
# Stage 2: Build Java 17 Backend
# ========================================
FROM maven:3.9-eclipse-temurin-21 AS java-builder
WORKDIR /app

# Points to your specific nested backend folder
COPY dpi-backend/dpi-analysis-backend/pom.xml .
RUN mvn dependency:go-offline

COPY dpi-backend/dpi-analysis-backend/src ./src
RUN mvn clean package -Dmaven.test.skip=true

# ========================================
# Stage 3: Runtime Container
# ========================================
FROM eclipse-temurin:21-jre

# Install the packet capture library needed for the engine to run
RUN apt-get update && apt-get install -y libpcap0.8 && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# FIX: Copy to the path Java expects: /usr/local/bin/
COPY --from=cpp-builder /build/build/dpi_engine /usr/local/bin/dpi_engine
COPY --from=java-builder /app/target/*.jar /app/app.jar

# FIX: Grant execution permissions to the new path
RUN mkdir -p /app/storage/input /app/storage/output && chmod +x /usr/local/bin/dpi_engine

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]