package com.autoproduction.mvp.core;

import com.autoproduction.mvp.module.integration.erp.ErpDataManager;
import java.time.LocalDate;
import java.util.Map;
import java.util.Random;
import org.springframework.stereotype.Service;

@Service
abstract class MvpStoreSimulationEngineSupport extends MvpStoreCoreNormalizationSupport {
  protected MvpStoreSimulationEngineSupport(ErpDataManager erpDataManager) {
    super(erpDataManager);
  }

  protected int generateDailySalesAndProductionOrders(LocalDate businessDate, int dailySales, Random random, String requestId) {
    return MvpStoreSimulationOrderSupport.generateDailySalesAndProductionOrders(this, businessDate, dailySales, random, requestId);
  }

  protected Map<String, Object> rebuildPlanningHorizon(LocalDate startDate, String scenario, Random random, String requestId) {
    return MvpStoreSimulationHorizonSupport.rebuildPlanningHorizon(this, startDate, scenario, random, requestId);
  }

  protected int simulateDailyReporting(
    LocalDate businessDate,
    String versionNo,
    String scenario,
    Random random,
    String requestIdPrefix
  ) {
    return MvpStoreSimulationReportingSupport.simulateDailyReporting(
      this,
      businessDate,
      versionNo,
      scenario,
      random,
      requestIdPrefix
    );
  }

  protected boolean createSimulatedReporting(
    String orderNo,
    String productCode,
    String processCode,
    double plannedQty,
    double orderQty,
    String predecessorProcessCode,
    String scenario,
    Random random,
    String requestId,
    Map<String, Double> cumulativeReportedByOrderProcess
  ) {
    return MvpStoreSimulationReportingSupport.createSimulatedReporting(
      this,
      orderNo,
      productCode,
      processCode,
      plannedQty,
      orderQty,
      predecessorProcessCode,
      scenario,
      random,
      requestId,
      cumulativeReportedByOrderProcess
    );
  }

  protected void refreshOrderStatuses(LocalDate businessDate) {
    MvpStoreSimulationOrderSupport.refreshOrderStatuses(this, businessDate);
  }

  protected int countDelayedOrders(LocalDate businessDate) {
    return MvpStoreSimulationOrderSupport.countDelayedOrders(this, businessDate);
  }

  protected static boolean hasRemainingQty(MvpDomain.Order order) {
    return MvpStoreSimulationOrderSupport.hasRemainingQty(order);
  }

  protected static double orderTotalQty(MvpDomain.Order order) {
    return MvpStoreSimulationOrderSupport.orderTotalQty(order);
  }

  protected static double orderRemainingQty(MvpDomain.Order order) {
    return MvpStoreSimulationOrderSupport.orderRemainingQty(order);
  }

  protected static boolean isOrderDone(MvpDomain.Order order) {
    return MvpStoreSimulationOrderSupport.isOrderDone(order);
  }

  protected void ensureManualSimulationSnapshot() {
    MvpStoreSimulationManualSnapshotSupport.ensureManualSimulationSnapshot(this);
  }

  protected void restoreManualSimulationSnapshot() {
    MvpStoreSimulationManualSnapshotSupport.restoreManualSimulationSnapshot(this);
  }

  protected MvpDomain.State deepCopyState(MvpDomain.State source) {
    return MvpStoreSimulationDeepCopySupport.deepCopyState(this, source);
  }

  protected MvpDomain.Order deepCopyOrder(MvpDomain.Order source) {
    return MvpStoreSimulationDeepCopySupport.deepCopyOrder(source);
  }

  protected MvpDomain.ScheduleVersion deepCopyScheduleVersion(MvpDomain.ScheduleVersion source) {
    return MvpStoreSimulationDeepCopySupport.deepCopyScheduleVersion(this, source);
  }

  protected MvpDomain.Reporting deepCopyReporting(MvpDomain.Reporting source) {
    return MvpStoreSimulationDeepCopySupport.deepCopyReporting(source);
  }

  protected SimulationState deepCopySimulationState(SimulationState source) {
    return MvpStoreSimulationDeepCopySupport.deepCopySimulationState(this, source);
  }

  protected void restoreSimulationState(SimulationState source) {
    MvpStoreSimulationDeepCopySupport.restoreSimulationState(this, source);
  }

  protected void resetSimulationState(long seed, String scenario, int dailySales) {
    MvpStoreSimulationStateSupport.resetSimulationState(this, seed, scenario, dailySales);
  }

  protected Map<String, Object> buildSimulationStateResponse(String requestId) {
    return MvpStoreSimulationStateSupport.buildSimulationStateResponse(this, requestId);
  }

  protected void appendSimulationEvent(
    LocalDate date,
    String eventType,
    String message,
    String requestId,
    Map<String, Object> details
  ) {
    MvpStoreSimulationStateSupport.appendSimulationEvent(this, date, eventType, message, requestId, details);
  }
}

