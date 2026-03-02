package com.shravan.dpi.analysis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class DpiAnalysisApplication {

    public static void main(String[] args) {
        SpringApplication.run(DpiAnalysisApplication.class, args);
    }
}
