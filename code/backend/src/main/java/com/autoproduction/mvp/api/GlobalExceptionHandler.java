package com.autoproduction.mvp.api;

import com.autoproduction.mvp.core.MvpServiceException;
import com.autoproduction.mvp.module.platform.ApiErrorResponseBuilder;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private final ApiErrorResponseBuilder apiErrorResponseBuilder;

  public GlobalExceptionHandler(ApiErrorResponseBuilder apiErrorResponseBuilder) {
    this.apiErrorResponseBuilder = apiErrorResponseBuilder;
  }

  @ExceptionHandler(MvpServiceException.class)
  public ResponseEntity<Map<String, Object>> handleKnown(MvpServiceException error, HttpServletRequest request) {
    String requestId = ApiSupport.getOrCreateRequestId(request, null);
    return apiErrorResponseBuilder.buildResponse(
      HttpStatus.valueOf(error.getStatusCode()),
      request.getRequestURI(),
      requestId,
      error.getCode(),
      error.getMessage(),
      error.isRetryable()
    );
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException error, HttpServletRequest request) {
    String requestId = ApiSupport.getOrCreateRequestId(request, null);
    return apiErrorResponseBuilder.buildResponse(
      HttpStatus.NOT_FOUND,
      request.getRequestURI(),
      requestId,
      "NOT_FOUND",
      "Not found: %s.".formatted(request.getRequestURI()),
      false
    );
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleUnknown(Exception error, HttpServletRequest request) {
    String requestId = ApiSupport.getOrCreateRequestId(request, null);
    return apiErrorResponseBuilder.buildResponse(
      HttpStatus.INTERNAL_SERVER_ERROR,
      request.getRequestURI(),
      requestId,
      "INTERNAL_ERROR",
      error.getMessage() == null ? "Unknown error." : error.getMessage(),
      false
    );
  }
}
