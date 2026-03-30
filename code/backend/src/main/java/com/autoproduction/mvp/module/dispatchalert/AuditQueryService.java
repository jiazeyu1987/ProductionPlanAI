package com.autoproduction.mvp.module.dispatchalert;

import com.autoproduction.mvp.core.MvpStoreService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AuditQueryService {
  private final MvpStoreService store;

  public AuditQueryService(MvpStoreService store) {
    this.store = store;
  }

  public List<Map<String, Object>> listAuditLogs(Map<String, String> filters) {
    return store.listAuditLogs(filters);
  }
}
