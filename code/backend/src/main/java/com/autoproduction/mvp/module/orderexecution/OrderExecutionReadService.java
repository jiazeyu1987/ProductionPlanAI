package com.autoproduction.mvp.module.orderexecution;

import com.autoproduction.mvp.core.MvpStoreService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class OrderExecutionReadService {
  private final MvpStoreService store;

  public OrderExecutionReadService(MvpStoreService store) {
    this.store = store;
  }

  public List<Map<String, Object>> listOrders() {
    return store.listOrders();
  }

  public List<Map<String, Object>> listReportings() {
    return store.listReportings();
  }
}
