package com.autoproduction.mvp.module.orderexecution;

import com.autoproduction.mvp.core.MvpStoreService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class OrderMaterialQueryService {
  private final MvpStoreService store;

  public OrderMaterialQueryService(MvpStoreService store) {
    this.store = store;
  }

  public List<Map<String, Object>> listOrderPoolMaterials(String orderNo, boolean refresh) {
    return store.listOrderPoolMaterials(orderNo, refresh);
  }

  public List<Map<String, Object>> listMaterialChildrenByParentCode(String parentMaterialCode, boolean refresh) {
    return store.listMaterialChildrenByParentCode(parentMaterialCode, refresh);
  }

  public List<Map<String, Object>> listOrderMaterialAvailability(boolean refresh) {
    return store.listOrderMaterialAvailability(refresh);
  }
}
