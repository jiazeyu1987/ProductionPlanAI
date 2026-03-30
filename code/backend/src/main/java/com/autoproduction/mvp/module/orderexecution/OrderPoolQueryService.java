package com.autoproduction.mvp.module.orderexecution;

import com.autoproduction.mvp.core.MvpStoreService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class OrderPoolQueryService {
  private final MvpStoreService store;

  public OrderPoolQueryService(MvpStoreService store) {
    this.store = store;
  }

  public List<Map<String, Object>> listOrderPool(Map<String, String> filters) {
    return store.listOrderPool(filters);
  }
}
