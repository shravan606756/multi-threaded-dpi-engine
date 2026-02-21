# Multi-Threaded Deep Packet Inspection (DPI) Engine

A high-performance multi-threaded Deep Packet Inspection (DPI) system written in C++. The engine processes PCAP files, performs protocol parsing, extracts TLS SNI, classifies traffic by application and domain, applies rule-based filtering, and produces structured JSON analytics.

This project demonstrates a scalable staged pipeline architecture for network traffic analysis and backend integration.

---

## Overview

The DPI engine provides:

- Ethernet/IP/TCP/UDP protocol parsing
- TLS Server Name Indication (SNI) extraction
- Application-level traffic classification
- Rule-based blocking (application, domain, IP)
- Flow-aware multi-threaded packet processing
- Structured JSON analytics output
- Filtered PCAP generation

The engine operates in batch mode using PCAP input files.

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

## Building

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

## Usage Examples

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

The engine emits a structured JSON object at completion.

### Schema

```
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

This output is intended for automated backend consumption.

---

## Future Integrations

Planned extensions:

- Spring Boot orchestration layer
- AI-assisted traffic analysis
- REST API wrapper
- Web dashboard visualization
- Cloud deployment support
- Real-time live packet capture mode

---

## Important Notes

- Classification reflects detected traffic, not only forwarded packets.
- Blocking affects forwarding but classification statistics remain visible.
- Uneven thread utilization is expected for small PCAP datasets.
- Current implementation operates in batch PCAP mode.

---

## Engineering Concepts Demonstrated

- Deep Packet Inspection
- TLS SNI Extraction
- Five-Tuple Flow Tracking
- Multi-threaded Pipeline Architecture
- Producer-Consumer Model
- Thread-safe Queues
- Hash-based Load Distribution

---

## Author

Shravan Singh Udawat