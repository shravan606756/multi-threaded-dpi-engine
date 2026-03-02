package com.shravan.dpi.analysis.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

public class FileUtil {

    private FileUtil() {
        // Private constructor to prevent instantiation
    }

    public static String saveUploadedFile(MultipartFile file, String directory) throws IOException {
        // Create directory if it doesn't exist
        Path dirPath = Paths.get(directory);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }

        // Generate unique filename
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".") 
                ? originalFilename.substring(originalFilename.lastIndexOf(".")) 
                : "";
        String uniqueFilename = UUID.randomUUID().toString() + extension;
        
        Path filePath = dirPath.resolve(uniqueFilename);
        file.transferTo(filePath.toFile());
        
        return filePath.toString();
    }

    public static void deleteFile(String filePath) {
        try {
            File file = new File(filePath);
            if (file.exists()) {
                file.delete();
            }
        } catch (Exception e) {
            // Log but don't throw - cleanup is best effort
            System.err.println("Failed to delete file: " + filePath + " - " + e.getMessage());
        }
    }

    public static void cleanupOldFiles(String directory, long maxAgeMillis) {
        try {
            File dir = new File(directory);
            if (!dir.exists() || !dir.isDirectory()) {
                return;
            }

            long currentTime = System.currentTimeMillis();
            File[] files = dir.listFiles();
            
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && (currentTime - file.lastModified()) > maxAgeMillis) {
                        file.delete();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to cleanup old files: " + e.getMessage());
        }
    }
}
