package com.stockanalyzer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AnalysisRequest {

    @NotBlank(message = "Ticker symbol is required")
    @Size(min = 1, max = 10, message = "Ticker must be 1–10 characters")
    @Pattern(regexp = "^[A-Za-z.\\-]{1,10}$", message = "Ticker must contain only letters, dots, or hyphens")
    private String ticker;

    @NotBlank(message = "Investment horizon is required")
    @Size(min = 1, max = 50, message = "Horizon must be 1–50 characters")
    private String horizon;
}
