package com.autoproduction.mvp.module.integration.controller;

import com.autoproduction.mvp.api.ApiSupport;
import com.autoproduction.mvp.core.MvpStoreService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class ExternalContractController {
  private final MvpStoreService store;

  public ExternalContractController(MvpStoreService store) {
    this.store = store;
  }

  @GetMapping("/erp/sales-order-lines")
  public ResponseEntity<Map<String, Object>> listSalesOrderLines(HttpServletRequest request) {
    return listResponse(request, store.listSalesOrderLines());
  }

  @GetMapping("/erp/plan-orders")
  public ResponseEntity<Map<String, Object>> listPlanOrders(HttpServletRequest request) {
    return listResponse(request, store.listPlanOrders());
  }

  @GetMapping("/erp/production-orders")
  public ResponseEntity<Map<String, Object>> listProductionOrders(HttpServletRequest request) {
    return listResponse(request, store.listProductionOrders());
  }

  @GetMapping("/erp/sales-order-headers-raw")
  public ResponseEntity<Map<String, Object>> listSalesOrderHeadersRaw(HttpServletRequest request) {
    return listResponse(request, store.listErpSalesOrderHeadersRaw());
  }

  @GetMapping("/erp/sales-order-lines-raw")
  public ResponseEntity<Map<String, Object>> listSalesOrderLinesRaw(HttpServletRequest request) {
    return listResponse(request, store.listErpSalesOrderLinesRaw());
  }

  @GetMapping("/erp/production-order-headers-raw")
  public ResponseEntity<Map<String, Object>> listProductionOrderHeadersRaw(HttpServletRequest request) {
    return listResponse(request, store.listErpProductionOrderHeadersRaw());
  }

  @GetMapping("/erp/production-order-lines-raw")
  public ResponseEntity<Map<String, Object>> listProductionOrderLinesRaw(HttpServletRequest request) {
    return listResponse(request, store.listErpProductionOrderLinesRaw());
  }

  @GetMapping("/erp/schedule-controls")
  public ResponseEntity<Map<String, Object>> listScheduleControls(HttpServletRequest request) {
    return listResponse(request, store.listScheduleControls());
  }

  @GetMapping("/erp/mrp-links")
  public ResponseEntity<Map<String, Object>> listMrpLinks(HttpServletRequest request) {
    return listResponse(request, store.listMrpLinks());
  }

  @GetMapping("/erp/delivery-progress")
  public ResponseEntity<Map<String, Object>> listDeliveryProgress(HttpServletRequest request) {
    return listResponse(request, store.listDeliveryProgress());
  }

  @GetMapping("/erp/material-availability")
  public ResponseEntity<Map<String, Object>> listMaterialAvailability(HttpServletRequest request) {
    return listResponse(request, store.listMaterialAvailability());
  }

  @GetMapping("/mes/equipments")
  public ResponseEntity<Map<String, Object>> listEquipments(HttpServletRequest request) {
    return listResponse(request, store.listEquipments());
  }

  @GetMapping("/mes/process-routes")
  public ResponseEntity<Map<String, Object>> listProcessRoutes(HttpServletRequest request) {
    return listResponse(request, store.listProcessRoutes());
  }

  @GetMapping("/mes/reportings")
  public ResponseEntity<Map<String, Object>> listMesReportings(
    HttpServletRequest request,
    @RequestParam Map<String, String> filters
  ) {
    return listResponse(request, store.listReportingsForMes(filters));
  }

  @GetMapping("/mes/equipment-process-capabilities")
  public ResponseEntity<Map<String, Object>> listEquipmentCapabilities(HttpServletRequest request) {
    return listResponse(request, store.listEquipmentProcessCapabilities());
  }

  @GetMapping("/mes/employee-skills")
  public ResponseEntity<Map<String, Object>> listEmployeeSkills(HttpServletRequest request) {
    return listResponse(request, store.listEmployeeSkills());
  }

  @GetMapping("/mes/shift-calendar")
  public ResponseEntity<Map<String, Object>> listShiftCalendar(HttpServletRequest request) {
    return listResponse(request, store.listShiftCalendar());
  }

  @GetMapping("/reports/workshop-weekly-plan")
  public ResponseEntity<Map<String, Object>> listWorkshopWeeklyPlan(
    HttpServletRequest request,
    @RequestParam(value = "version_no", required = false) String versionNo
  ) {
    return listResponse(request, store.listWorkshopWeeklyPlanRows(versionNo));
  }

  @GetMapping("/reports/workshop-monthly-plan")
  public ResponseEntity<Map<String, Object>> listWorkshopMonthlyPlan(
    HttpServletRequest request,
    @RequestParam(value = "version_no", required = false) String versionNo
  ) {
    return listResponse(request, store.listWorkshopMonthlyPlanRows(versionNo));
  }

  @GetMapping(value = "/reports/workshop-weekly-plan/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
  public ResponseEntity<byte[]> exportWorkshopWeeklyPlan(
    HttpServletRequest request,
    @RequestParam(value = "version_no", required = false) String versionNo
  ) {
    String requestId = ApiSupport.getOrCreateRequestId(request, null);
    return ResponseEntity.ok()
      .header("x-request-id", requestId)
      .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"workshop-weekly-plan.xlsx\"")
      .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
      .body(store.exportWorkshopWeeklyPlanXlsx(versionNo));
  }

  @GetMapping(value = "/reports/workshop-monthly-plan/export", produces = "application/vnd.ms-excel")
  public ResponseEntity<byte[]> exportWorkshopMonthlyPlan(
    HttpServletRequest request,
    @RequestParam(value = "version_no", required = false) String versionNo
  ) {
    String requestId = ApiSupport.getOrCreateRequestId(request, null);
    return ResponseEntity.ok()
      .header("x-request-id", requestId)
      .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"workshop-monthly-plan.xls\"")
      .contentType(MediaType.parseMediaType("application/vnd.ms-excel"))
      .body(store.exportWorkshopMonthlyPlanXls(versionNo));
  }

  @PostMapping("/erp/schedule-results")
  public ResponseEntity<Map<String, Object>> writeScheduleResults(
    HttpServletRequest request,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = payload == null ? Map.of() : payload;
    String requestId = ApiSupport.requireRequestId(request, body);
    return ApiSupport.ok(requestId, store.writeScheduleResults(body, requestId, "erp"));
  }

  @PostMapping("/erp/schedule-status")
  public ResponseEntity<Map<String, Object>> writeScheduleStatus(
    HttpServletRequest request,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = payload == null ? Map.of() : payload;
    String requestId = ApiSupport.requireRequestId(request, body);
    return ApiSupport.ok(requestId, store.writeScheduleStatus(body, requestId, "erp"));
  }

  @PostMapping("/internal/wip-lots")
  public ResponseEntity<Map<String, Object>> ingestWipLot(
    HttpServletRequest request,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = payload == null ? Map.of() : payload;
    String requestId = ApiSupport.requireRequestId(request, body);
    return ApiSupport.json(HttpStatus.ACCEPTED, requestId, store.ingestWipLotEvent(body, requestId, "mes"));
  }

  @PostMapping("/internal/replan-jobs")
  public ResponseEntity<Map<String, Object>> triggerReplan(
    HttpServletRequest request,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = payload == null ? Map.of() : payload;
    String requestId = ApiSupport.requireRequestId(request, body);
    store.triggerReplanJob(body, requestId, "system");
    return ApiSupport.json(
      HttpStatus.ACCEPTED,
      requestId,
      Map.of("request_id", requestId, "accepted", true, "message", "Replan accepted.")
    );
  }

  private ResponseEntity<Map<String, Object>> listResponse(HttpServletRequest request, List<Map<String, Object>> items) {
    String requestId = ApiSupport.getOrCreateRequestId(request, null);
    return ApiSupport.ok(requestId, ApiSupport.pagedBody(requestId, request, items));
  }
}
