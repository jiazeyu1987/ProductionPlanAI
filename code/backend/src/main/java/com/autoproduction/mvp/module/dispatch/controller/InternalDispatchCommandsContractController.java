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
public class InternalDispatchCommandsContractController {
  private final DispatchAlertFacade dispatchAlertFacade;

  public InternalDispatchCommandsContractController(DispatchAlertFacade dispatchAlertFacade) {
    this.dispatchAlertFacade = dispatchAlertFacade;
  }

  @GetMapping("/dispatch-commands")
  public ResponseEntity<Map<String, Object>> listDispatchCommands(HttpServletRequest request, @RequestParam Map<String, String> filters) {
    return ContractControllerSupport.listResponse(request, dispatchAlertFacade.listDispatchCommands(filters));
  }

  @PostMapping("/dispatch-commands")
  public ResponseEntity<Map<String, Object>> createDispatchCommand(
    HttpServletRequest request,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = InternalContractRequestSupport.body(payload);
    String requestId = InternalContractRequestSupport.requireRequestId(request, body);
    return ApiSupport.json(HttpStatus.ACCEPTED, requestId, dispatchAlertFacade.createDispatchCommand(body, requestId, "dispatch"));
  }

  @PostMapping("/dispatch-commands/{commandId}/approvals")
  public ResponseEntity<Map<String, Object>> approveDispatchCommand(
    HttpServletRequest request,
    @PathVariable String commandId,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = InternalContractRequestSupport.body(payload);
    String requestId = InternalContractRequestSupport.requireRequestId(request, body);
    return ApiSupport.ok(requestId, dispatchAlertFacade.approveDispatchCommand(commandId, body, requestId, "approver"));
  }
}

