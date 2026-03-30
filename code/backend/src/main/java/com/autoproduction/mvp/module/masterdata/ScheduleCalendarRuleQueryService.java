package com.autoproduction.mvp.module.masterdata;

import com.autoproduction.mvp.core.MvpStoreService;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ScheduleCalendarRuleQueryService {
  private final MvpStoreService store;

  public ScheduleCalendarRuleQueryService(MvpStoreService store) {
    this.store = store;
  }

  public Map<String, Object> getScheduleCalendarRules(String requestId) {
    return store.getScheduleCalendarRules(requestId);
  }
}
