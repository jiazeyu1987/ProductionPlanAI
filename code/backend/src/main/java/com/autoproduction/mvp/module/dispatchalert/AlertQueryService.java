package com.autoproduction.mvp.module.dispatchalert;

import com.autoproduction.mvp.core.MvpStoreService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AlertQueryService {
  private final MvpStoreService store;

  public AlertQueryService(MvpStoreService store) {
    this.store = store;
  }

  public List<Map<String, Object>> listAlerts(Map<String, String> filters) {
    return store.listAlerts(filters);
  }
}
