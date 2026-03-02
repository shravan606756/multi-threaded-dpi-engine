package com.shravan.dpi.analysis.controller;

import com.shravan.dpi.analysis.dto.request.AnalysisRequest;
import com.shravan.dpi.analysis.dto.response.*;
import com.shravan.dpi.analysis.service.AiService;
import com.shravan.dpi.analysis.service.AnalysisService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;

@RestController
@RequestMapping("/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;
    private final AiService aiService;

    public AnalysisController(AnalysisService analysisService , AiService aiService) {
        this.analysisService = analysisService;
        this.aiService = aiService;
    }

    @PostMapping("/run")
    public ResponseEntity<AnalysisResponse> runAnalysis(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "lbs", required = true) Integer lbs,
            @RequestParam(value = "fps", required = true) Integer fps,
            @RequestParam(value = "blockApps", required = false) String[] blockApps,
            @RequestParam(value = "blockDomains", required = false) String[] blockDomains,
            @RequestParam(value = "blockIps", required = false) String[] blockIps
    ) {
        try {
            // Build request object
            AnalysisRequest request = new AnalysisRequest();
            request.setLbs(lbs);
            request.setFps(fps);
            
            if (blockApps != null) {
                request.setBlockApps(java.util.Arrays.asList(blockApps));
            }
            if (blockDomains != null) {
                request.setBlockDomains(java.util.Arrays.asList(blockDomains));
            }
            if (blockIps != null) {
                request.setBlockIps(java.util.Arrays.asList(blockIps));
            }

            // Start analysis
            String jobId = analysisService.startAnalysis(file, request);

            AnalysisResponse response = new AnalysisResponse(jobId, "RUNNING");
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);

        } catch (Exception e) {
            throw new RuntimeException("Failed to start analysis: " + e.getMessage(), e);
        }
    }

    @GetMapping("/status/{jobId}")
    public ResponseEntity<StatusResponse> getStatus(@PathVariable String jobId) {
        String status = analysisService.getStatus(jobId);
        StatusResponse response = new StatusResponse(jobId, status);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/result/{jobId}")
    public ResponseEntity<ResultResponse> getResult(@PathVariable String jobId) {
        ResultResponse result = analysisService.getResult(jobId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/test-ai")
    public ResponseEntity<String> testAi() {
        DpiResult testResult = new DpiResult();
        testResult.setTotalPackets(100);
        testResult.setTotalConnections(50);
        testResult.setClassified(80);
        testResult.setUnknown(20);

        AiExplanation result = aiService.generateExplanation(testResult);
        return ResponseEntity.ok(result.getSummary());
    }
}
