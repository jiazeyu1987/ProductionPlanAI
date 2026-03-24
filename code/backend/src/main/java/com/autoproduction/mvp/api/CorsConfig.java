package com.autoproduction.mvp.api;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
  @Value("${CORS_ALLOWED_ORIGINS:*}")
  private String allowedOrigins;

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    applyCommonCors(registry.addMapping("/api/**"));
    applyCommonCors(registry.addMapping("/v1/**"));
    applyCommonCors(registry.addMapping("/internal/v1/**"));
  }

  private void applyCommonCors(CorsRegistration registration) {
    registration
      .allowedOriginPatterns(resolveAllowedOriginPatterns())
      .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
      .allowedHeaders("*")
      .exposedHeaders("x-request-id");
  }

  private String[] resolveAllowedOriginPatterns() {
    return Arrays
      .stream(allowedOrigins.split(","))
      .map(String::trim)
      .filter(value -> !value.isEmpty())
      .toArray(String[]::new);
  }
}
