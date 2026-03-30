package com.autoproduction.mvp.module.masterdata;

import com.autoproduction.mvp.core.MvpStoreService;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MasterdataConfigCommandService {
  private final MvpStoreService store;

  public MasterdataConfigCommandService(MvpStoreService store) {
    this.store = store;
  }

  public Map<String, Object> saveMasterdataConfig(Map<String, Object> payload, String requestId, String operator) {
    return store.saveMasterdataConfig(payload, requestId, operator);
  }
}
