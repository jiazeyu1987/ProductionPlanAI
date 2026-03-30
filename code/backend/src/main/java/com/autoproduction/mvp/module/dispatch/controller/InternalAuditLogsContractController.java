package com.autoproduction.mvp.module.dispatch.controller;

import com.autoproduction.mvp.api.ContractControllerSupport;
import com.autoproduction.mvp.module.dispatchalert.DispatchAlertFacade;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(InternalContractController.BASE_PATH)
public class InternalAuditLogsContractController {
  private final DispatchAlertFacade dispatchAlertFacade;

  public InternalAuditLogsContractController(DispatchAlertFacade dispatchAlertFacade) {
    this.dispatchAlertFacade = dispatchAlertFacade;
  }

  @GetMapping("/audit-logs")
  public ResponseEntity<Map<String, Object>> listAuditLogs(HttpServletRequest request, @RequestParam Map<String, String> filters) {
    return ContractControllerSupport.listResponse(request, dispatchAlertFacade.listAuditLogs(filters));
  }
}

