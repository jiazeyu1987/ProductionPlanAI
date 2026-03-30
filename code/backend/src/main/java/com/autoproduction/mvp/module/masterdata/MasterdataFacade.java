package com.autoproduction.mvp.module.masterdata;

import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MasterdataFacade {
  private final MasterdataConfigQueryService masterdataConfigQueryService;
  private final MasterdataConfigCommandService masterdataConfigCommandService;
  private final MasterdataRouteCommandService masterdataRouteCommandService;
  private final ScheduleCalendarRuleQueryService scheduleCalendarRuleQueryService;
  private final ScheduleCalendarRuleCommandService scheduleCalendarRuleCommandService;

  public MasterdataFacade(
    MasterdataConfigQueryService masterdataConfigQueryService,
    MasterdataConfigCommandService masterdataConfigCommandService,
    MasterdataRouteCommandService masterdataRouteCommandService,
    ScheduleCalendarRuleQueryService scheduleCalendarRuleQueryService,
    ScheduleCalendarRuleCommandService scheduleCalendarRuleCommandService
  ) {
    this.masterdataConfigQueryService = masterdataConfigQueryService;
    this.masterdataConfigCommandService = masterdataConfigCommandService;
    this.masterdataRouteCommandService = masterdataRouteCommandService;
    this.scheduleCalendarRuleQueryService = scheduleCalendarRuleQueryService;
    this.scheduleCalendarRuleCommandService = scheduleCalendarRuleCommandService;
  }

  public Map<String, Object> getMasterdataConfig(String requestId) {
    return masterdataConfigQueryService.getMasterdataConfig(requestId);
  }

  public Map<String, Object> saveMasterdataConfig(Map<String, Object> payload, String requestId, String operator) {
    return masterdataConfigCommandService.saveMasterdataConfig(payload, requestId, operator);
  }

  public Map<String, Object> getScheduleCalendarRules(String requestId) {
    return scheduleCalendarRuleQueryService.getScheduleCalendarRules(requestId);
  }

  public Map<String, Object> saveScheduleCalendarRules(Map<String, Object> payload, String requestId, String operator) {
    return scheduleCalendarRuleCommandService.saveScheduleCalendarRules(payload, requestId, operator);
  }

  public Map<String, Object> createMasterdataRoute(Map<String, Object> payload, String requestId, String operator) {
    return masterdataRouteCommandService.createMasterdataRoute(payload, requestId, operator);
  }

  public Map<String, Object> updateMasterdataRoute(Map<String, Object> payload, String requestId, String operator) {
    return masterdataRouteCommandService.updateMasterdataRoute(payload, requestId, operator);
  }

  public Map<String, Object> copyMasterdataRoute(Map<String, Object> payload, String requestId, String operator) {
    return masterdataRouteCommandService.copyMasterdataRoute(payload, requestId, operator);
  }

  public Map<String, Object> deleteMasterdataRoute(Map<String, Object> payload, String requestId, String operator) {
    return masterdataRouteCommandService.deleteMasterdataRoute(payload, requestId, operator);
  }
}
