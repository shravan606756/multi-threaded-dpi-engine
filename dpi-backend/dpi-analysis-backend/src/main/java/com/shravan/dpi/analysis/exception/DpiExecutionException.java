package com.shravan.dpi.analysis.exception;

public class DpiExecutionException extends RuntimeException {

    public DpiExecutionException(String message) {
        super(message);
    }

    public DpiExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
