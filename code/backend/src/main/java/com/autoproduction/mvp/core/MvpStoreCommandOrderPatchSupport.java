package com.autoproduction.mvp.core;

import com.autoproduction.mvp.module.integration.erp.ErpDataManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

abstract class MvpStoreCommandOrderPatchSupport extends MvpStoreCommandDispatchSupport {
  protected MvpStoreCommandOrderPatchSupport(ErpDataManager erpDataManager) {
    super(erpDataManager);
  }

  protected MvpDomain.Order fromOrderPayload(Map<String, Object> payload) {
    return MvpStoreCommandOrderPayloadSupport.fromOrderPayload(this, payload);
  }

  protected void applyOrderPatch(MvpDomain.Order order, Map<String, Object> patch) {
    MvpStoreCommandOrderPatchApplySupport.applyOrderPatch(this, order, patch);
  }

  protected MvpDomain.OrderBusinessData parseBusinessData(
    Map<String, Object> payload,
    String orderNo,
    List<MvpDomain.OrderItem> orderItems,
    LocalDate dueDate
  ) {
    return MvpStoreCommandOrderPayloadSupport.parseBusinessData(this, payload, orderNo, orderItems, dueDate);
  }

  protected void applyBusinessPatch(MvpDomain.Order order, Map<String, Object> patch) {
    MvpStoreCommandOrderPatchApplySupport.applyBusinessPatch(this, order, patch);
  }

  protected void updateOrderProgressFacts(MvpDomain.Order order) {
    MvpStoreCommandOrderProgressFactsSupport.updateOrderProgressFacts(this, order);
  }

  protected void syncCompletedQtyFromFinalProcessReports() {
    MvpStoreCommandOrderProgressFactsSupport.syncCompletedQtyFromFinalProcessReports(this);
  }

  protected boolean isFinalProcessForProduct(String productCode, String processCode) {
    return MvpStoreCommandOrderProgressFactsSupport.isFinalProcessForProduct(this, productCode, processCode);
  }

  protected static boolean isFallbackFinalProcess(String normalizedProcessCode) {
    return MvpStoreCommandOrderProgressFactsSupport.isFallbackFinalProcess(normalizedProcessCode);
  }

  protected MvpDomain.OrderBusinessData businessData(MvpDomain.Order order) {
    if (order.businessData == null) {
      order.businessData = new MvpDomain.OrderBusinessData();
    }
    return order.businessData;
  }

  protected MvpDomain.OrderBusinessData buildSimulationBusinessData(
    LocalDate businessDate,
    LocalDate dueDate,
    String salesOrderNo,
    String productCode,
    double qty
  ) {
    return MvpStoreCommandOrderPayloadSupport.buildSimulationBusinessData(this, businessDate, dueDate, salesOrderNo, productCode, qty);
  }
}

