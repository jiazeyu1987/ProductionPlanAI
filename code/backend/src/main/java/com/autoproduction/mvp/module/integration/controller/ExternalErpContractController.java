package com.autoproduction.mvp.module.integration.controller;

import com.autoproduction.mvp.api.ApiSupport;
import com.autoproduction.mvp.api.ContractControllerSupport;
import com.autoproduction.mvp.module.integration.IntegrationFacade;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class ExternalErpContractController {
  private final IntegrationFacade integrationFacade;

  public ExternalErpContractController(IntegrationFacade integrationFacade) {
    this.integrationFacade = integrationFacade;
  }

  @GetMapping("/erp/sales-order-lines")
  public ResponseEntity<Map<String, Object>> listSalesOrderLines(HttpServletRequest request) {
    return ContractControllerSupport.listResponse(request, integrationFacade.listSalesOrderLines());
  }

  @GetMapping("/erp/plan-orders")
  public ResponseEntity<Map<String, Object>> listPlanOrders(HttpServletRequest request) {
    return ContractControllerSupport.listResponse(request, integrationFacade.listPlanOrders());
  }

  @GetMapping("/erp/production-orders")
  public ResponseEntity<Map<String, Object>> listProductionOrders(HttpServletRequest request) {
    return ContractControllerSupport.listResponse(request, integrationFacade.listProductionOrders());
  }

  @GetMapping("/erp/purchase-orders")
  public ResponseEntity<Map<String, Object>> listPurchaseOrders(HttpServletRequest request) {
    return ContractControllerSupport.listResponse(request, integrationFacade.listPurchaseOrders());
  }

  @GetMapping("/erp/sales-order-headers-raw")
  public ResponseEntity<Map<String, Object>> listSalesOrderHeadersRaw(HttpServletRequest request) {
    return ContractControllerSupport.listResponse(request, integrationFacade.listErpSalesOrderHeadersRaw());
  }

  @GetMapping("/erp/sales-order-lines-raw")
  public ResponseEntity<Map<String, Object>> listSalesOrderLinesRaw(HttpServletRequest request) {
    return ContractControllerSupport.listResponse(request, integrationFacade.listErpSalesOrderLinesRaw());
  }

  @GetMapping("/erp/production-order-headers-raw")
  public ResponseEntity<Map<String, Object>> listProductionOrderHeadersRaw(HttpServletRequest request) {
    return ContractControllerSupport.listResponse(request, integrationFacade.listErpProductionOrderHeadersRaw());
  }

  @GetMapping("/erp/production-order-lines-raw")
  public ResponseEntity<Map<String, Object>> listProductionOrderLinesRaw(HttpServletRequest request) {
    return ContractControllerSupport.listResponse(request, integrationFacade.listErpProductionOrderLinesRaw());
  }

  @GetMapping("/erp/schedule-controls")
  public ResponseEntity<Map<String, Object>> listScheduleControls(HttpServletRequest request) {
    return ContractControllerSupport.listResponse(request, integrationFacade.listScheduleControls());
  }

  @GetMapping("/erp/mrp-links")
  public ResponseEntity<Map<String, Object>> listMrpLinks(HttpServletRequest request) {
    return ContractControllerSupport.listResponse(request, integrationFacade.listMrpLinks());
  }

  @GetMapping("/erp/delivery-progress")
  public ResponseEntity<Map<String, Object>> listDeliveryProgress(HttpServletRequest request) {
    return ContractControllerSupport.listResponse(request, integrationFacade.listDeliveryProgress());
  }

  @GetMapping("/erp/material-availability")
  public ResponseEntity<Map<String, Object>> listMaterialAvailability(HttpServletRequest request) {
    return ContractControllerSupport.listResponse(request, integrationFacade.listMaterialAvailability());
  }

  @PostMapping("/erp/schedule-results")
  public ResponseEntity<Map<String, Object>> writeScheduleResults(
    HttpServletRequest request,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = ContractControllerSupport.body(payload);
    String requestId = ContractControllerSupport.requireRequestId(request, body);
    return ApiSupport.ok(requestId, integrationFacade.writeScheduleResults(body, requestId, "erp"));
  }

  @PostMapping("/erp/schedule-status")
  public ResponseEntity<Map<String, Object>> writeScheduleStatus(
    HttpServletRequest request,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = ContractControllerSupport.body(payload);
    String requestId = ContractControllerSupport.requireRequestId(request, body);
    return ApiSupport.ok(requestId, integrationFacade.writeScheduleStatus(body, requestId, "erp"));
  }

  @PostMapping("/internal/replan-jobs")
  public ResponseEntity<Map<String, Object>> triggerReplan(
    HttpServletRequest request,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = ContractControllerSupport.body(payload);
    String requestId = ContractControllerSupport.requireRequestId(request, body);
    integrationFacade.triggerReplanJob(body, requestId, "system");
    return ApiSupport.json(
      HttpStatus.ACCEPTED,
      requestId,
      Map.of("request_id", requestId, "accepted", true, "message", "Replan accepted.")
    );
  }
}
