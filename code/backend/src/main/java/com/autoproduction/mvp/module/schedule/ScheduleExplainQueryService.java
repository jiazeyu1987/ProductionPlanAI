package com.autoproduction.mvp.module.schedule;

import com.autoproduction.mvp.core.MvpStoreService;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ScheduleExplainQueryService {
  private final MvpStoreService store;

  public ScheduleExplainQueryService(MvpStoreService store) {
    this.store = store;
  }

  public Map<String, Object> getVersionDiff(String versionNo, String compareWith, String requestId) {
    return store.getVersionDiff(versionNo, compareWith, requestId);
  }

  public Map<String, Object> getScheduleAlgorithm(String versionNo, String requestId) {
    return store.getScheduleAlgorithm(versionNo, requestId);
  }
}
