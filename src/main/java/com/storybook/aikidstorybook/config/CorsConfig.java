package com.storybook.aikidstorybook.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        
        // Allow frontend origin
        config.addAllowedOrigin("http://localhost:3000");
        
        // Allow all necessary headers
        config.addAllowedHeader("*");
        
        // Allow all HTTP methods including OPTIONS preflight
        config.addAllowedMethod("*");
        
        // Allow credentials if needed
        config.setAllowCredentials(true);
        
        // Apply to all endpoints including /graphql
        source.registerCorsConfiguration("/**", config);
        
        return new CorsFilter(source);
    }
}