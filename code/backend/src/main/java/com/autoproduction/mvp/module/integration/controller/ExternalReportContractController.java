package com.autoproduction.mvp.module.integration.controller;

import com.autoproduction.mvp.api.ApiSupport;
import com.autoproduction.mvp.api.ContractControllerSupport;
import com.autoproduction.mvp.module.integration.IntegrationFacade;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class ExternalReportContractController {
  private final IntegrationFacade integrationFacade;

  public ExternalReportContractController(IntegrationFacade integrationFacade) {
    this.integrationFacade = integrationFacade;
  }

  @GetMapping("/reports/workshop-weekly-plan")
  public ResponseEntity<Map<String, Object>> listWorkshopWeeklyPlan(
    HttpServletRequest request,
    @RequestParam(value = "version_no", required = false) String versionNo
  ) {
    return ContractControllerSupport.listResponse(request, integrationFacade.listWorkshopWeeklyPlanRows(versionNo));
  }

  @GetMapping("/reports/workshop-monthly-plan")
  public ResponseEntity<Map<String, Object>> listWorkshopMonthlyPlan(
    HttpServletRequest request,
    @RequestParam(value = "version_no", required = false) String versionNo
  ) {
    return ContractControllerSupport.listResponse(request, integrationFacade.listWorkshopMonthlyPlanRows(versionNo));
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
      .body(integrationFacade.exportWorkshopWeeklyPlanXlsx(versionNo));
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
      .body(integrationFacade.exportWorkshopMonthlyPlanXls(versionNo));
  }

}
