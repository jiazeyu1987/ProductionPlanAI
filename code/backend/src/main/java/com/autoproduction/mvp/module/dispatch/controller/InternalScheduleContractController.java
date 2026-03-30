package com.autoproduction.mvp.module.dispatch.controller;

import com.autoproduction.mvp.api.ApiSupport;
import com.autoproduction.mvp.api.ContractControllerSupport;
import com.autoproduction.mvp.core.MvpServiceException;
import com.autoproduction.mvp.module.schedule.ScheduleFacade;
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
public class InternalScheduleContractController {
  private final ScheduleFacade scheduleFacade;

  public InternalScheduleContractController(ScheduleFacade scheduleFacade) {
    this.scheduleFacade = scheduleFacade;
  }

  @GetMapping("/schedule-versions")
  public ResponseEntity<Map<String, Object>> listScheduleVersions(HttpServletRequest request, @RequestParam Map<String, String> filters) {
    return ContractControllerSupport.listResponse(request, scheduleFacade.listScheduleVersions(filters));
  }

  @GetMapping("/schedule-versions/{versionNo}/tasks")
  public ResponseEntity<Map<String, Object>> listScheduleTasks(HttpServletRequest request, @PathVariable("versionNo") String versionNo) {
    return ContractControllerSupport.listResponse(request, scheduleFacade.listScheduleTasks(versionNo));
  }

  @GetMapping("/schedule-versions/{versionNo}/daily-process-load")
  public ResponseEntity<Map<String, Object>> listScheduleDailyProcessLoad(
    HttpServletRequest request,
    @PathVariable("versionNo") String versionNo
  ) {
    return ContractControllerSupport.listResponse(request, scheduleFacade.listScheduleDailyProcessLoad(versionNo));
  }

  @GetMapping("/schedule-versions/{versionNo}/shift-process-load")
  public ResponseEntity<Map<String, Object>> listScheduleShiftProcessLoad(
    HttpServletRequest request,
    @PathVariable("versionNo") String versionNo
  ) {
    return ContractControllerSupport.listResponse(request, scheduleFacade.listScheduleShiftProcessLoad(versionNo));
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
    String requestId = InternalContractRequestSupport.getOrCreateRequestId(request);
    return ApiSupport.ok(requestId, scheduleFacade.getVersionDiff(versionNo, compareWith, requestId));
  }

  @GetMapping("/schedule-versions/{versionNo}/algorithm")
  public ResponseEntity<Map<String, Object>> getScheduleAlgorithm(
    HttpServletRequest request,
    @PathVariable("versionNo") String versionNo
  ) {
    String requestId = InternalContractRequestSupport.getOrCreateRequestId(request);
    return ApiSupport.ok(requestId, scheduleFacade.getScheduleAlgorithm(versionNo, requestId));
  }

  @PostMapping("/schedule-versions/{versionNo}/publish")
  public ResponseEntity<Map<String, Object>> publishVersion(
    HttpServletRequest request,
    @PathVariable("versionNo") String versionNo,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = InternalContractRequestSupport.body(payload);
    String requestId = InternalContractRequestSupport.requireRequestId(request, body);
    String operator = InternalContractRequestSupport.operator(body, "publisher");
    return ApiSupport.ok(requestId, scheduleFacade.publishSchedule(versionNo, body, requestId, operator));
  }

  @PostMapping("/schedule-versions/{versionNo}/rollback")
  public ResponseEntity<Map<String, Object>> rollbackVersion(
    HttpServletRequest request,
    @PathVariable("versionNo") String versionNo,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = InternalContractRequestSupport.body(payload);
    String requestId = InternalContractRequestSupport.requireRequestId(request, body);
    String operator = InternalContractRequestSupport.operator(body, "publisher");
    return ApiSupport.ok(requestId, scheduleFacade.rollbackSchedule(versionNo, body, requestId, operator));
  }
}

