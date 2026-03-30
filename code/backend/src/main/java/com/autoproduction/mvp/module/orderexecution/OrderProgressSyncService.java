package com.autoproduction.mvp.module.orderexecution;

import com.autoproduction.mvp.core.MvpStoreService;
import org.springframework.stereotype.Service;

@Service
public class OrderProgressSyncService implements OrderProgressSyncPort {
  private final MvpStoreService store;

  public OrderProgressSyncService(MvpStoreService store) {
    this.store = store;
  }

  @Override
  public void syncOrderCompletionProgress() {
    store.syncOrderCompletionProgress();
  }
}
