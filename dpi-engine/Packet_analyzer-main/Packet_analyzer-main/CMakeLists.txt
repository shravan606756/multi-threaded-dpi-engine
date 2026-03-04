cmake_minimum_required(VERSION 3.16)
project(DpiEngine VERSION 1.0)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

# ✅ CORRECT: Build the FULL DPI engine
set(SOURCES
    src/main_dpi.cpp
    src/dpi_engine.cpp
    src/fast_path.cpp
    src/load_balancer.cpp
    src/connection_tracker.cpp
    src/rule_manager.cpp
    src/packet_parser.cpp
    src/pcap_reader.cpp
    src/sni_extractor.cpp
    src/types.cpp
)

# Header files location
include_directories(${CMAKE_SOURCE_DIR}/include)

# Create the DPI engine executable
add_executable(dpi_engine ${SOURCES})

# Link pthread (required for multi-threading)
target_link_libraries(dpi_engine pthread)

# For Linux, you might need to link libpcap if dynamically loaded
# Uncomment if you get "libpcap not found" errors
# find_library(PCAP_LIBRARY pcap)
# target_link_libraries(dpi_engine ${PCAP_LIBRARY})