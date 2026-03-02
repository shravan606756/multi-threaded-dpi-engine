package com.shravan.dpi.analysis.dto.response;

public class ResultResponse {

    private String jobId;
    private DpiResult dpiResult;
    private AiExplanation aiExplanation;

    public ResultResponse() {
    }

    public ResultResponse(String jobId, DpiResult dpiResult, AiExplanation aiExplanation) {
        this.jobId = jobId;
        this.dpiResult = dpiResult;
        this.aiExplanation = aiExplanation;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public DpiResult getDpiResult() {
        return dpiResult;
    }

    public void setDpiResult(DpiResult dpiResult) {
        this.dpiResult = dpiResult;
    }

    public AiExplanation getAiExplanation() {
        return aiExplanation;
    }

    public void setAiExplanation(AiExplanation aiExplanation) {
        this.aiExplanation = aiExplanation;
    }
}
