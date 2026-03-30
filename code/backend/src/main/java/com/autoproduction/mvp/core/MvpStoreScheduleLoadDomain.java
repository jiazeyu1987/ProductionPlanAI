package com.autoproduction.mvp.core;

import com.autoproduction.mvp.module.integration.erp.ErpDataManager;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
abstract class MvpStoreScheduleLoadDomain extends MvpStoreScheduleAlgorithmDomain {
  protected MvpStoreScheduleLoadDomain(ErpDataManager erpDataManager) {
    super(erpDataManager);
  }

  public List<Map<String, Object>> listScheduleVersions(Map<String, String> filters) {
    synchronized (lock) {
      return MvpStoreScheduleVersionListSupport.listScheduleVersions(this, filters);
    }
  }

  public List<Map<String, Object>> listScheduleTasks(String versionNo) {
    synchronized (lock) {
      return MvpStoreScheduleTaskListSupport.listScheduleTasks(this, versionNo);
    }
  }

  public List<Map<String, Object>> listScheduleDailyProcessLoad(String versionNo) {
    synchronized (lock) {
      return MvpStoreScheduleProcessLoadSupport.listScheduleDailyProcessLoad(this, versionNo);
    }
  }

  public List<Map<String, Object>> listScheduleShiftProcessLoad(String versionNo) {
    synchronized (lock) {
      return MvpStoreScheduleProcessLoadSupport.listScheduleShiftProcessLoad(this, versionNo);
    }
  }
}

