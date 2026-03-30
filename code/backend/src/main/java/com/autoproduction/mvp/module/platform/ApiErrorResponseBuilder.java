package com.autoproduction.mvp.module.platform;

import com.autoproduction.mvp.api.ApiSupport;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class ApiErrorResponseBuilder {

  public ResponseEntity<Map<String, Object>> buildResponse(
    HttpStatus status,
    String path,
    String requestId,
    String code,
    String message,
    boolean retryable
  ) {
    return ApiSupport.json(status, requestId, buildBody(path, requestId, code, message, retryable));
  }

  public Map<String, Object> buildBody(
    String path,
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
      return body;
    }

    Map<String, Object> inner = new LinkedHashMap<>();
    inner.put("request_id", requestId);
    inner.put("code", code);
    inner.put("message", message);

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("error", inner);
    return body;
  }
}
