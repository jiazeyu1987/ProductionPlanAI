package com.autoproduction.mvp.core;

import com.autoproduction.mvp.module.integration.erp.ErpDataManager;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
abstract class MvpStoreOrderDomain extends MvpStoreScheduleDomain {
  protected MvpStoreOrderDomain(ErpDataManager erpDataManager) {
    super(erpDataManager);
  }

  public void reset() {
    synchronized (lock) {
      MvpStoreOrderResetSupport.reset(this);
      MvpStoreOrderPoolSnapshotSupport.persistFrom(this);
    }
  }

  public List<Map<String, Object>> listOrders() {
    synchronized (lock) {
      return MvpStoreOrderQuerySupport.listOrders(this);
    }
  }

  public List<Map<String, Object>> listOrderPool(Map<String, String> filters) {
    synchronized (lock) {
      return MvpStoreOrderQuerySupport.listOrderPool(this, filters);
    }
  }

  public List<Map<String, Object>> listOrderPoolMaterials(String orderNo) {
    return listOrderPoolMaterials(orderNo, false);
  }

  public List<Map<String, Object>> listOrderPoolMaterials(String orderNo, boolean refreshFromErp) {
    synchronized (lock) {
      return MvpStoreOrderMaterialsSupport.listOrderPoolMaterials(this, orderNo, refreshFromErp);
    }
  }

  public List<Map<String, Object>> listMaterialChildrenByParentCode(String parentMaterialCode) {
    return listMaterialChildrenByParentCode(parentMaterialCode, false);
  }

  public List<Map<String, Object>> listMaterialChildrenByParentCode(String parentMaterialCode, boolean refreshFromErp) {
    synchronized (lock) {
      return MvpStoreOrderMaterialsSupport.listMaterialChildrenByParentCode(this, parentMaterialCode, refreshFromErp);
    }
  }

  public List<Map<String, Object>> listOrderMaterialAvailability(boolean refreshFromErp) {
    synchronized (lock) {
      return deepCopyList(buildOrderMaterialAvailabilityRowsFromOrderPool(refreshFromErp));
    }
  }

  protected List<Map<String, Object>> buildOrderMaterialAvailabilityUnionRows(boolean refreshFromErp) {
    return MvpStoreOrderMaterialAvailabilitySupport.buildOrderMaterialAvailabilityUnionRows(this, refreshFromErp);
  }

  protected List<Map<String, Object>> buildOrderMaterialAvailabilityRowsFromOrderPool(boolean refreshFromErp) {
    return MvpStoreOrderMaterialAvailabilitySupport.buildOrderMaterialAvailabilityRowsFromOrderPool(this, refreshFromErp);
  }

  protected OrderMaterialConstraintSnapshot buildOrderMaterialConstraintSnapshot(boolean refreshFromErp) {
    return MvpStoreOrderMaterialAvailabilitySupport.buildOrderMaterialConstraintSnapshot(this, refreshFromErp);
  }

  protected OrderMaterialConstraintSnapshot buildOrderMaterialConstraintSnapshotRows(
    List<OrderMaterialWorkRow> workRows,
    java.util.Set<String> allMaterialCodes,
    boolean refreshFromErp
  ) {
    return MvpStoreOrderMaterialAvailabilitySupport.buildOrderMaterialConstraintSnapshotRows(
      this,
      workRows,
      allMaterialCodes,
      refreshFromErp
    );
  }

  protected Map<String, Map<String, Object>> queryInventoryByMaterialCodesInParallel(
    List<String> materialCodes,
    boolean refreshFromErp
  ) {
    return MvpStoreOrderMaterialAvailabilitySupport.queryInventoryByMaterialCodesInParallel(
      this,
      materialCodes,
      refreshFromErp
    );
  }

  protected void applyOrderMaterialConstraintsForSchedule(
    MvpDomain.State scheduleState,
    Map<String, OrderMaterialConstraint> constraintByOrderNo
  ) {
    MvpStoreOrderMaterialAvailabilitySupport.applyOrderMaterialConstraintsForSchedule(this, scheduleState, constraintByOrderNo);
  }

  protected static boolean looksLikeErpProductionOrderNo(String orderNo) {
    String normalized = normalizeCode(orderNo);
    if (normalized == null || normalized.isBlank()) {
      return false;
    }
    return normalized.matches("\\d{3}MO\\d+") || normalized.matches("MO-[A-Z0-9-]+");
  }

  protected String resolveFirstProcessCode(String productCode) {
    String normalizedProductCode = normalizeCode(productCode);
    if (normalizedProductCode == null || normalizedProductCode.isBlank()) {
      return "";
    }
    List<MvpDomain.ProcessStep> steps = state.processRoutes.get(normalizedProductCode);
    if (steps == null || steps.isEmpty()) {
      steps = state.processRoutes.get(productCode);
    }
    if (steps == null || steps.isEmpty()) {
      return "";
    }
    return normalizeCode(steps.get(0).processCode);
  }

  public Map<String, Object> upsertOrder(Map<String, Object> payload, String requestId, String operator) {
    synchronized (lock) {
      Map<String, Object> result = MvpStoreOrderWriteSupport.upsertOrder(this, payload, requestId, operator);
      MvpStoreOrderPoolSnapshotSupport.persistFrom(this);
      return result;
    }
  }

  public Map<String, Object> patchOrder(String orderNo, Map<String, Object> patch, String requestId, String operator) {
    synchronized (lock) {
      Map<String, Object> result = MvpStoreOrderWriteSupport.patchOrder(this, orderNo, patch, requestId, operator);
      MvpStoreOrderPoolSnapshotSupport.persistFrom(this);
      return result;
    }
  }

  public Map<String, Object> deleteOrder(String orderNo, String requestId, String operator) {
    synchronized (lock) {
      Map<String, Object> result = MvpStoreOrderWriteSupport.deleteOrder(this, orderNo, requestId, operator);
      MvpStoreOrderPoolSnapshotSupport.persistFrom(this);
      return result;
    }
  }

  public Map<String, Object> importProductionOrdersFromErp(Map<String, Object> payload, String requestId, String operator) {
    synchronized (lock) {
      Map<String, Object> result = runIdempotent(
        requestId,
        "ERP_IMPORT_PRODUCTION_ORDERS",
        () -> MvpStoreOrderErpImportSupport.importProductionOrdersFromErp(this, payload, requestId, operator)
      );
      MvpStoreOrderPoolSnapshotSupport.persistFrom(this);
      return result;
    }
  }

  public void syncOrderCompletionProgress() {
    synchronized (lock) {
      MvpStoreOrderWriteSupport.syncOrderCompletionProgress(this);
    }
  }
}
