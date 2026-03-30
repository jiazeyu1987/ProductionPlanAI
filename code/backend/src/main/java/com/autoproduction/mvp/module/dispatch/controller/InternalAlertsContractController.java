package com.autoproduction.mvp.module.dispatch.controller;

import com.autoproduction.mvp.api.ApiSupport;
import com.autoproduction.mvp.api.ContractControllerSupport;
import com.autoproduction.mvp.module.dispatchalert.DispatchAlertFacade;
import jakarta.servlet.http.HttpServletRequest;
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
@RequestMapping(InternalContractController.BASE_PATH)
public class InternalAlertsContractController {
  private final DispatchAlertFacade dispatchAlertFacade;

  public InternalAlertsContractController(DispatchAlertFacade dispatchAlertFacade) {
    this.dispatchAlertFacade = dispatchAlertFacade;
  }

  @GetMapping("/alerts")
  public ResponseEntity<Map<String, Object>> listAlerts(HttpServletRequest request, @RequestParam Map<String, String> filters) {
    return ContractControllerSupport.listResponse(request, dispatchAlertFacade.listAlerts(filters));
  }

  @PostMapping("/alerts/{alertId}/ack")
  public ResponseEntity<Map<String, Object>> ackAlert(
    HttpServletRequest request,
    @PathVariable String alertId,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = InternalContractRequestSupport.body(payload);
    String requestId = InternalContractRequestSupport.requireRequestId(request, body);
    String operator = InternalContractRequestSupport.operator(body, "operator");
    return ApiSupport.ok(requestId, dispatchAlertFacade.ackAlert(alertId, body, requestId, operator));
  }

  @PostMapping("/alerts/{alertId}/close")
  public ResponseEntity<Map<String, Object>> closeAlert(
    HttpServletRequest request,
    @PathVariable String alertId,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = InternalContractRequestSupport.body(payload);
    String requestId = InternalContractRequestSupport.requireRequestId(request, body);
    String operator = InternalContractRequestSupport.operator(body, "operator");
    return ApiSupport.ok(requestId, dispatchAlertFacade.closeAlert(alertId, body, requestId, operator));
  }

  @PostMapping("/replan-jobs")
  public ResponseEntity<Map<String, Object>> triggerReplan(
    HttpServletRequest request,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = InternalContractRequestSupport.body(payload);
    String requestId = InternalContractRequestSupport.requireRequestId(request, body);
    dispatchAlertFacade.triggerReplanJob(body, requestId, "system");
    return ApiSupport.json(
      HttpStatus.ACCEPTED,
      requestId,
      Map.of("request_id", requestId, "accepted", true, "message", "Replan accepted.")
    );
  }

  @GetMapping("/replan-jobs/{jobNo}")
  public ResponseEntity<Map<String, Object>> getReplanJob(HttpServletRequest request, @PathVariable("jobNo") String jobNo) {
    String requestId = InternalContractRequestSupport.getOrCreateRequestId(request);
    return ApiSupport.ok(requestId, dispatchAlertFacade.getReplanJob(jobNo, requestId));
  }
}

