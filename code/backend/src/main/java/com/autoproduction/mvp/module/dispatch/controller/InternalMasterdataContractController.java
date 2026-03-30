package com.autoproduction.mvp.module.dispatch.controller;

import com.autoproduction.mvp.api.ApiSupport;
import com.autoproduction.mvp.module.masterdata.MasterdataFacade;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(InternalContractController.BASE_PATH)
public class InternalMasterdataContractController {
  private final MasterdataFacade masterdataFacade;

  public InternalMasterdataContractController(MasterdataFacade masterdataFacade) {
    this.masterdataFacade = masterdataFacade;
  }

  @GetMapping("/masterdata/config")
  public ResponseEntity<Map<String, Object>> getMasterdataConfig(HttpServletRequest request) {
    String requestId = InternalContractRequestSupport.getOrCreateRequestId(request);
    return ApiSupport.ok(requestId, masterdataFacade.getMasterdataConfig(requestId));
  }

  @PostMapping("/masterdata/config")
  public ResponseEntity<Map<String, Object>> saveMasterdataConfig(
    HttpServletRequest request,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = InternalContractRequestSupport.body(payload);
    String requestId = InternalContractRequestSupport.requireRequestId(request, body);
    String operator = InternalContractRequestSupport.operator(body, "masterdata-admin");
    return ApiSupport.ok(requestId, masterdataFacade.saveMasterdataConfig(body, requestId, operator));
  }

  @GetMapping("/schedule-calendar/rules")
  public ResponseEntity<Map<String, Object>> getScheduleCalendarRules(HttpServletRequest request) {
    String requestId = InternalContractRequestSupport.getOrCreateRequestId(request);
    return ApiSupport.ok(requestId, masterdataFacade.getScheduleCalendarRules(requestId));
  }

  @PostMapping("/schedule-calendar/rules")
  public ResponseEntity<Map<String, Object>> saveScheduleCalendarRules(
    HttpServletRequest request,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = InternalContractRequestSupport.body(payload);
    String requestId = InternalContractRequestSupport.requireRequestId(request, body);
    String operator = InternalContractRequestSupport.operator(body, "calendar-admin");
    return ApiSupport.ok(requestId, masterdataFacade.saveScheduleCalendarRules(body, requestId, operator));
  }

  @PostMapping("/masterdata/routes/create")
  public ResponseEntity<Map<String, Object>> createMasterdataRoute(
    HttpServletRequest request,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = InternalContractRequestSupport.body(payload);
    String requestId = InternalContractRequestSupport.requireRequestId(request, body);
    String operator = InternalContractRequestSupport.operator(body, "masterdata-admin");
    return ApiSupport.ok(requestId, masterdataFacade.createMasterdataRoute(body, requestId, operator));
  }

  @PostMapping("/masterdata/routes/update")
  public ResponseEntity<Map<String, Object>> updateMasterdataRoute(
    HttpServletRequest request,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = InternalContractRequestSupport.body(payload);
    String requestId = InternalContractRequestSupport.requireRequestId(request, body);
    String operator = InternalContractRequestSupport.operator(body, "masterdata-admin");
    return ApiSupport.ok(requestId, masterdataFacade.updateMasterdataRoute(body, requestId, operator));
  }

  @PostMapping("/masterdata/routes/copy")
  public ResponseEntity<Map<String, Object>> copyMasterdataRoute(
    HttpServletRequest request,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = InternalContractRequestSupport.body(payload);
    String requestId = InternalContractRequestSupport.requireRequestId(request, body);
    String operator = InternalContractRequestSupport.operator(body, "masterdata-admin");
    return ApiSupport.ok(requestId, masterdataFacade.copyMasterdataRoute(body, requestId, operator));
  }

  @PostMapping("/masterdata/routes/delete")
  public ResponseEntity<Map<String, Object>> deleteMasterdataRoute(
    HttpServletRequest request,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = InternalContractRequestSupport.body(payload);
    String requestId = InternalContractRequestSupport.requireRequestId(request, body);
    String operator = InternalContractRequestSupport.operator(body, "masterdata-admin");
    return ApiSupport.ok(requestId, masterdataFacade.deleteMasterdataRoute(body, requestId, operator));
  }
}

