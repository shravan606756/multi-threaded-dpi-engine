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

    // 1. ADDED 'String enginePath' HERE so Java knows what it is!
    public String executeDpi(String enginePath, String inputFilePath, AnalysisRequest request) {
        try {
            // Generate guaranteed absolute paths for Windows
            String inputFileName = new File(inputFilePath).getName();
            String outputFileName = "output_" + inputFileName;

            String absoluteInputPath = new File(inputFilePath).getAbsolutePath();
            String absoluteOutputPath = new File(config.getOutputStoragePath(), outputFileName).getAbsolutePath();

            // Pass the enginePath straight through to your builder
            List<String> command = buildCommand(enginePath, absoluteInputPath, absoluteOutputPath, request);

            // Log the command to the IntelliJ console so you can verify it
            System.out.println("EXECUTING C++ COMMAND: " + String.join(" ", command));

            // Execute process
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            // Capture output and JSON
            StringBuilder output = new StringBuilder();
            StringBuilder jsonOutput = new StringBuilder();
            boolean inJsonBlock = false;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");

                    // Detect the start of the JSON block
                    if (line.trim().equals("{")) {
                        inJsonBlock = true;
                    }

                    // Once we start capturing, DO NOT STOP until the C++ engine finishes
                    if (inJsonBlock) {
                        jsonOutput.append(line).append("\n");
                    }
                }
            }

            // Wait for process to complete
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new DpiExecutionException(
                        "DPI engine failed with exit code " + exitCode + ".\nCommand: " + String.join(" ", command) + "\nOutput: " + output.toString()
                );
            }

            // Extract JSON portion
            String jsonResult = jsonOutput.toString().trim();
            if (jsonResult.isEmpty()) {
                throw new DpiExecutionException(
                        "No JSON output found from DPI engine. Output: " + output.toString()
                );
            }

            return jsonResult;

        } catch (DpiExecutionException e) {
            throw e; // Rethrow custom exceptions
        } catch (Exception e) {
            throw new DpiExecutionException("Failed to execute DPI engine: " + e.getMessage(), e);
        }
    }

    private List<String> buildCommand(String enginePath, String inputFile, String outputFile, AnalysisRequest request) {
        List<String> command = new ArrayList<>();

        // 2. Add the dynamic engine path as the VERY FIRST argument (the executable)
        command.add(enginePath);

        // (REMOVED the old config.getBinaryPath() line here so it doesn't duplicate!)

        // NO FLAGS (-i or -o) here! The C++ engine expects positional arguments.
        command.add(inputFile);
        command.add(outputFile);

        // Reverting back to your original --lbs and --fps flags
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