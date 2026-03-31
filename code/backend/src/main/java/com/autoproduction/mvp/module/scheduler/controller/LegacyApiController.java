package com.autoproduction.mvp.module.scheduler.controller;

import com.autoproduction.mvp.api.ApiSupport;
import com.autoproduction.mvp.api.ContractControllerSupport;
import com.autoproduction.mvp.core.ErpSqliteOrderLoader;
import com.autoproduction.mvp.module.orderexecution.OrderExecutionFacade;
import com.autoproduction.mvp.module.integration.erp.ErpDataManager;
import com.autoproduction.mvp.module.platform.PlatformMaintenanceService;
import com.autoproduction.mvp.module.schedule.ScheduleFacade;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.DeleteMapping;
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
  private final ScheduleFacade scheduleFacade;
  private final OrderExecutionFacade orderExecutionFacade;
  private final PlatformMaintenanceService platformMaintenanceService;
  private final ErpDataManager erpDataManager;
  private final ErpSqliteOrderLoader erpSqliteOrderLoader;
  private final Environment environment;

  public LegacyApiController(
    ScheduleFacade scheduleFacade,
    OrderExecutionFacade orderExecutionFacade,
    PlatformMaintenanceService platformMaintenanceService,
    ErpDataManager erpDataManager,
    ErpSqliteOrderLoader erpSqliteOrderLoader,
    Environment environment
  ) {
    this.scheduleFacade = scheduleFacade;
    this.orderExecutionFacade = orderExecutionFacade;
    this.platformMaintenanceService = platformMaintenanceService;
    this.erpDataManager = erpDataManager;
    this.erpSqliteOrderLoader = erpSqliteOrderLoader;
    this.environment = environment;
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
    body.put("items", orderExecutionFacade.listOrders());
    return body;
  }

  @PostMapping("/orders")
  public ResponseEntity<Map<String, Object>> upsertOrder(
    HttpServletRequest request,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = ContractControllerSupport.body(payload);
    String requestId = ContractControllerSupport.requireRequestId(request, body);
    Map<String, Object> result = orderExecutionFacade.upsertOrder(body, requestId, "api");
    return ApiSupport.json(HttpStatus.CREATED, requestId, result);
  }

  @PatchMapping("/orders/{orderNo}")
  public ResponseEntity<Map<String, Object>> patchOrder(
    HttpServletRequest request,
    @PathVariable String orderNo,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = ContractControllerSupport.body(payload);
    String requestId = ContractControllerSupport.requireRequestId(request, body);
    return ApiSupport.ok(requestId, orderExecutionFacade.patchOrder(orderNo, body, requestId, "api"));
  }

  @PostMapping("/schedules/generate")
  public ResponseEntity<Map<String, Object>> generateSchedule(
    HttpServletRequest request,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = ContractControllerSupport.body(payload);
    String requestId = ContractControllerSupport.requireRequestId(request, body);
    return ApiSupport.json(HttpStatus.CREATED, requestId, scheduleFacade.generateSchedule(body, requestId, "api"));
  }

  @GetMapping("/schedules/generate-task")
  public Map<String, Object> scheduleGenerateTask(HttpServletRequest request) {
    String requestId = ApiSupport.getOrCreateRequestId(request, null);
    return scheduleFacade.getGenerateTaskStatus(requestId);
  }

  @GetMapping("/schedules")
  public Map<String, Object> listSchedules() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("items", scheduleFacade.listSchedules());
    return body;
  }

  @GetMapping("/schedules/latest")
  public Map<String, Object> latestSchedule() {
    return scheduleFacade.getLatestSchedule();
  }

  @GetMapping("/schedules/latest/validation")
  public Map<String, Object> validateLatest() {
    return scheduleFacade.validateSchedule(null);
  }

  @GetMapping("/schedules/{versionNo}/validation")
  public Map<String, Object> validateSchedule(@PathVariable String versionNo) {
    return scheduleFacade.validateSchedule(versionNo);
  }

  @PostMapping("/schedules/{versionNo}/publish")
  public ResponseEntity<Map<String, Object>> publishSchedule(
    HttpServletRequest request,
    @PathVariable String versionNo,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = ContractControllerSupport.body(payload);
    String requestId = ContractControllerSupport.requireRequestId(request, body);
    return ApiSupport.ok(requestId, scheduleFacade.publishSchedule(versionNo, body, requestId, "api"));
  }

  @PostMapping("/reportings")
  public ResponseEntity<Map<String, Object>> createReporting(
    HttpServletRequest request,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = ContractControllerSupport.body(payload);
    String requestId = ContractControllerSupport.requireRequestId(request, body);
    return ApiSupport.json(HttpStatus.CREATED, requestId, orderExecutionFacade.recordReporting(body, requestId, "api"));
  }

  @GetMapping("/reportings")
  public Map<String, Object> listReportings() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("items", orderExecutionFacade.listReportings());
    return body;
  }

  @DeleteMapping("/reportings/{reportingId}")
  public ResponseEntity<Map<String, Object>> deleteReporting(
    HttpServletRequest request,
    @PathVariable String reportingId,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = ContractControllerSupport.body(payload);
    String requestId = ContractControllerSupport.requireRequestId(request, body);
    return ApiSupport.ok(requestId, orderExecutionFacade.deleteReporting(reportingId, requestId, "api"));
  }

  @PostMapping("/reset")
  public Map<String, Object> reset() {
    return platformMaintenanceService.reset();
  }

  @PostMapping("/runtime/rebuild")
  public Map<String, Object> rebuildRuntimeState(HttpServletRequest request) {
    String requestId = ApiSupport.getOrCreateRequestId(request, null);
    return platformMaintenanceService.rebuildRuntimeState(requestId);
  }

  @GetMapping("/test/erp/production-orders-preview")
  public Map<String, Object> previewErpProductionOrders(HttpServletRequest request) {
    String requestId = ApiSupport.getOrCreateRequestId(request, null);
    List<Map<String, Object>> fromCache = erpDataManager.getProductionOrders();
    List<Map<String, Object>> fromLoader = erpSqliteOrderLoader.loadProductionOrders();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("request_id", requestId);
    body.put("cached_count", fromCache == null ? 0 : fromCache.size());
    body.put("loader_count", fromLoader == null ? 0 : fromLoader.size());
    body.put("cached_sample", fromCache == null ? List.of() : fromCache.stream().limit(3).toList());
    body.put("loader_sample", fromLoader == null ? List.of() : fromLoader.stream().limit(3).toList());
    return body;
  }

  @GetMapping("/test/erp/production-orders-debug")
  public Map<String, Object> debugErpProductionOrderSources(HttpServletRequest request) {
    String requestId = ApiSupport.getOrCreateRequestId(request, null);
    String sqlitePath = environment.getProperty("mvp.erp.sqlite-path", "");

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("request_id", requestId);
    body.put("sqlite_path", sqlitePath);
    body.put("sqlite_exists", safeExists(sqlitePath));
    return body;
  }

  @GetMapping("/test/erp/material-issues/{orderNo}")
  public Map<String, Object> listErpMaterialIssuesForTest(
    HttpServletRequest request,
    @PathVariable String orderNo
  ) {
    String requestId = ApiSupport.getOrCreateRequestId(request, null);
    String normalizedOrderNo = orderNo == null ? "" : orderNo.trim();
    if (normalizedOrderNo.isBlank()) {
      throw new IllegalArgumentException("orderNo is required.");
    }

    List<Map<String, Object>> items = new ArrayList<>();
    List<Map<String, Object>> rawRows = erpSqliteOrderLoader.loadProductionMaterialIssuesByOrderContains(normalizedOrderNo);
    for (Map<String, Object> rawRow : rawRows) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("order_no", rawRow.getOrDefault("source_production_order_no", ""));
      row.put("child_material_code", rawRow.getOrDefault("child_material_code", ""));
      row.put("child_material_name_cn", rawRow.getOrDefault("child_material_name_cn", ""));
      row.put("spec_model", rawRow.getOrDefault("spec_model", ""));
      items.add(row);
    }

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("request_id", requestId);
    body.put("items", items);
    return body;
  }

  private static boolean safeExists(String rawPath) {
    if (rawPath == null || rawPath.isBlank()) {
      return false;
    }
    try {
      return Files.exists(Path.of(rawPath));
    } catch (Exception ex) {
      return false;
    }
  }

  @PostMapping("/test/import-production-orders")
  public ResponseEntity<Map<String, Object>> importProductionOrdersFromErp(
    HttpServletRequest request,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = ContractControllerSupport.body(payload);
    String requestId = ContractControllerSupport.requireRequestId(request, body);
    Map<String, Object> result = orderExecutionFacade.importProductionOrdersFromErp(body, requestId, "api");
    return ApiSupport.ok(requestId, result);
  }

  @GetMapping("/test/import-production-orders/task")
  public Map<String, Object> importProductionOrdersTask(HttpServletRequest request) {
    String requestId = ApiSupport.getOrCreateRequestId(request, null);
    return orderExecutionFacade.getImportTaskStatus(requestId);
  }
}
