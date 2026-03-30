package com.autoproduction.mvp.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.autoproduction.mvp.module.platform.ApiErrorResponseBuilder;
import com.autoproduction.mvp.module.platform.ContractRouteAuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ContractAuthFilter extends OncePerRequestFilter {
  private final ObjectMapper objectMapper;
  private final ApiErrorResponseBuilder apiErrorResponseBuilder;
  private final ContractRouteAuthService contractRouteAuthService;

  public ContractAuthFilter(
    ObjectMapper objectMapper,
    ApiErrorResponseBuilder apiErrorResponseBuilder,
    ContractRouteAuthService contractRouteAuthService
  ) {
    this.objectMapper = objectMapper;
    this.apiErrorResponseBuilder = apiErrorResponseBuilder;
    this.contractRouteAuthService = contractRouteAuthService;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !contractRouteAuthService.shouldAuthenticate(request.getMethod(), request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(
    HttpServletRequest request,
    HttpServletResponse response,
    FilterChain filterChain
  ) throws ServletException, IOException {
    String auth = request.getHeader("Authorization");
    if (contractRouteAuthService.hasValidBearerToken(auth)) {
      filterChain.doFilter(request, response);
      return;
    }

    String requestId = ApiSupport.getOrCreateRequestId(request, null);
    response.setStatus(401);
    response.setHeader("x-request-id", requestId);
    response.setContentType("application/json");

    Map<String, Object> body = apiErrorResponseBuilder.buildBody(
      request.getRequestURI(),
      requestId,
      "UNAUTHORIZED",
      "Bearer token is required.",
      false
    );
    objectMapper.writeValue(response.getWriter(), body);
  }
}
