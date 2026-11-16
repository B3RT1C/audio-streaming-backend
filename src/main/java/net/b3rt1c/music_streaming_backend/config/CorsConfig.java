package net.b3rt1c.music_streaming_backend.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer(
            @Value("${app.cors.allowed-origins:*}") String allowedOrigins) {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isEmpty())
            .toList();

        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                CorsRegistration mapping = registry.addMapping("/**")
                    .allowedMethods("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS")
                    .allowedHeaders("*")
                    .exposedHeaders("Accept-Ranges", "Content-Range", "Content-Length", "Content-Type");
                if (origins.isEmpty() || origins.contains("*")) {
                    mapping.allowedOriginPatterns("*");
                } else {
                    mapping.allowedOrigins(origins.toArray(String[]::new));
                }
            }
        };
    }
}
