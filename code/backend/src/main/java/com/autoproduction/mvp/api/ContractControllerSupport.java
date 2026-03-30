package com.autoproduction.mvp.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;

public final class ContractControllerSupport {
  private ContractControllerSupport() {}

  public static Map<String, Object> body(Map<String, Object> payload) {
    return payload == null ? Map.of() : payload;
  }

  public static String requireRequestId(HttpServletRequest request, Map<String, Object> payload) {
    return ApiSupport.requireRequestId(request, body(payload));
  }

  public static String operator(Map<String, Object> body, String fallback) {
    return String.valueOf(body.getOrDefault("operator", fallback));
  }

  public static ResponseEntity<Map<String, Object>> listResponse(HttpServletRequest request, List<Map<String, Object>> items) {
    String requestId = ApiSupport.getOrCreateRequestId(request, null);
    return ApiSupport.ok(requestId, ApiSupport.pagedBody(requestId, request, items));
  }
}
