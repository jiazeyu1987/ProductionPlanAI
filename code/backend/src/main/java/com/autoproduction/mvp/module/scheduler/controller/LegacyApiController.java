package com.autoproduction.mvp.module.scheduler.controller;

import com.autoproduction.mvp.api.ApiSupport;
import com.autoproduction.mvp.core.MvpStoreService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class LegacyApiController {
  private final MvpStoreService store;

  public LegacyApiController(MvpStoreService store) {
    this.store = store;
  }

  @GetMapping("/health")
  public Map<String, Object> health() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("status", "ok");
    body.put("time", ApiSupport.now());
    return body;
  }

  @GetMapping("/orders")
  public Map<String, Object> listOrders() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("items", store.listOrders());
    return body;
  }

  @PostMapping("/orders")
  public ResponseEntity<Map<String, Object>> upsertOrder(
    HttpServletRequest request,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = payload == null ? Map.of() : payload;
    String requestId = ApiSupport.requireRequestId(request, body);
    Map<String, Object> result = store.upsertOrder(body, requestId, "api");
    return ApiSupport.json(HttpStatus.CREATED, requestId, result);
  }

  @PatchMapping("/orders/{orderNo}")
  public ResponseEntity<Map<String, Object>> patchOrder(
    HttpServletRequest request,
    @PathVariable String orderNo,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = payload == null ? Map.of() : payload;
    String requestId = ApiSupport.requireRequestId(request, body);
    return ApiSupport.ok(requestId, store.patchOrder(orderNo, body, requestId, "api"));
  }

  @PostMapping("/schedules/generate")
  public ResponseEntity<Map<String, Object>> generateSchedule(
    HttpServletRequest request,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = payload == null ? Map.of() : payload;
    String requestId = ApiSupport.requireRequestId(request, body);
    return ApiSupport.json(HttpStatus.CREATED, requestId, store.generateSchedule(body, requestId, "api"));
  }

  @GetMapping("/schedules")
  public Map<String, Object> listSchedules() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("items", store.listSchedules());
    return body;
  }

  @GetMapping("/schedules/latest")
  public Map<String, Object> latestSchedule() {
    return store.getLatestSchedule();
  }

  @GetMapping("/schedules/latest/validation")
  public Map<String, Object> validateLatest() {
    return store.validateSchedule(null);
  }

  @GetMapping("/schedules/{versionNo}/validation")
  public Map<String, Object> validateSchedule(@PathVariable String versionNo) {
    return store.validateSchedule(versionNo);
  }

  @PostMapping("/schedules/{versionNo}/publish")
  public ResponseEntity<Map<String, Object>> publishSchedule(
    HttpServletRequest request,
    @PathVariable String versionNo,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = payload == null ? Map.of() : payload;
    String requestId = ApiSupport.requireRequestId(request, body);
    return ApiSupport.ok(requestId, store.publishSchedule(versionNo, body, requestId, "api"));
  }

  @PostMapping("/reportings")
  public ResponseEntity<Map<String, Object>> createReporting(
    HttpServletRequest request,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = payload == null ? Map.of() : payload;
    String requestId = ApiSupport.requireRequestId(request, body);
    return ApiSupport.json(HttpStatus.CREATED, requestId, store.recordReporting(body, requestId, "api"));
  }

  @GetMapping("/reportings")
  public Map<String, Object> listReportings() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("items", store.listReportings());
    return body;
  }

  @PostMapping("/reset")
  public Map<String, Object> reset() {
    store.reset();
    return Map.of("reset", true);
  }
}
