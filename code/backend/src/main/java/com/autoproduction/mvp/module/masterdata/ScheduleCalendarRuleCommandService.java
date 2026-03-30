package com.autoproduction.mvp.module.masterdata;

import com.autoproduction.mvp.core.MvpStoreService;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ScheduleCalendarRuleCommandService {
  private final MvpStoreService store;

  public ScheduleCalendarRuleCommandService(MvpStoreService store) {
    this.store = store;
  }

  public Map<String, Object> saveScheduleCalendarRules(Map<String, Object> payload, String requestId, String operator) {
    return store.saveScheduleCalendarRules(payload, requestId, operator);
  }
}
