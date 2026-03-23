package com.autoproduction.mvp.api;

import com.autoproduction.mvp.core.MvpServiceException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(MvpServiceException.class)
  public ResponseEntity<Map<String, Object>> handleKnown(MvpServiceException error, HttpServletRequest request) {
    String requestId = ApiSupport.getOrCreateRequestId(request, null);
    return buildError(
      request.getRequestURI(),
      HttpStatus.valueOf(error.getStatusCode()),
      requestId,
      error.getCode(),
      error.getMessage(),
      error.isRetryable()
    );
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleUnknown(Exception error, HttpServletRequest request) {
    String requestId = ApiSupport.getOrCreateRequestId(request, null);
    return buildError(
      request.getRequestURI(),
      HttpStatus.INTERNAL_SERVER_ERROR,
      requestId,
      "INTERNAL_ERROR",
      error.getMessage() == null ? "Unknown error." : error.getMessage(),
      false
    );
  }

  private ResponseEntity<Map<String, Object>> buildError(
    String path,
    HttpStatus status,
    String requestId,
    String code,
    String message,
    boolean retryable
  ) {
    if (ApiSupport.isContractRoute(path)) {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("request_id", requestId);
      body.put("code", code);
      body.put("message", message);
      body.put("retryable", retryable);
      body.put("timestamp", ApiSupport.now());
      return ApiSupport.json(status, requestId, body);
    }

    Map<String, Object> inner = new LinkedHashMap<>();
    inner.put("request_id", requestId);
    inner.put("code", code);
    inner.put("message", message);

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("error", inner);
    return ApiSupport.json(status, requestId, body);
  }
}
