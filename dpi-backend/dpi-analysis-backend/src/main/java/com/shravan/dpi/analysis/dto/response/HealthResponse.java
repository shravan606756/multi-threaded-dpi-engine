package com.shravan.dpi.analysis.dto.response;

import java.time.LocalDateTime;

public class HealthResponse {

    private String status;
    private LocalDateTime timestamp;

    public HealthResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public HealthResponse(String status) {
        this.status = status;
        this.timestamp = LocalDateTime.now();
    }

    public HealthResponse(String status, LocalDateTime timestamp) {
        this.status = status;
        this.timestamp = timestamp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
