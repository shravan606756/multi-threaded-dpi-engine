package com.shravan.dpi.analysis.dto.request;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

public class AnalysisRequest {

    @NotNull(message = "LBS (Load Balancing Strategy) cannot be null")
    @Min(value = 1, message = "LBS must be at least 1")
    private Integer lbs;

    @NotNull(message = "FPS (Flow Per Second) cannot be null")
    @Min(value = 1, message = "FPS must be at least 1")
    private Integer fps;

    private List<String> blockApps;
    private List<String> blockDomains;
    private List<String> blockIps;

    public AnalysisRequest() {
        this.blockApps = new ArrayList<>();
        this.blockDomains = new ArrayList<>();
        this.blockIps = new ArrayList<>();
    }

    public AnalysisRequest(Integer lbs, Integer fps, List<String> blockApps, 
                          List<String> blockDomains, List<String> blockIps) {
        this.lbs = lbs;
        this.fps = fps;
        this.blockApps = blockApps != null ? blockApps : new ArrayList<>();
        this.blockDomains = blockDomains != null ? blockDomains : new ArrayList<>();
        this.blockIps = blockIps != null ? blockIps : new ArrayList<>();
    }

    public Integer getLbs() {
        return lbs;
    }

    public void setLbs(Integer lbs) {
        this.lbs = lbs;
    }

    public Integer getFps() {
        return fps;
    }

    public void setFps(Integer fps) {
        this.fps = fps;
    }

    public List<String> getBlockApps() {
        return blockApps;
    }

    public void setBlockApps(List<String> blockApps) {
        this.blockApps = blockApps;
    }

    public List<String> getBlockDomains() {
        return blockDomains;
    }

    public void setBlockDomains(List<String> blockDomains) {
        this.blockDomains = blockDomains;
    }

    public List<String> getBlockIps() {
        return blockIps;
    }

    public void setBlockIps(List<String> blockIps) {
        this.blockIps = blockIps;
    }
}
