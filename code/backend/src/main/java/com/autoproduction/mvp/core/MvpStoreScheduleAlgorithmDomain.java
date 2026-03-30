package com.autoproduction.mvp.core;

import com.autoproduction.mvp.module.integration.erp.ErpDataManager;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
abstract class MvpStoreScheduleAlgorithmDomain extends MvpStoreIntegrationDomain {
  protected MvpStoreScheduleAlgorithmDomain(ErpDataManager erpDataManager) {
    super(erpDataManager);
  }

  public Map<String, Object> getScheduleAlgorithm(String versionNo, String requestId) {
    synchronized (lock) {
      return MvpStoreScheduleAlgorithmViewSupport.getScheduleAlgorithm(this, versionNo, requestId);
    }
  }

  public Map<String, Object> getVersionDiff(String versionNo, String compareWith, String requestId) {
    synchronized (lock) {
      return MvpStoreScheduleVersionDiffSupport.getVersionDiff(this, versionNo, compareWith, requestId);
    }
  }
}

