package net.b3rt1c.music_streaming_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                    .allowedOrigins(
                        "http://localhost:4200",
                        "http://localhost:8083",
                        "http://192.168.1.79:8083"
                    )
                    .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                    .allowedHeaders("*")
                    .exposedHeaders("Accept-Ranges", "Content-Range", "Content-Length");
            }
        };
    }
}
