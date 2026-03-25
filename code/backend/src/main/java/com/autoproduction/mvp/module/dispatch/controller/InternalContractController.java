package com.autoproduction.mvp.module.dispatch.controller;

import com.autoproduction.mvp.api.ApiSupport;
import com.autoproduction.mvp.core.MvpServiceException;
import com.autoproduction.mvp.core.MvpStoreService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/internal")
public class InternalContractController {
  private final MvpStoreService store;

  public InternalContractController(MvpStoreService store) {
    this.store = store;
  }

  @GetMapping("/order-pool")
  public ResponseEntity<Map<String, Object>> listOrderPool(HttpServletRequest request, @RequestParam Map<String, String> filters) {
    return listResponse(request, store.listOrderPool(filters));
  }

  @PostMapping("/order-pool/{orderNo}/patch")
  public ResponseEntity<Map<String, Object>> patchOrderPoolOrder(
    HttpServletRequest request,
    @PathVariable("orderNo") String orderNo,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = payload == null ? Map.of() : payload;
    String requestId = ApiSupport.requireRequestId(request, body);
    String operator = String.valueOf(body.getOrDefault("operator", "order-pool-admin"));
    return ApiSupport.ok(requestId, store.patchOrder(orderNo, body, requestId, operator));
  }

  @GetMapping("/schedule-versions")
  public ResponseEntity<Map<String, Object>> listScheduleVersions(
    HttpServletRequest request,
    @RequestParam Map<String, String> filters
  ) {
    return listResponse(request, store.listScheduleVersions(filters));
  }

  @GetMapping("/schedule-versions/{versionNo}/tasks")
  public ResponseEntity<Map<String, Object>> listScheduleTasks(HttpServletRequest request, @PathVariable("versionNo") String versionNo) {
    return listResponse(request, store.listScheduleTasks(versionNo));
  }

  @GetMapping("/schedule-versions/{versionNo}/daily-process-load")
  public ResponseEntity<Map<String, Object>> listScheduleDailyProcessLoad(
    HttpServletRequest request,
    @PathVariable("versionNo") String versionNo
  ) {
    return listResponse(request, store.listScheduleDailyProcessLoad(versionNo));
  }

  @GetMapping("/schedule-versions/{versionNo}/shift-process-load")
  public ResponseEntity<Map<String, Object>> listScheduleShiftProcessLoad(
    HttpServletRequest request,
    @PathVariable("versionNo") String versionNo
  ) {
    return listResponse(request, store.listScheduleShiftProcessLoad(versionNo));
  }

  @GetMapping("/masterdata/config")
  public ResponseEntity<Map<String, Object>> getMasterdataConfig(HttpServletRequest request) {
    String requestId = ApiSupport.getOrCreateRequestId(request, null);
    return ApiSupport.ok(requestId, store.getMasterdataConfig(requestId));
  }

  @PostMapping("/masterdata/config")
  public ResponseEntity<Map<String, Object>> saveMasterdataConfig(
    HttpServletRequest request,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = payload == null ? Map.of() : payload;
    String requestId = ApiSupport.requireRequestId(request, body);
    String operator = String.valueOf(body.getOrDefault("operator", "masterdata-admin"));
    return ApiSupport.ok(requestId, store.saveMasterdataConfig(body, requestId, operator));
  }

  @PostMapping("/masterdata/routes/create")
  public ResponseEntity<Map<String, Object>> createMasterdataRoute(
    HttpServletRequest request,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = payload == null ? Map.of() : payload;
    String requestId = ApiSupport.requireRequestId(request, body);
    String operator = String.valueOf(body.getOrDefault("operator", "masterdata-admin"));
    return ApiSupport.ok(requestId, store.createMasterdataRoute(body, requestId, operator));
  }

  @PostMapping("/masterdata/routes/update")
  public ResponseEntity<Map<String, Object>> updateMasterdataRoute(
    HttpServletRequest request,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = payload == null ? Map.of() : payload;
    String requestId = ApiSupport.requireRequestId(request, body);
    String operator = String.valueOf(body.getOrDefault("operator", "masterdata-admin"));
    return ApiSupport.ok(requestId, store.updateMasterdataRoute(body, requestId, operator));
  }

  @PostMapping("/masterdata/routes/copy")
  public ResponseEntity<Map<String, Object>> copyMasterdataRoute(
    HttpServletRequest request,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = payload == null ? Map.of() : payload;
    String requestId = ApiSupport.requireRequestId(request, body);
    String operator = String.valueOf(body.getOrDefault("operator", "masterdata-admin"));
    return ApiSupport.ok(requestId, store.copyMasterdataRoute(body, requestId, operator));
  }

  @PostMapping("/masterdata/routes/delete")
  public ResponseEntity<Map<String, Object>> deleteMasterdataRoute(
    HttpServletRequest request,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = payload == null ? Map.of() : payload;
    String requestId = ApiSupport.requireRequestId(request, body);
    String operator = String.valueOf(body.getOrDefault("operator", "masterdata-admin"));
    return ApiSupport.ok(requestId, store.deleteMasterdataRoute(body, requestId, operator));
  }

  @GetMapping("/schedule-versions/{versionNo}/diff")
  public ResponseEntity<Map<String, Object>> getVersionDiff(
    HttpServletRequest request,
    @PathVariable("versionNo") String versionNo,
    @RequestParam(name = "compare_with") String compareWith
  ) {
    if (compareWith == null || compareWith.isBlank()) {
      throw new MvpServiceException(400, "MISSING_COMPARE_VERSION", "compare_with is required.", false);
    }
    String requestId = ApiSupport.getOrCreateRequestId(request, null);
    return ApiSupport.ok(requestId, store.getVersionDiff(versionNo, compareWith, requestId));
  }

  @GetMapping("/schedule-versions/{versionNo}/algorithm")
  public ResponseEntity<Map<String, Object>> getScheduleAlgorithm(
    HttpServletRequest request,
    @PathVariable("versionNo") String versionNo
  ) {
    String requestId = ApiSupport.getOrCreateRequestId(request, null);
    return ApiSupport.ok(requestId, store.getScheduleAlgorithm(versionNo, requestId));
  }

  @GetMapping("/dispatch-commands")
  public ResponseEntity<Map<String, Object>> listDispatchCommands(
    HttpServletRequest request,
    @RequestParam Map<String, String> filters
  ) {
    return listResponse(request, store.listDispatchCommands(filters));
  }

  @PostMapping("/dispatch-commands")
  public ResponseEntity<Map<String, Object>> createDispatchCommand(
    HttpServletRequest request,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = payload == null ? Map.of() : payload;
    String requestId = ApiSupport.requireRequestId(request, body);
    return ApiSupport.json(HttpStatus.ACCEPTED, requestId, store.createDispatchCommand(body, requestId, "dispatch"));
  }

  @PostMapping("/dispatch-commands/{commandId}/approvals")
  public ResponseEntity<Map<String, Object>> approveDispatchCommand(
    HttpServletRequest request,
    @PathVariable String commandId,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = payload == null ? Map.of() : payload;
    String requestId = ApiSupport.requireRequestId(request, body);
    return ApiSupport.ok(requestId, store.approveDispatchCommand(commandId, body, requestId, "approver"));
  }

  @PostMapping("/schedule-versions/{versionNo}/publish")
  public ResponseEntity<Map<String, Object>> publishVersion(
    HttpServletRequest request,
    @PathVariable("versionNo") String versionNo,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = payload == null ? Map.of() : payload;
    String requestId = ApiSupport.requireRequestId(request, body);
    String operator = String.valueOf(body.getOrDefault("operator", "publisher"));
    return ApiSupport.ok(requestId, store.publishSchedule(versionNo, body, requestId, operator));
  }

  @PostMapping("/schedule-versions/{versionNo}/rollback")
  public ResponseEntity<Map<String, Object>> rollbackVersion(
    HttpServletRequest request,
    @PathVariable("versionNo") String versionNo,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = payload == null ? Map.of() : payload;
    String requestId = ApiSupport.requireRequestId(request, body);
    String operator = String.valueOf(body.getOrDefault("operator", "publisher"));
    return ApiSupport.ok(requestId, store.rollbackSchedule(versionNo, body, requestId, operator));
  }

  @GetMapping("/alerts")
  public ResponseEntity<Map<String, Object>> listAlerts(HttpServletRequest request, @RequestParam Map<String, String> filters) {
    return listResponse(request, store.listAlerts(filters));
  }

  @PostMapping("/alerts/{alertId}/ack")
  public ResponseEntity<Map<String, Object>> ackAlert(
    HttpServletRequest request,
    @PathVariable String alertId,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = payload == null ? Map.of() : payload;
    String requestId = ApiSupport.requireRequestId(request, body);
    String operator = String.valueOf(body.getOrDefault("operator", "operator"));
    return ApiSupport.ok(requestId, store.ackAlert(alertId, body, requestId, operator));
  }

  @PostMapping("/alerts/{alertId}/close")
  public ResponseEntity<Map<String, Object>> closeAlert(
    HttpServletRequest request,
    @PathVariable String alertId,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = payload == null ? Map.of() : payload;
    String requestId = ApiSupport.requireRequestId(request, body);
    String operator = String.valueOf(body.getOrDefault("operator", "operator"));
    return ApiSupport.ok(requestId, store.closeAlert(alertId, body, requestId, operator));
  }

  @PostMapping("/replan-jobs")
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

  @GetMapping("/replan-jobs/{jobNo}")
  public ResponseEntity<Map<String, Object>> getReplanJob(HttpServletRequest request, @PathVariable("jobNo") String jobNo) {
    String requestId = ApiSupport.getOrCreateRequestId(request, null);
    return ApiSupport.ok(requestId, store.getReplanJob(jobNo, requestId));
  }

  @GetMapping("/audit-logs")
  public ResponseEntity<Map<String, Object>> listAuditLogs(HttpServletRequest request, @RequestParam Map<String, String> filters) {
    return listResponse(request, store.listAuditLogs(filters));
  }

  @GetMapping("/simulation/state")
  public ResponseEntity<Map<String, Object>> getSimulationState(HttpServletRequest request) {
    String requestId = ApiSupport.getOrCreateRequestId(request, null);
    return ApiSupport.ok(requestId, store.getSimulationState(requestId));
  }

  @GetMapping("/simulation/events")
  public ResponseEntity<Map<String, Object>> listSimulationEvents(
    HttpServletRequest request,
    @RequestParam Map<String, String> filters
  ) {
    return listResponse(request, store.listSimulationEvents(filters));
  }

  @PostMapping("/simulation/reset")
  public ResponseEntity<Map<String, Object>> resetSimulation(
    HttpServletRequest request,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = payload == null ? Map.of() : payload;
    String requestId = ApiSupport.requireRequestId(request, body);
    return ApiSupport.ok(requestId, store.resetSimulation(body, requestId, "simulator"));
  }

  @PostMapping("/simulation/run")
  public ResponseEntity<Map<String, Object>> runSimulation(
    HttpServletRequest request,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = payload == null ? Map.of() : payload;
    String requestId = ApiSupport.requireRequestId(request, body);
    return ApiSupport.ok(requestId, store.runSimulation(body, requestId, "simulator"));
  }

  @PostMapping("/simulation/manual/add-production-order")
  public ResponseEntity<Map<String, Object>> addManualProductionOrder(
    HttpServletRequest request,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = payload == null ? Map.of() : payload;
    String requestId = ApiSupport.requireRequestId(request, body);
    return ApiSupport.ok(requestId, store.addManualProductionOrder(body, requestId, "simulator"));
  }

  @PostMapping("/simulation/manual/advance-day")
  public ResponseEntity<Map<String, Object>> advanceManualSimulationDay(
    HttpServletRequest request,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = payload == null ? Map.of() : payload;
    String requestId = ApiSupport.requireRequestId(request, body);
    return ApiSupport.ok(requestId, store.advanceManualOneDay(body, requestId, "simulator"));
  }

  @PostMapping("/simulation/manual/reset")
  public ResponseEntity<Map<String, Object>> resetManualSimulation(
    HttpServletRequest request,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = payload == null ? Map.of() : payload;
    String requestId = ApiSupport.requireRequestId(request, body);
    return ApiSupport.ok(requestId, store.resetManualSimulation(body, requestId, "simulator"));
  }

  @GetMapping("/integration/inbox")
  public ResponseEntity<Map<String, Object>> listIntegrationInbox(
    HttpServletRequest request,
    @RequestParam Map<String, String> filters
  ) {
    return listResponse(request, store.listIntegrationInbox(filters));
  }

  @GetMapping("/integration/outbox")
  public ResponseEntity<Map<String, Object>> listIntegrationOutbox(
    HttpServletRequest request,
    @RequestParam Map<String, String> filters
  ) {
    return listResponse(request, store.listIntegrationOutbox(filters));
  }

  @PostMapping("/integration/outbox/{messageId}/retry")
  public ResponseEntity<Map<String, Object>> retryOutbox(
    HttpServletRequest request,
    @PathVariable String messageId,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = payload == null ? Map.of() : payload;
    String requestId = ApiSupport.requireRequestId(request, body);
    String operator = String.valueOf(body.getOrDefault("operator", "admin"));
    return ApiSupport.ok(requestId, store.retryOutboxMessage(messageId, requestId, operator));
  }

  private ResponseEntity<Map<String, Object>> listResponse(HttpServletRequest request, List<Map<String, Object>> items) {
    String requestId = ApiSupport.getOrCreateRequestId(request, null);
    return ApiSupport.ok(requestId, ApiSupport.pagedBody(requestId, request, items));
  }
}
