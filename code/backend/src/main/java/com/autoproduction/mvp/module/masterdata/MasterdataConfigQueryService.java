package com.autoproduction.mvp.module.masterdata;

import com.autoproduction.mvp.core.MvpStoreService;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MasterdataConfigQueryService {
  private final MvpStoreService store;

  public MasterdataConfigQueryService(MvpStoreService store) {
    this.store = store;
  }

  public Map<String, Object> getMasterdataConfig(String requestId) {
    return store.getMasterdataConfig(requestId);
  }
}
