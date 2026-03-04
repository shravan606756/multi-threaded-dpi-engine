package com.shravan.dpi.analysis.service;

import com.shravan.dpi.analysis.config.DpiEngineConfig;
import com.shravan.dpi.analysis.dto.request.AnalysisRequest;
import com.shravan.dpi.analysis.exception.DpiExecutionException;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@Service
public class DpiEngineService {

    private final DpiEngineConfig config;

    public DpiEngineService(DpiEngineConfig config) {
        this.config = config;
    }

    // ✅ Remove enginePath parameter - get it from config
    public String executeDpi(String inputFilePath, AnalysisRequest request) {
        try {
            String inputFileName = new File(inputFilePath).getName();
            String outputFileName = "output_" + inputFileName;

            String absoluteInputPath = new File(inputFilePath).getAbsolutePath();
            String absoluteOutputPath = new File(config.getOutputStoragePath(), outputFileName).getAbsolutePath();

            // ✅ Get engine path from config
            String enginePath = config.getBinaryPath();

            List<String> command = buildCommand(enginePath, absoluteInputPath, absoluteOutputPath, request);

            System.out.println("EXECUTING C++ COMMAND: " + String.join(" ", command));

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            StringBuilder output = new StringBuilder();
            StringBuilder jsonOutput = new StringBuilder();
            boolean inJsonBlock = false;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");

                    if (line.trim().equals("{")) {
                        inJsonBlock = true;
                    }

                    if (inJsonBlock) {
                        jsonOutput.append(line).append("\n");
                    }
                }
            }

            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new DpiExecutionException(
                        "DPI engine failed with exit code " + exitCode +
                                "\nCommand: " + String.join(" ", command) +
                                "\nOutput: " + output.toString()
                );
            }

            String jsonResult = jsonOutput.toString().trim();
            if (jsonResult.isEmpty()) {
                throw new DpiExecutionException(
                        "No JSON output found from DPI engine. Output: " + output.toString()
                );
            }

            return jsonResult;

        } catch (DpiExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new DpiExecutionException("Failed to execute DPI engine: " + e.getMessage(), e);
        }
    }

    private List<String> buildCommand(String enginePath, String inputFile, String outputFile, AnalysisRequest request) {
        List<String> command = new ArrayList<>();

        // Binary path
        command.add(enginePath);

        // Positional arguments
        command.add(inputFile);
        command.add(outputFile);

        // Flags
        command.add("--lbs");
        command.add(String.valueOf(request.getLbs() != null ? request.getLbs() : 2));

        command.add("--fps");
        command.add(String.valueOf(request.getFps() != null ? request.getFps() : 2));

        if (request.getBlockApps() != null && !request.getBlockApps().isEmpty()) {
            for (String app : request.getBlockApps()) {
                command.add("--block-app");
                command.add(app);
            }
        }

        return command;
    }
}