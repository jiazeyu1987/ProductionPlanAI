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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class ExternalMesContractController {
  private final IntegrationFacade integrationFacade;

  public ExternalMesContractController(IntegrationFacade integrationFacade) {
    this.integrationFacade = integrationFacade;
  }

  @GetMapping("/mes/equipments")
  public ResponseEntity<Map<String, Object>> listEquipments(HttpServletRequest request) {
    return ContractControllerSupport.listResponse(request, integrationFacade.listEquipments());
  }

  @GetMapping("/mes/process-routes")
  public ResponseEntity<Map<String, Object>> listProcessRoutes(HttpServletRequest request) {
    return ContractControllerSupport.listResponse(request, integrationFacade.listProcessRoutes());
  }

  @GetMapping("/mes/reportings")
  public ResponseEntity<Map<String, Object>> listMesReportings(
    HttpServletRequest request,
    @RequestParam Map<String, String> filters
  ) {
    return ContractControllerSupport.listResponse(request, integrationFacade.listReportingsForMes(filters));
  }

  @GetMapping("/mes/equipment-process-capabilities")
  public ResponseEntity<Map<String, Object>> listEquipmentCapabilities(HttpServletRequest request) {
    return ContractControllerSupport.listResponse(request, integrationFacade.listEquipmentProcessCapabilities());
  }

  @GetMapping("/mes/employee-skills")
  public ResponseEntity<Map<String, Object>> listEmployeeSkills(HttpServletRequest request) {
    return ContractControllerSupport.listResponse(request, integrationFacade.listEmployeeSkills());
  }

  @GetMapping("/mes/shift-calendar")
  public ResponseEntity<Map<String, Object>> listShiftCalendar(HttpServletRequest request) {
    return ContractControllerSupport.listResponse(request, integrationFacade.listShiftCalendar());
  }

  @PostMapping("/internal/wip-lots")
  public ResponseEntity<Map<String, Object>> ingestWipLot(
    HttpServletRequest request,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = ContractControllerSupport.body(payload);
    String requestId = ContractControllerSupport.requireRequestId(request, body);
    return ApiSupport.json(HttpStatus.ACCEPTED, requestId, integrationFacade.ingestWipLotEvent(body, requestId, "mes"));
  }
}
