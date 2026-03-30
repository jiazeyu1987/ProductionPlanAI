package com.autoproduction.mvp.core;

import com.autoproduction.mvp.module.integration.erp.ErpDataManager;
import java.util.Map;

abstract class MvpStoreCommandDispatchSupport extends MvpStoreCommandReplanSupport {
  protected MvpStoreCommandDispatchSupport(ErpDataManager erpDataManager) {
    super(erpDataManager);
  }

  protected void applyDispatchCommand(Map<String, Object> command) {
    String targetOrderNo = string(command, "target_order_no", null);
    if (targetOrderNo == null) {
      return;
    }
    MvpDomain.Order order = state.orders.stream().filter(it -> it.orderNo.equals(targetOrderNo)).findFirst().orElse(null);
    if (order == null) {
      return;
    }
    String commandType = string(command, "command_type", "");
    if ("LOCK".equals(commandType)) {
      order.lockFlag = true;
    } else if ("UNLOCK".equals(commandType)) {
      order.lockFlag = false;
    } else if ("FREEZE".equals(commandType)) {
      order.frozen = true;
    } else if ("UNFREEZE".equals(commandType)) {
      order.frozen = false;
    } else if ("PRIORITY".equals(commandType)) {
      order.urgent = true;
    } else if ("UNPRIORITY".equals(commandType)) {
      order.urgent = false;
    }
  }
}

