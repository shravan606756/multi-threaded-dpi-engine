# Deep Packet Inspection (DPI) Analysis Platform

A complete end-to-end network traffic analysis system combining a high-performance C++ DPI engine with a Spring Boot orchestration backend and AI-powered insights.

---

## System Overview

This platform provides enterprise-grade network traffic analysis through three integrated components:

1. **C++ DPI Engine** - Multi-threaded packet inspection and classification
2. **Spring Boot Backend** - Orchestration, API layer, and AI integration
3. **Redis Cache** - Job state management and AI response caching

**Future Deployment Architecture:**
```
Azure VM
 ├── Container 1: Backend (Spring Boot + DPI Engine)
 ├── Container 2: Redis (Cache & Job Storage)
 └── Container 3: Frontend (React/Vite + Nginx)
```

---

# Part 1: C++ DPI Engine

## Overview

A high-performance multi-threaded Deep Packet Inspection (DPI) system written in C++. The engine processes PCAP files, performs protocol parsing, extracts TLS SNI, classifies traffic by application and domain, applies rule-based filtering, and produces structured JSON analytics.

This project demonstrates a scalable staged pipeline architecture for network traffic analysis and backend integration.

---

## Network Packet Structure

The engine parses packets layer-by-layer according to standard protocol structure:
```
+---------------------+
| Ethernet Header     |
+---------------------+
| IP Header           |
+---------------------+
| TCP / UDP Header    |
+---------------------+
| Application Payload |
+---------------------+
```

### Parsed Components

- **Ethernet Header**
  - Source MAC
  - Destination MAC
  - EtherType

- **IP Header**
  - Source IP
  - Destination IP
  - Protocol

- **TCP/UDP Header**
  - Source Port
  - Destination Port
  - Sequence/Acknowledgment numbers (TCP)

- **Application Payload**
  - TLS handshake (for SNI extraction)
  - HTTP host header (if applicable)

---

## Five-Tuple Flow Identification

Connections are tracked using the Five-Tuple:
```
(Source IP,
 Destination IP,
 Source Port,
 Destination Port,
 Protocol)
```

This tuple uniquely identifies a network flow.

The engine uses hash-based distribution of the Five-Tuple to:

- Ensure all packets of the same connection are processed by the same worker thread
- Maintain stateful connection tracking
- Prevent race conditions across threads

---

## Architecture

### Processing Pipeline
```
PCAP Reader Thread
        |
Load Balancer Manager
        |
Load Balancer Threads
        |
Fast Path Worker Threads
        |
Output Queue
        |
Writer Thread + Reporting
```

---

### Packet Processing Flow
```
Read Packet
    |
Parse Ethernet/IP/TCP/UDP
    |
Extract Domain (TLS SNI / HTTP Host)
    |
Classify Application
    |
Apply Blocking Rules
    |
DROP or FORWARD
```

---

## Thread Configuration

Parallelism is configurable:
```
Total Fast Path Threads = lbs × fps
```

Parameters:

- `--lbs` → Number of Load Balancer threads
- `--fps` → Fast Path workers per LB

Example:
```
--lbs 4 --fps 4
```

Creates:

- 4 Load Balancers
- 16 Fast Path worker threads

Flow-aware hashing ensures consistent packet-to-thread mapping.

---

## Project Folder Structure
```
Packet_Analyzer/
│
├── include/
│
├── src/
│   ├── connection_tracker.cpp
│   ├── dpi_engine.cpp
│   ├── fast_path.cpp
│   ├── load_balancer.cpp
│   ├── packet_parser.cpp
│   ├── pcap_reader.cpp
│   ├── rule_manager.cpp
│   ├── sni_extractor.cpp
│   ├── types.cpp
│   └── main_dpi.cpp
│
├── test_dpi.pcap
└── README.md
```

---

## Building the DPI Engine

### Windows (PowerShell)

Compile:
```powershell
g++ -std=c++17 -O3 -DNDEBUG -Iinclude -Isrc src/connection_tracker.cpp src/dpi_engine.cpp src/fast_path.cpp src/load_balancer.cpp src/packet_parser.cpp src/pcap_reader.cpp src/rule_manager.cpp src/sni_extractor.cpp src/types.cpp src/main_dpi.cpp -o dpi_engine.exe -pthread
```

Run:
```powershell
.\dpi_engine.exe test_dpi.pcap result.pcap
```

---

### Linux / macOS

Compile:
```bash
g++ -std=c++17 -O3 -DNDEBUG -Iinclude -Isrc \
src/connection_tracker.cpp src/dpi_engine.cpp src/fast_path.cpp \
src/load_balancer.cpp src/packet_parser.cpp src/pcap_reader.cpp \
src/rule_manager.cpp src/sni_extractor.cpp src/types.cpp \
src/main_dpi.cpp -o dpi_engine -pthread
```

Run:
```bash
./dpi_engine test_dpi.pcap result.pcap
```

---

## DPI Engine Usage Examples

Basic analysis:
```
./dpi_engine test_dpi.pcap output.pcap
```

Block applications:
```
./dpi_engine test_dpi.pcap output.pcap --block-app YouTube --block-app TikTok
```

Block domains:
```
./dpi_engine test_dpi.pcap output.pcap --block-domain facebook
```

Block IP:
```
./dpi_engine test_dpi.pcap output.pcap --block-ip 192.168.1.50
```

Custom threading:
```
./dpi_engine test_dpi.pcap output.pcap --lbs 4 --fps 4
```

---

## Runtime Output Format

During execution, the engine prints structured logs including:

- Initialization details
- Thread topology
- Blocking events
- Processing statistics
- Classification results
- Final JSON analytics

Example excerpt:
```
[DPIEngine] Starting DPI engine v1.0
[DPIEngine] Config: load_balancers=2 fps_per_lb=2 total_fp_threads=4
[RuleManager] Blocked app: YouTube
[DPIEngine] Processing: test_dpi.pcap
[FP0] BLOCKED packet: App YouTube

[DPIEngine] Statistics
  total_packets=77
  forwarded_packets=75
  dropped_packets=2
  active_connections=43
```

---

## JSON Output Structure

The engine emits a structured JSON object at completion for backend consumption.

### Schema
```json
{
  "total_packets": number,
  "total_connections": number,
  "classified": number,
  "unknown": number,
  "application_distribution": {
      "<application_name>": count
  },
  "top_domains": {
      "<domain_name>": count
  }
}
```

### Example
```json
{
  "total_packets": 77,
  "total_connections": 43,
  "classified": 22,
  "unknown": 21,
  "application_distribution": {
    "DNS": 4,
    "Twitter/X": 3,
    "YouTube": 1
  },
  "top_domains": {
    "www.youtube.com": 2,
    "www.facebook.com": 2
  }
}
```

This JSON output is consumed by the Spring Boot backend for AI analysis and visualization.

---

## Engineering Concepts Demonstrated (C++ Engine)

- Deep Packet Inspection
- TLS SNI Extraction
- Five-Tuple Flow Tracking
- Multi-threaded Pipeline Architecture
- Producer-Consumer Model
- Thread-safe Queues
- Hash-based Load Distribution

---

# Part 2: Spring Boot Backend

## Overview

A Spring Boot orchestration layer that provides:
- RESTful APIs for asynchronous PCAP analysis
- Native DPI binary execution via ProcessBuilder
- Redis-backed job state management
- AI-powered traffic insights via Groq API (Llama 3)
- Structured JSON parsing and response aggregation

---

## Backend Architecture
```
Client (HTTP Request)
   │
   ▼
Spring Boot Controllers
   │
   ├── AnalysisController
   │     │
   │     ▼
   │   AnalysisService (@Async)
   │     │
   │     ├── DpiEngineService → Executes C++ Binary
   │     │                       ↓
   │     │                  Captures JSON Output
   │     │
   │     ├── JsonParser → Parses DPI Results
   │     │
   │     └── AiService → Groq API (Llama 3)
   │                     ↓
   │                Redis Cache
   │
   └── HealthController
```

---

## Backend Technology Stack

| Component | Technology |
|-----------|-----------|
| Framework | Spring Boot 3.4.3 |
| Language | Java 17 |
| Cache | Redis (Lettuce) |
| AI Provider | Groq (Llama 3.3-70B) |
| Build Tool | Maven |
| Deployment | Docker Compose |

---

## Backend Project Structure
```
dpi-analysis-backend/
│
├── src/main/java/com/shravan/dpi/analysis/
│   ├── config/              # Configuration classes
│   │   ├── DpiEngineConfig.java
│   │   └── RedisConfig.java
│   │
│   ├── controller/          # REST endpoints
│   │   ├── AnalysisController.java
│   │   └── HealthController.java
│   │
│   ├── service/             # Business logic
│   │   ├── AnalysisService.java
│   │   ├── DpiEngineService.java
│   │   └── AiService.java
│   │
│   ├── dto/                 # Data Transfer Objects
│   │   ├── request/
│   │   │   └── AnalysisRequest.java
│   │   └── response/
│   │       ├── DpiResult.java
│   │       ├── AiExplanation.java
│   │       ├── AnalysisResponse.java
│   │       ├── StatusResponse.java
│   │       └── ResultResponse.java
│   │
│   ├── util/                # Utilities
│   │   ├── JsonParser.java
│   │   ├── FileUtil.java
│   │   ├── HashUtil.java
│   │   └── RedisKeys.java
│   │
│   └── exception/           # Error handling
│       ├── DpiExecutionException.java
│       ├── AiServiceException.java
│       ├── JobNotFoundException.java
│       └── GlobalExceptionHandler.java
│
├── src/main/resources/
│   └── application.yml
│
├── docker/
│   ├── Dockerfile
│   └── docker-compose.yml
│
└── pom.xml
```

---

## Getting Started

### Prerequisites

- **Java 17+**
- **Maven 3.6+**
- **Redis 6.0+**
- **DPI Engine Binary** (compiled C++ executable)
- **Groq API Key** (free tier: https://console.groq.com/)

---

### Configuration

Edit `src/main/resources/application.yml`:
```yaml
spring:
  redis:
    host: localhost
    port: 6379

  ai:
    openai:
      api-key: ${GROQ_API_KEY}
      base-url: https://api.groq.com/openai/v1
      chat:
        options:
          model: llama-3.3-70b-versatile
          temperature: 0.7
          max-tokens: 300

dpi:
  engine:
    binary:
      path: /path/to/dpi_engine  # Your compiled C++ binary
    storage:
      input: /tmp/dpi/input
      output: /tmp/dpi/output
```

---

### Build & Run (Local Development)
```bash
# Set Groq API key
export GROQ_API_KEY=gsk_your_api_key_here

# Build
mvn clean package

# Run
java -jar target/dpi-analysis-backend-1.0.0.jar

# Or use Maven
mvn spring-boot:run
```

---

### Docker Deployment
```bash
# Build and start all services
docker-compose up --build

# Run in detached mode
docker-compose up -d

# View logs
docker-compose logs -f dpi-backend

# Stop services
docker-compose down
```

---

## REST API Reference

### 1. Health Check
```http
GET /api/health
```

**Response:**
```json
{
  "status": "UP",
  "timestamp": "2026-03-02T14:30:00"
}
```

---

### 2. Start Analysis (Asynchronous)
```http
POST /api/analysis/run
Content-Type: multipart/form-data

Parameters:
  - file: <pcap-file> (required)
  - lbs: 2 (optional, default: 2)
  - fps: 2 (optional, default: 2)
  - blockApps: YouTube,Netflix (optional)
  - blockDomains: *.facebook.com (optional)
  - blockIps: 192.168.1.1 (optional)
```

**Response (HTTP 202 Accepted):**
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "RUNNING"
}
```

---

### 3. Check Job Status
```http
GET /api/analysis/status/{jobId}
```

**Response:**
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "DONE"
}
```

**Possible Statuses:** `RUNNING`, `DONE`, `FAILED`

---

### 4. Retrieve Analysis Results
```http
GET /api/analysis/result/{jobId}
```

**Response:**
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "dpiResult": {
    "totalPackets": 77,
    "totalConnections": 43,
    "classified": 22,
    "unknown": 21,
    "applicationDistribution": {
      "DNS": 4,
      "Twitter/X": 3,
      "HTTPS": 2,
      "YouTube": 1
    },
    "topDomains": {
      "www.youtube.com": 2,
      "fonts.googleapis.com": 2
    },
    "executionTimeMs": 1250
  },
  "aiExplanation": {
    "summary": "Analyzed 77 packets across 43 connections with a 51% classification success rate.",
    "riskLevel": "MEDIUM",
    "insights": [
      "Successfully classified 51% of network traffic",
      "Highest activity: DNS with 4 packets",
      "Tracked 43 unique network connections"
    ]
  }
}
```

---

## Example Usage

### Using cURL
```bash
# 1. Start analysis
curl -X POST http://localhost:8080/api/analysis/run \
  -F "file=@test_dpi.pcap" \
  -F "lbs=2" \
  -F "fps=2" \
  -F "blockApps=YouTube"

# Response: {"jobId":"abc-123","status":"RUNNING"}

# 2. Check status
curl http://localhost:8080/api/analysis/status/abc-123

# 3. Get results (once status is DONE)
curl http://localhost:8080/api/analysis/result/abc-123
```

---

### Using Python
```python
import requests
import time

# 1. Start analysis
files = {'file': open('test_dpi.pcap', 'rb')}
data = {'lbs': 2, 'fps': 2, 'blockApps': ['YouTube']}

response = requests.post(
    'http://localhost:8080/api/analysis/run',
    files=files,
    data=data
)
job_id = response.json()['jobId']
print(f"Job started: {job_id}")

# 2. Poll for completion
while True:
    status_response = requests.get(
        f'http://localhost:8080/api/analysis/status/{job_id}'
    )
    status = status_response.json()['status']
    print(f"Status: {status}")
    
    if status == 'DONE':
        break
    elif status == 'FAILED':
        print("Analysis failed!")
        exit(1)
    
    time.sleep(2)

# 3. Get results
result = requests.get(
    f'http://localhost:8080/api/analysis/result/{job_id}'
)
print(result.json())
```

---

## Key Backend Features

### Asynchronous Processing
- Uses Spring's `@Async` for non-blocking execution
- Returns Job ID immediately
- Client polls for status and results

### Native Binary Integration
- Executes C++ DPI engine via `ProcessBuilder`
- Captures stdout for JSON output
- Handles process timeouts and errors

### Redis Caching
- **Job State:** 24-hour TTL
- **AI Responses:** 1-hour TTL (cache key based on DPI result hash)
- Prevents redundant AI API calls

### AI-Powered Insights
- **Provider:** Groq (Llama 3.3-70B-Versatile)
- **Fallback:** Rule-based analysis if AI unavailable
- **Output:** Traffic summary, risk assessment, key insights

### Error Handling
- Global exception handler (`@ControllerAdvice`)
- Consistent JSON error responses
- Detailed logging for debugging

---

## Troubleshooting

### DPI Binary Not Found
```
Error: Failed to execute DPI engine: Cannot run program
```
**Solution:** Ensure `dpi.engine.binary.path` points to the correct executable with execute permissions.

---

### Redis Connection Failed
```
Error: Unable to connect to Redis
```
**Solution:** 
1. Verify Redis is running: `redis-cli ping`
2. Check host/port in `application.yml`

---

### Job Not Found
```
Error: Job not found: abc-123
```
**Solution:** Job expired (24-hour TTL) or incorrect Job ID.

---

### Groq API Error
```
Error: AI service unavailable
```
**Solution:**
1. Verify `GROQ_API_KEY` is set
2. Check API key permissions at https://console.groq.com/
3. Backend falls back to rule-based analysis

---

## Azure VM Deployment (Future)

### Planned Container Architecture
```
Azure VM (Ubuntu 22.04 LTS)
├── Container 1: dpi-backend
│   ├── Spring Boot 3.4.3
│   ├── DPI Engine Binary
│   └── Port: 8080
│
├── Container 2: redis
│   ├── Redis 7-alpine
│   └── Port: 6379
│
└── Container 3: frontend (Future)
    ├── React + Vite
    ├── Nginx
    └── Port: 80/443
```

### Deployment Steps (Azure)
```bash
# 1. SSH into Azure VM
ssh azureuser@your-vm-ip

# 2. Clone repository
git clone https://github.com/your-username/dpi-analysis-platform.git
cd dpi-analysis-platform

# 3. Set environment variables
export GROQ_API_KEY=gsk_your_key_here

# 4. Build and deploy
docker-compose up -d

# 5. Verify services
docker-compose ps
curl http://localhost:8080/api/health
```

---

## System Integration Flow
```
┌─────────────┐
│   Client    │
│ (Web/CLI)   │
└──────┬──────┘
       │
       │ HTTP POST (PCAP + params)
       ▼
┌─────────────────────────────────┐
│   Spring Boot Backend           │
│   ┌─────────────────────────┐   │
│   │  AnalysisController     │   │
│   └───────────┬─────────────┘   │
│               │                 │
│               ▼                 │
│   ┌─────────────────────────┐   │
│   │  AnalysisService        │   │
│   │  (@Async Background)    │   │
│   └───────────┬─────────────┘   │
│               │                 │
│       ┌───────┴───────┐         │
│       │               │         │
│       ▼               ▼         │
│  ┌─────────┐   ┌──────────┐    │
│  │   DPI   │   │  Redis   │    │
│  │ Engine  │   │  Cache   │    │
│  └────┬────┘   └──────────┘    │
│       │                         │
│       │ JSON Output             │
│       ▼                         │
│  ┌──────────┐                   │
│  │ JsonParser│                  │
│  └────┬─────┘                   │
│       │                         │
│       ▼                         │
│  ┌──────────┐                   │
│  │AiService │──────────────────┼─→ Groq API
│  │(Llama 3) │                   │   (Llama 3.3-70B)
│  └────┬─────┘                   │
│       │                         │
│       ▼                         │
│  ResultResponse                 │
│  (DPI + AI combined)            │
└─────────────────────────────────┘
       │
       │ HTTP GET /result/{jobId}
       ▼
┌─────────────┐
│   Client    │
└─────────────┘
```

---

## Engineering Concepts Demonstrated

### C++ DPI Engine
- Multi-threaded packet processing
- Five-tuple flow tracking
- TLS SNI extraction
- Lock-free queues
- Hash-based load distribution

### Spring Boot Backend
- Asynchronous processing (`@Async`)
- Native process execution (`ProcessBuilder`)
- Redis caching strategy
- REST API design
- AI model integration (Groq/Llama 3)
- Structured JSON parsing
- Global exception handling

---

## Contributing

This project demonstrates end-to-end integration of:
- High-performance C++ systems programming
- Spring Boot microservices architecture
- AI/LLM API integration
- Containerized deployment

---

## License

This project is for educational and portfolio purposes.

---

## Author

**Shravan Singh Udawat**

- **C++ DPI Engine**: Multi-threaded packet inspection and classification
- **Spring Boot Backend**: Orchestration, API layer, and AI integration
- **System Architecture**: End-to-end platform design

**LinkedIn:** [Your LinkedIn]  
**GitHub:** [Your GitHub]  
**Email:** [Your Email]

---

## Quick Start Summary
```bash
# 1. Compile C++ DPI Engine
cd Packet_Analyzer
g++ -std=c++17 -O3 src/*.cpp -o dpi_engine -pthread

# 2. Set Groq API Key
export GROQ_API_KEY=gsk_your_key_here

# 3. Start Redis
docker run -d -p 6379:6379 redis:7-alpine

# 4. Configure Backend
# Edit application.yml with DPI binary path

# 5. Run Backend
cd dpi-analysis-backend
mvn spring-boot:run

# 6. Test
curl -X POST http://localhost:8080/api/analysis/run \
  -F "file=@test.pcap" -F "lbs=2" -F "fps=2"
```

---

**Status:** Production-Ready for Azure VM Deployment  
**Last Updated:** March 2026
