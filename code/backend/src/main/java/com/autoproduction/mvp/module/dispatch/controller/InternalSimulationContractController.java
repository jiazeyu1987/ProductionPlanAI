package com.autoproduction.mvp.module.dispatch.controller;

import com.autoproduction.mvp.api.ApiSupport;
import com.autoproduction.mvp.api.ContractControllerSupport;
import com.autoproduction.mvp.module.dispatchalert.DispatchAlertFacade;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(InternalContractController.BASE_PATH)
public class InternalSimulationContractController {
  private final DispatchAlertFacade dispatchAlertFacade;

  public InternalSimulationContractController(DispatchAlertFacade dispatchAlertFacade) {
    this.dispatchAlertFacade = dispatchAlertFacade;
  }

  @GetMapping("/simulation/state")
  public ResponseEntity<Map<String, Object>> getSimulationState(HttpServletRequest request) {
    String requestId = InternalContractRequestSupport.getOrCreateRequestId(request);
    return ApiSupport.ok(requestId, dispatchAlertFacade.getSimulationState(requestId));
  }

  @GetMapping("/simulation/events")
  public ResponseEntity<Map<String, Object>> listSimulationEvents(HttpServletRequest request, @RequestParam Map<String, String> filters) {
    return ContractControllerSupport.listResponse(request, dispatchAlertFacade.listSimulationEvents(filters));
  }

  @PostMapping("/simulation/reset")
  public ResponseEntity<Map<String, Object>> resetSimulation(
    HttpServletRequest request,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = InternalContractRequestSupport.body(payload);
    String requestId = InternalContractRequestSupport.requireRequestId(request, body);
    return ApiSupport.ok(requestId, dispatchAlertFacade.resetSimulation(body, requestId, "simulator"));
  }

  @PostMapping("/simulation/run")
  public ResponseEntity<Map<String, Object>> runSimulation(
    HttpServletRequest request,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = InternalContractRequestSupport.body(payload);
    String requestId = InternalContractRequestSupport.requireRequestId(request, body);
    return ApiSupport.ok(requestId, dispatchAlertFacade.runSimulation(body, requestId, "simulator"));
  }

  @PostMapping("/simulation/manual/add-production-order")
  public ResponseEntity<Map<String, Object>> addManualProductionOrder(
    HttpServletRequest request,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = InternalContractRequestSupport.body(payload);
    String requestId = InternalContractRequestSupport.requireRequestId(request, body);
    return ApiSupport.ok(requestId, dispatchAlertFacade.addManualProductionOrder(body, requestId, "simulator"));
  }

  @PostMapping("/simulation/manual/advance-day")
  public ResponseEntity<Map<String, Object>> advanceManualSimulationDay(
    HttpServletRequest request,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = InternalContractRequestSupport.body(payload);
    String requestId = InternalContractRequestSupport.requireRequestId(request, body);
    return ApiSupport.ok(requestId, dispatchAlertFacade.advanceManualOneDay(body, requestId, "simulator"));
  }

  @PostMapping("/simulation/manual/reset")
  public ResponseEntity<Map<String, Object>> resetManualSimulation(
    HttpServletRequest request,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = InternalContractRequestSupport.body(payload);
    String requestId = InternalContractRequestSupport.requireRequestId(request, body);
    return ApiSupport.ok(requestId, dispatchAlertFacade.resetManualSimulation(body, requestId, "simulator"));
  }
}

