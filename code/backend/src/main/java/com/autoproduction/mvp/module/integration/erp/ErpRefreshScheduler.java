package com.autoproduction.mvp.module.integration.erp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ErpRefreshScheduler {
  private final ErpDataManager erpDataManager;
  private final boolean refreshEnabled;

  public ErpRefreshScheduler(
    ErpDataManager erpDataManager,
    @Value("${mvp.erp.refresh.enabled:true}") boolean refreshEnabled
  ) {
    this.erpDataManager = erpDataManager;
    this.refreshEnabled = refreshEnabled;
  }

  @Scheduled(
    fixedDelayString = "${mvp.erp.refresh.fixed-delay-ms:60000}",
    initialDelayString = "${mvp.erp.refresh.initial-delay-ms:10000}"
  )
  public void refreshOnSchedule() {
    if (!refreshEnabled) {
      return;
    }
    erpDataManager.refreshScheduled();
  }
}
