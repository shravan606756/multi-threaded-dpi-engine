package com.shravan.dpi.analysis.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.shravan.dpi.analysis.dto.response.DpiResult;

public class JsonParser {

    private static final ObjectMapper objectMapper = new ObjectMapper()
            // Tell Jackson to map "total_packets" to "totalPackets"
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            // Ignore extra fields if C++ outputs something Java doesn't expect
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private JsonParser() {
        // Private constructor to prevent instantiation
    }

    public static DpiResult parseDpiResult(String jsonString) {
        try {
            return objectMapper.readValue(jsonString, DpiResult.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse DPI result JSON: " + e.getMessage(), e);
        }
    }

    public static String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert object to JSON: " + e.getMessage(), e);
        }
    }
}