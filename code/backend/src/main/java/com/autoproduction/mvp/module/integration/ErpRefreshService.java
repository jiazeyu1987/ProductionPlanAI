package com.autoproduction.mvp.module.integration;

import com.autoproduction.mvp.module.integration.erp.ErpDataManager;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ErpRefreshService {
  private final ErpDataManager erpDataManager;

  public ErpRefreshService(ErpDataManager erpDataManager) {
    this.erpDataManager = erpDataManager;
  }

  public Map<String, Object> getRefreshStatus(String requestId) {
    return erpDataManager.getRefreshStatus(requestId);
  }

  public Map<String, Object> refreshManual(String requestId, String operator, String reason) {
    return erpDataManager.refreshManual(requestId, operator, reason);
  }
}
