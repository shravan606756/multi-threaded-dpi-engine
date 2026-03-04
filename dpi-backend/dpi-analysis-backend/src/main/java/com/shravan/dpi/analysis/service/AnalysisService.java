package com.shravan.dpi.analysis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shravan.dpi.analysis.config.DpiEngineConfig;
import com.shravan.dpi.analysis.dto.request.AnalysisRequest;
import com.shravan.dpi.analysis.dto.response.AiExplanation;
import com.shravan.dpi.analysis.dto.response.DpiResult;
import com.shravan.dpi.analysis.dto.response.ResultResponse;
import com.shravan.dpi.analysis.exception.JobNotFoundException;
import com.shravan.dpi.analysis.util.FileUtil;
import com.shravan.dpi.analysis.util.JsonParser;
import com.shravan.dpi.analysis.util.RedisKeys;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.io.File;
import java.io.IOException;

@Service
public class AnalysisService {

    private final DpiEngineService dpiEngineService;
    private final AiService aiService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${dpi.engine.storage.input}")
    private String inputDir;

    @Value("${dpi.engine.storage.output}")
    private String outputDir;

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_DONE = "DONE";
    private static final String STATUS_FAILED = "FAILED";

    public AnalysisService(DpiEngineService dpiEngineService,
                           AiService aiService,
                           RedisTemplate<String, Object> redisTemplate) {
        this.dpiEngineService = dpiEngineService;
        this.aiService = aiService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
    }

    public String startAnalysis(MultipartFile file, AnalysisRequest request) throws IOException {
        // Ensure directories exist
        Files.createDirectories(Paths.get(inputDir));
        Files.createDirectories(Paths.get(outputDir));

        String jobId = UUID.randomUUID().toString();

        // Store initial status
        String statusKey = RedisKeys.jobStatus(jobId);
        redisTemplate.opsForValue().set(statusKey, STATUS_RUNNING, 24, TimeUnit.HOURS);

        // Save uploaded file
        String filePath = FileUtil.saveUploadedFile(file, inputDir);

        // Trigger async processing
        processInBackground(jobId, filePath, request);

        return jobId;
    }

    @Async
    public void processInBackground(String jobId, String filePath, AnalysisRequest request) {
        String statusKey = RedisKeys.jobStatus(jobId);
        String resultKey = RedisKeys.jobResult(jobId);

        try {
            // ✅ Execute DPI engine (gets binary path from config internally)
            String dpiJsonOutput = dpiEngineService.executeDpi(filePath, request);

            // Parse and process results
            DpiResult dpiResult = JsonParser.parseDpiResult(dpiJsonOutput);
            AiExplanation aiExplanation = aiService.generateExplanation(dpiResult);

            ResultResponse resultResponse = new ResultResponse(jobId, dpiResult, aiExplanation);

            // Store results
            redisTemplate.opsForValue().set(resultKey, resultResponse, 24, TimeUnit.HOURS);
            redisTemplate.opsForValue().set(statusKey, STATUS_DONE, 24, TimeUnit.HOURS);

        } catch (Exception e) {
            redisTemplate.opsForValue().set(statusKey, STATUS_FAILED, 24, TimeUnit.HOURS);
            System.err.println("Analysis failed for job " + jobId + ": " + e.getMessage());
            e.printStackTrace();
        } finally {
            FileUtil.deleteFile(filePath);
        }
    }

    public String getStatus(String jobId) {
        String statusKey = RedisKeys.jobStatus(jobId);
        Object status = redisTemplate.opsForValue().get(statusKey);

        if (status == null) {
            throw new JobNotFoundException("Job not found: " + jobId);
        }

        return status.toString();
    }

    public ResultResponse getResult(String jobId) {
        String statusKey = RedisKeys.jobStatus(jobId);
        Object status = redisTemplate.opsForValue().get(statusKey);

        if (status == null) {
            throw new JobNotFoundException("Job not found: " + jobId);
        }

        if (!STATUS_DONE.equals(status.toString())) {
            throw new RuntimeException("Job is not complete yet. Current status: " + status);
        }

        String resultKey = RedisKeys.jobResult(jobId);
        Object result = redisTemplate.opsForValue().get(resultKey);

        if (result == null) {
            throw new RuntimeException("Result not found for job: " + jobId);
        }

        try {
            return objectMapper.convertValue(result, ResultResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse result: " + e.getMessage(), e);
        }
    }
}