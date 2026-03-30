package com.autoproduction.mvp.module.dispatch.controller;

import com.autoproduction.mvp.api.ApiSupport;
import com.autoproduction.mvp.api.ContractControllerSupport;
import com.autoproduction.mvp.module.integration.IntegrationFacade;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
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
public class InternalIntegrationContractController {
  private final IntegrationFacade integrationFacade;

  public InternalIntegrationContractController(IntegrationFacade integrationFacade) {
    this.integrationFacade = integrationFacade;
  }

  @GetMapping("/integration/inbox")
  public ResponseEntity<Map<String, Object>> listIntegrationInbox(HttpServletRequest request, @RequestParam Map<String, String> filters) {
    return ContractControllerSupport.listResponse(request, integrationFacade.listIntegrationInbox(filters));
  }

  @GetMapping("/integration/outbox")
  public ResponseEntity<Map<String, Object>> listIntegrationOutbox(HttpServletRequest request, @RequestParam Map<String, String> filters) {
    return ContractControllerSupport.listResponse(request, integrationFacade.listIntegrationOutbox(filters));
  }

  @PostMapping("/integration/outbox/{messageId}/retry")
  public ResponseEntity<Map<String, Object>> retryOutbox(
    HttpServletRequest request,
    @PathVariable String messageId,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = InternalContractRequestSupport.body(payload);
    String requestId = InternalContractRequestSupport.requireRequestId(request, body);
    String operator = InternalContractRequestSupport.operator(body, "admin");
    return ApiSupport.ok(requestId, integrationFacade.retryOutboxMessage(messageId, requestId, operator));
  }

  @GetMapping("/integration/erp/refresh-status")
  public ResponseEntity<Map<String, Object>> getErpRefreshStatus(HttpServletRequest request) {
    String requestId = InternalContractRequestSupport.getOrCreateRequestId(request);
    return ApiSupport.ok(requestId, integrationFacade.getErpRefreshStatus(requestId));
  }

  @PostMapping("/integration/erp/refresh")
  public ResponseEntity<Map<String, Object>> refreshErpData(
    HttpServletRequest request,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = InternalContractRequestSupport.body(payload);
    String requestId = InternalContractRequestSupport.requireRequestId(request, body);
    String operator = InternalContractRequestSupport.operator(body, "integration-admin");
    String reason = body.get("reason") == null ? null : String.valueOf(body.get("reason"));
    return ApiSupport.ok(requestId, integrationFacade.refreshErpData(requestId, operator, reason));
  }
}

