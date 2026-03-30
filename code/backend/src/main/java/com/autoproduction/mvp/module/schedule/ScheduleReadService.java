package com.autoproduction.mvp.module.schedule;

import com.autoproduction.mvp.core.MvpStoreService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ScheduleReadService {
  private final MvpStoreService store;

  public ScheduleReadService(MvpStoreService store) {
    this.store = store;
  }

  public List<Map<String, Object>> listSchedules() {
    return store.listSchedules();
  }

  public Map<String, Object> getLatestSchedule() {
    return store.getLatestSchedule();
  }

  public Map<String, Object> validateSchedule(String versionNo) {
    return store.validateSchedule(versionNo);
  }
}
