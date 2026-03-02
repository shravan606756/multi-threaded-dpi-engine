package com.shravan.dpi.analysis.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DpiEngineConfig {

    @Value("${dpi.engine.binary.path:/usr/local/bin/dpi_engine}")
    private String binaryPath;

    @Value("${dpi.engine.storage.input:/tmp/dpi/input}")
    private String inputStoragePath;

    @Value("${dpi.engine.storage.output:/tmp/dpi/output}")
    private String outputStoragePath;

    @Value("${dpi.engine.default.threads:4}")
    private int defaultThreads;

    @Value("${dpi.engine.default.lbs:1000}")
    private int defaultLbs;

    @Value("${dpi.engine.default.fps:5000}")
    private int defaultFps;

    public String getBinaryPath() {
        return binaryPath;
    }

    public void setBinaryPath(String binaryPath) {
        this.binaryPath = binaryPath;
    }

    public String getInputStoragePath() {
        return inputStoragePath;
    }

    public void setInputStoragePath(String inputStoragePath) {
        this.inputStoragePath = inputStoragePath;
    }

    public String getOutputStoragePath() {
        return outputStoragePath;
    }

    public void setOutputStoragePath(String outputStoragePath) {
        this.outputStoragePath = outputStoragePath;
    }

    public int getDefaultThreads() {
        return defaultThreads;
    }

    public void setDefaultThreads(int defaultThreads) {
        this.defaultThreads = defaultThreads;
    }

    public int getDefaultLbs() {
        return defaultLbs;
    }

    public void setDefaultLbs(int defaultLbs) {
        this.defaultLbs = defaultLbs;
    }

    public int getDefaultFps() {
        return defaultFps;
    }

    public void setDefaultFps(int defaultFps) {
        this.defaultFps = defaultFps;
    }
}
