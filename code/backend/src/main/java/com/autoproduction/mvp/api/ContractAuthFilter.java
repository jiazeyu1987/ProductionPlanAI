package com.autoproduction.mvp.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ContractAuthFilter extends OncePerRequestFilter {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      return true;
    }
    return !ApiSupport.isContractRoute(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(
    HttpServletRequest request,
    HttpServletResponse response,
    FilterChain filterChain
  ) throws ServletException, IOException {
    String auth = request.getHeader("Authorization");
    if (auth != null && auth.startsWith("Bearer ")) {
      filterChain.doFilter(request, response);
      return;
    }

    String requestId = ApiSupport.getOrCreateRequestId(request, null);
    response.setStatus(401);
    response.setHeader("x-request-id", requestId);
    response.setContentType("application/json");

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("request_id", requestId);
    body.put("code", "UNAUTHORIZED");
    body.put("message", "Bearer token is required.");
    body.put("retryable", false);
    body.put("timestamp", ApiSupport.now());
    objectMapper.writeValue(response.getWriter(), body);
  }
}
