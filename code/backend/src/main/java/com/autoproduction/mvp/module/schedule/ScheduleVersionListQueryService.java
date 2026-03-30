package com.autoproduction.mvp.module.schedule;

import com.autoproduction.mvp.core.MvpStoreService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ScheduleVersionListQueryService {
  private final MvpStoreService store;

  public ScheduleVersionListQueryService(MvpStoreService store) {
    this.store = store;
  }

  public List<Map<String, Object>> listScheduleVersions(Map<String, String> filters) {
    return store.listScheduleVersions(filters);
  }

  public List<Map<String, Object>> listScheduleTasks(String versionNo) {
    return store.listScheduleTasks(versionNo);
  }

  public List<Map<String, Object>> listScheduleDailyProcessLoad(String versionNo) {
    return store.listScheduleDailyProcessLoad(versionNo);
  }

  public List<Map<String, Object>> listScheduleShiftProcessLoad(String versionNo) {
    return store.listScheduleShiftProcessLoad(versionNo);
  }
}
