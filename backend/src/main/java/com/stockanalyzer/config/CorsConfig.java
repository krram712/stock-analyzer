package com.stockanalyzer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins}")
    private String allowedOriginsStr;

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        // Support both exact origins and wildcard patterns (e.g. *.vercel.app)
        List<String> origins = Arrays.asList(allowedOriginsStr.split(","));
        List<String> exact   = origins.stream().filter(o -> !o.contains("*")).toList();
        List<String> pattern = origins.stream().filter(o ->  o.contains("*")).toList();

        if (!exact.isEmpty())   config.setAllowedOrigins(exact);
        if (!pattern.isEmpty()) config.setAllowedOriginPatterns(pattern);

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("X-Processing-Time-Ms"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        source.registerCorsConfiguration("/api/**", config);
        return new CorsFilter(source);
    }
}
