package com.autoproduction.mvp.api;

import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RootController {

  @GetMapping("/")
  public ResponseEntity<Map<String, Object>> index(HttpServletRequest request) {
    String requestId = ApiSupport.getOrCreateRequestId(request, null);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("status", "ok");
    body.put("time", OffsetDateTime.now(ZoneOffset.UTC).toString());
    body.put("message", "Backend API is running. Open the UI on the Vite dev server.");
    body.put("health", "/api/health");
    return ApiSupport.ok(requestId, body);
  }
}

