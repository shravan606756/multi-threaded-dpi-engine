package com.shravan.dpi.analysis.dto.response;

import java.util.List;

public class AiExplanation {

    private String summary;
    private String riskLevel;
    private List<String> insights;

    public AiExplanation() {
    }

    public AiExplanation(String summary, String riskLevel, List<String> insights) {
        this.summary = summary;
        this.riskLevel = riskLevel;
        this.insights = insights;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public List<String> getInsights() {
        return insights;
    }

    public void setInsights(List<String> insights) {
        this.insights = insights;
    }
}
