package com.shravan.dpi.analysis.dto.response;

public class AnalysisResponse {

    private String jobId;
    private String status;

    public AnalysisResponse() {
    }

    public AnalysisResponse(String jobId, String status) {
        this.jobId = jobId;
        this.status = status;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
