package com.autoproduction.mvp.module.schedule;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ScheduleFacade {
  private final ScheduleVersionListQueryService scheduleVersionListQueryService;
  private final ScheduleExplainQueryService scheduleExplainQueryService;
  private final ScheduleCommandService scheduleCommandService;
  private final ScheduleReadService scheduleReadService;

  public ScheduleFacade(
    ScheduleVersionListQueryService scheduleVersionListQueryService,
    ScheduleExplainQueryService scheduleExplainQueryService,
    ScheduleCommandService scheduleCommandService,
    ScheduleReadService scheduleReadService
  ) {
    this.scheduleVersionListQueryService = scheduleVersionListQueryService;
    this.scheduleExplainQueryService = scheduleExplainQueryService;
    this.scheduleCommandService = scheduleCommandService;
    this.scheduleReadService = scheduleReadService;
  }

  public List<Map<String, Object>> listScheduleVersions(Map<String, String> filters) {
    return scheduleVersionListQueryService.listScheduleVersions(filters);
  }

  public List<Map<String, Object>> listScheduleTasks(String versionNo) {
    return scheduleVersionListQueryService.listScheduleTasks(versionNo);
  }

  public List<Map<String, Object>> listScheduleDailyProcessLoad(String versionNo) {
    return scheduleVersionListQueryService.listScheduleDailyProcessLoad(versionNo);
  }

  public List<Map<String, Object>> listScheduleShiftProcessLoad(String versionNo) {
    return scheduleVersionListQueryService.listScheduleShiftProcessLoad(versionNo);
  }

  public Map<String, Object> getVersionDiff(String versionNo, String compareWith, String requestId) {
    return scheduleExplainQueryService.getVersionDiff(versionNo, compareWith, requestId);
  }

  public Map<String, Object> getScheduleAlgorithm(String versionNo, String requestId) {
    return scheduleExplainQueryService.getScheduleAlgorithm(versionNo, requestId);
  }

  public Map<String, Object> publishSchedule(String versionNo, Map<String, Object> payload, String requestId, String operator) {
    return scheduleCommandService.publishSchedule(versionNo, payload, requestId, operator);
  }

  public Map<String, Object> rollbackSchedule(String versionNo, Map<String, Object> payload, String requestId, String operator) {
    return scheduleCommandService.rollbackSchedule(versionNo, payload, requestId, operator);
  }

  public Map<String, Object> generateSchedule(Map<String, Object> payload, String requestId, String operator) {
    return scheduleCommandService.generateSchedule(payload, requestId, operator);
  }

  public List<Map<String, Object>> listSchedules() {
    return scheduleReadService.listSchedules();
  }

  public Map<String, Object> getLatestSchedule() {
    return scheduleReadService.getLatestSchedule();
  }

  public Map<String, Object> validateSchedule(String versionNo) {
    return scheduleReadService.validateSchedule(versionNo);
  }
}
