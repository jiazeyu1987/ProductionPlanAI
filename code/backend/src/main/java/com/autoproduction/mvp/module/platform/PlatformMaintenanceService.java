package com.autoproduction.mvp.module.platform;

import com.autoproduction.mvp.core.MvpStoreService;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PlatformMaintenanceService {
  private final MvpStoreService store;

  public PlatformMaintenanceService(MvpStoreService store) {
    this.store = store;
  }

  public Map<String, Object> reset() {
    store.reset();
    return Map.of("reset", true);
  }
}
