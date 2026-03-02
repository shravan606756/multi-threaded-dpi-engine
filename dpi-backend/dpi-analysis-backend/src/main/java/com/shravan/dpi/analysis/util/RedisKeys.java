package com.shravan.dpi.analysis.util;

public class RedisKeys {

    private static final String JOB_STATUS_KEY = "job:%s:status";
    private static final String JOB_RESULT_KEY = "job:%s:result";
    private static final String AI_CACHE_KEY = "ai:cache:%s";

    private RedisKeys() {
        // Private constructor to prevent instantiation
    }

    public static String jobStatus(String jobId) {
        return String.format(JOB_STATUS_KEY, jobId);
    }

    public static String jobResult(String jobId) {
        return String.format(JOB_RESULT_KEY, jobId);
    }

    public static String aiCache(String cacheKey) {
        return String.format(AI_CACHE_KEY, cacheKey);
    }
}
