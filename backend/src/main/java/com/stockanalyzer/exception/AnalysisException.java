package com.stockanalyzer.exception;

public class AnalysisException extends RuntimeException {

    private final int statusCode;

    public AnalysisException(String message) {
        super(message);
        this.statusCode = 500;
    }

    public AnalysisException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public AnalysisException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 500;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
