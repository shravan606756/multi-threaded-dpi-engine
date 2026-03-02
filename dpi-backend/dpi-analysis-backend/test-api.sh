#!/bin/bash

# DPI Analysis Backend - API Test Script
# Usage: ./test-api.sh <pcap-file>

BASE_URL="http://localhost:8080/api"
PCAP_FILE="${1:-sample.pcap}"

echo "🚀 DPI Analysis Backend - API Test"
echo "===================================="
echo ""

# Check if file exists
if [ ! -f "$PCAP_FILE" ]; then
    echo "❌ Error: PCAP file not found: $PCAP_FILE"
    echo "Usage: ./test-api.sh <pcap-file>"
    exit 1
fi

# 1. Health Check
echo "1️⃣  Checking health..."
HEALTH=$(curl -s "$BASE_URL/health")
echo "Response: $HEALTH"
echo ""

# 2. Start Analysis
echo "2️⃣  Starting analysis..."
RESPONSE=$(curl -s -X POST "$BASE_URL/analysis/run" \
    -F "file=@$PCAP_FILE" \
    -F "lbs=1000" \
    -F "fps=5000" \
    -F "blockApps=YouTube" \
    -F "blockApps=Netflix")

echo "Response: $RESPONSE"
JOB_ID=$(echo $RESPONSE | grep -o '"jobId":"[^"]*"' | cut -d'"' -f4)
echo "Job ID: $JOB_ID"
echo ""

if [ -z "$JOB_ID" ]; then
    echo "❌ Failed to get Job ID"
    exit 1
fi

# 3. Poll Status
echo "3️⃣  Polling status..."
STATUS="RUNNING"
ATTEMPTS=0
MAX_ATTEMPTS=30

while [ "$STATUS" == "RUNNING" ] && [ $ATTEMPTS -lt $MAX_ATTEMPTS ]; do
    sleep 2
    STATUS_RESPONSE=$(curl -s "$BASE_URL/analysis/status/$JOB_ID")
    STATUS=$(echo $STATUS_RESPONSE | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
    echo "Status: $STATUS (attempt $((ATTEMPTS + 1))/$MAX_ATTEMPTS)"
    ATTEMPTS=$((ATTEMPTS + 1))
done
echo ""

# 4. Get Results
if [ "$STATUS" == "DONE" ]; then
    echo "4️⃣  Fetching results..."
    RESULTS=$(curl -s "$BASE_URL/analysis/result/$JOB_ID")
    echo "Results:"
    echo "$RESULTS" | python3 -m json.tool 2>/dev/null || echo "$RESULTS"
    echo ""
    echo "✅ Analysis completed successfully!"
elif [ "$STATUS" == "FAILED" ]; then
    echo "❌ Analysis failed"
    exit 1
else
    echo "⏱️  Analysis timeout"
    exit 1
fi
