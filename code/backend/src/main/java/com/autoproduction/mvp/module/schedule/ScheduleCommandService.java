package com.autoproduction.mvp.module.schedule;

import com.autoproduction.mvp.core.MvpStoreService;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ScheduleCommandService {
  private final MvpStoreService store;

  public ScheduleCommandService(MvpStoreService store) {
    this.store = store;
  }

  public Map<String, Object> generateSchedule(Map<String, Object> payload, String requestId, String operator) {
    return store.generateSchedule(payload, requestId, operator);
  }

  public Map<String, Object> publishSchedule(String versionNo, Map<String, Object> payload, String requestId, String operator) {
    return store.publishSchedule(versionNo, payload, requestId, operator);
  }

  public Map<String, Object> rollbackSchedule(String versionNo, Map<String, Object> payload, String requestId, String operator) {
    return store.rollbackSchedule(versionNo, payload, requestId, operator);
  }
}
