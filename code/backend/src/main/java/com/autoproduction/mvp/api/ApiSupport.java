package com.autoproduction.mvp.api;

import com.autoproduction.mvp.core.MvpServiceException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public final class ApiSupport {
  public static final String REQUEST_ID_ATTR = "mvp.request_id";

  private ApiSupport() {}

  public static String resolveRequestId(HttpServletRequest request, Map<String, Object> payload) {
    String fromHeader = request.getHeader("x-request-id");
    if (fromHeader == null || fromHeader.isBlank()) {
      fromHeader = request.getHeader("x_request_id");
    }
    if (fromHeader != null && !fromHeader.isBlank()) {
      return fromHeader;
    }

    if (payload != null) {
      Object bodyValue = payload.get("request_id");
      if (bodyValue == null) {
        bodyValue = payload.get("requestId");
      }
      if (bodyValue != null && !String.valueOf(bodyValue).isBlank()) {
        return String.valueOf(bodyValue);
      }
    }

    String fromQuery = request.getParameter("request_id");
    if (fromQuery != null && !fromQuery.isBlank()) {
      return fromQuery;
    }
    return null;
  }

  public static String getOrCreateRequestId(HttpServletRequest request, Map<String, Object> payload) {
    Object cached = request.getAttribute(REQUEST_ID_ATTR);
    if (cached instanceof String value && !value.isBlank()) {
      return value;
    }
    String requestId = resolveRequestId(request, payload);
    if (requestId == null || requestId.isBlank()) {
      requestId = newRequestId();
    }
    request.setAttribute(REQUEST_ID_ATTR, requestId);
    return requestId;
  }

  public static String requireRequestId(HttpServletRequest request, Map<String, Object> payload) {
    String requestId = resolveRequestId(request, payload);
    if (requestId == null || requestId.isBlank()) {
      throw new MvpServiceException(400, "REQUEST_ID_REQUIRED", "request_id is required.", false);
    }
    request.setAttribute(REQUEST_ID_ATTR, requestId);
    return requestId;
  }

  public static ResponseEntity<Map<String, Object>> json(HttpStatus status, String requestId, Map<String, Object> body) {
    return ResponseEntity.status(status).header("x-request-id", requestId).body(body);
  }

  public static ResponseEntity<Map<String, Object>> ok(String requestId, Map<String, Object> body) {
    return json(HttpStatus.OK, requestId, body);
  }

  public static Map<String, Object> pagedBody(String requestId, HttpServletRequest request, List<Map<String, Object>> items) {
    int page = parsePositiveInt(request.getParameter("page"), 1);
    int pageSize = Math.max(1, Math.min(1000, parsePositiveInt(request.getParameter("page_size"), 200)));
    int start = (page - 1) * pageSize;
    int end = Math.min(items.size(), start + pageSize);

    List<Map<String, Object>> paged = start >= items.size() ? List.of() : items.subList(start, end);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("request_id", requestId);
    body.put("page", page);
    body.put("page_size", pageSize);
    body.put("total", items.size());
    body.put("items", paged);
    return body;
  }

  public static boolean isContractRoute(String path) {
    return "/v1".equals(path)
      || path.startsWith("/v1/")
      || "/internal/v1".equals(path)
      || path.startsWith("/internal/v1/");
  }

  public static String now() {
    return OffsetDateTime.now(ZoneOffset.UTC).toString();
  }

  private static String newRequestId() {
    return "req-" + UUID.randomUUID();
  }

  private static int parsePositiveInt(String raw, int fallback) {
    if (raw == null || raw.isBlank()) {
      return fallback;
    }
    try {
      return Math.max(1, Integer.parseInt(raw));
    } catch (NumberFormatException ignore) {
      return fallback;
    }
  }
}
