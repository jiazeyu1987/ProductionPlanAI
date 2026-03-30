package com.autoproduction.mvp.module.integration;

import com.autoproduction.mvp.core.MvpStoreService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MaterialAvailabilityQueryAdapter {
  private final MvpStoreService store;

  public MaterialAvailabilityQueryAdapter(MvpStoreService store) {
    this.store = store;
  }

  public List<Map<String, Object>> listMaterialAvailability() {
    return store.listMaterialAvailability();
  }
}
