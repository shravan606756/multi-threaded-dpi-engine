package com.shravan.dpi.analysis.dto.response;

import java.util.Map;

public class DpiResult {

    private int totalPackets;
    private int totalConnections;
    private int classified;
    private int unknown;
    private Map<String, Integer> applicationDistribution;
    private Map<String, Integer> topDomains;
    private String outputFile;
    private long executionTimeMs;

    public DpiResult() {
    }

    public DpiResult(int totalPackets, int totalConnections, int classified, int unknown,
                    Map<String, Integer> applicationDistribution, Map<String, Integer> topDomains,
                    String outputFile, long executionTimeMs) {
        this.totalPackets = totalPackets;
        this.totalConnections = totalConnections;
        this.classified = classified;
        this.unknown = unknown;
        this.applicationDistribution = applicationDistribution;
        this.topDomains = topDomains;
        this.outputFile = outputFile;
        this.executionTimeMs = executionTimeMs;
    }

    public int getTotalPackets() {
        return totalPackets;
    }

    public void setTotalPackets(int totalPackets) {
        this.totalPackets = totalPackets;
    }

    public int getTotalConnections() {
        return totalConnections;
    }

    public void setTotalConnections(int totalConnections) {
        this.totalConnections = totalConnections;
    }

    public int getClassified() {
        return classified;
    }

    public void setClassified(int classified) {
        this.classified = classified;
    }

    public int getUnknown() {
        return unknown;
    }

    public void setUnknown(int unknown) {
        this.unknown = unknown;
    }

    public Map<String, Integer> getApplicationDistribution() {
        return applicationDistribution;
    }

    public void setApplicationDistribution(Map<String, Integer> applicationDistribution) {
        this.applicationDistribution = applicationDistribution;
    }

    public Map<String, Integer> getTopDomains() {
        return topDomains;
    }

    public void setTopDomains(Map<String, Integer> topDomains) {
        this.topDomains = topDomains;
    }

    public String getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(String outputFile) {
        this.outputFile = outputFile;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }
}
