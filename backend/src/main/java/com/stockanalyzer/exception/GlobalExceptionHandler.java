package com.stockanalyzer.exception;

import com.stockanalyzer.dto.AnalysisResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<AnalysisResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));

        return ResponseEntity.badRequest().body(
                AnalysisResponse.builder()
                        .success(false)
                        .message("Validation error: " + errors)
                        .timestamp(System.currentTimeMillis())
                        .build()
        );
    }

    @ExceptionHandler(AnalysisException.class)
    public ResponseEntity<AnalysisResponse> handleAnalysisException(AnalysisException ex) {
        log.error("Analysis error: {}", ex.getMessage());
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;

        return ResponseEntity.status(status).body(
                AnalysisResponse.builder()
                        .success(false)
                        .message(ex.getMessage())
                        .timestamp(System.currentTimeMillis())
                        .build()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<AnalysisResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error during analysis", ex);
        return ResponseEntity.internalServerError().body(
                AnalysisResponse.builder()
                        .success(false)
                        .message("An unexpected error occurred. Please try again.")
                        .timestamp(System.currentTimeMillis())
                        .build()
        );
    }
}
