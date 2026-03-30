package com.autoproduction.mvp.core;

import com.autoproduction.mvp.core.MvpStoreRuntimeBase.OrderMaterialConstraint;
import com.autoproduction.mvp.core.MvpStoreRuntimeBase.OrderMaterialConstraintSnapshot;
import com.autoproduction.mvp.core.MvpStoreRuntimeBase.OrderMaterialWorkRow;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class MvpStoreOrderMaterialAvailabilitySupport {
  private MvpStoreOrderMaterialAvailabilitySupport() {}

  static List<Map<String, Object>> buildOrderMaterialAvailabilityUnionRows(MvpStoreOrderDomain domain, boolean refreshFromErp) {
    return MvpStoreOrderMaterialAvailabilityUnionSupport.buildOrderMaterialAvailabilityUnionRows(domain, refreshFromErp);
  }

  static List<Map<String, Object>> buildOrderMaterialAvailabilityRowsFromOrderPool(MvpStoreOrderDomain domain, boolean refreshFromErp) {
    return MvpStoreOrderMaterialConstraintSupport.buildOrderMaterialAvailabilityRowsFromOrderPool(domain, refreshFromErp);
  }

  static OrderMaterialConstraintSnapshot buildOrderMaterialConstraintSnapshot(MvpStoreOrderDomain domain, boolean refreshFromErp) {
    return MvpStoreOrderMaterialConstraintSupport.buildOrderMaterialConstraintSnapshot(domain, refreshFromErp);
  }

  static OrderMaterialConstraintSnapshot buildOrderMaterialConstraintSnapshotRows(
    MvpStoreOrderDomain domain,
    List<OrderMaterialWorkRow> workRows,
    Set<String> allMaterialCodes,
    boolean refreshFromErp
  ) {
    return MvpStoreOrderMaterialConstraintSupport.buildOrderMaterialConstraintSnapshotRows(
      domain,
      workRows,
      allMaterialCodes,
      refreshFromErp
    );
  }

  static Map<String, Map<String, Object>> queryInventoryByMaterialCodesInParallel(
    MvpStoreOrderDomain domain,
    List<String> materialCodes,
    boolean refreshFromErp
  ) {
    return MvpStoreOrderInventoryQuerySupport.queryInventoryByMaterialCodesInParallel(domain, materialCodes, refreshFromErp);
  }

  static void applyOrderMaterialConstraintsForSchedule(
    MvpStoreOrderDomain domain,
    MvpDomain.State scheduleState,
    Map<String, OrderMaterialConstraint> constraintByOrderNo
  ) {
    MvpStoreOrderMaterialConstraintSupport.applyOrderMaterialConstraintsForSchedule(domain, scheduleState, constraintByOrderNo);
  }
}

