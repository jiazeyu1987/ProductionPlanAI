package com.autoproduction.mvp.core;

import com.autoproduction.mvp.module.integration.erp.ErpDataManager;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
abstract class MvpStoreScheduleExplainMetricsSupport extends MvpStoreSimulationEngineSupport {
  protected MvpStoreScheduleExplainMetricsSupport(ErpDataManager erpDataManager) {
    super(erpDataManager);
  }

  protected Map<String, Object> runIdempotent(String requestId, String action, Supplier<Map<String, Object>> executor) {
    return MvpStoreScheduleIdempotencyLedgerSupport.runIdempotent(this, requestId, action, executor);
  }

  protected static String toDateTime(String date, String shiftCode, boolean start) {
    return MvpStoreScheduleExplainTextSupport.toDateTime(date, shiftCode, start);
  }

  protected static int countCapacityBlocked(List<Map<String, Object>> unscheduled) {
    return MvpStoreScheduleUnscheduledReasonSupport.countCapacityBlocked(unscheduled);
  }

  protected static int countByReasonCodes(List<Map<String, Object>> unscheduled, Set<String> reasonCodes) {
    return MvpStoreScheduleUnscheduledReasonSupport.countByReasonCodes(unscheduled, reasonCodes);
  }

  protected static Map<String, Integer> buildUnscheduledReasonDistribution(List<Map<String, Object>> unscheduled) {
    return MvpStoreScheduleUnscheduledReasonSupport.buildUnscheduledReasonDistribution(unscheduled);
  }

  protected Map<String, Object> buildScheduleObservabilityMetrics(
    MvpDomain.ScheduleVersion schedule,
    Long candidateOrderCount,
    Long candidateTaskCount
  ) {
    return MvpStoreScheduleObservabilityMetricsSupport.buildScheduleObservabilityMetrics(
      this,
      schedule,
      candidateOrderCount,
      candidateTaskCount
    );
  }

  protected int countPublishActions() {
    return MvpStoreScheduleObservabilityMetricsSupport.countPublishActions(this);
  }

  protected int countRollbackActions() {
    return MvpStoreScheduleObservabilityMetricsSupport.countRollbackActions(this);
  }

  protected int countVersionActions(Set<String> actions) {
    return MvpStoreScheduleObservabilityMetricsSupport.countVersionActions(this, actions);
  }

  protected double calcReplanFailureRate() {
    return MvpStoreScheduleObservabilityMetricsSupport.calcReplanFailureRate(this);
  }

  protected double calcApiErrorRate() {
    return MvpStoreScheduleObservabilityMetricsSupport.calcApiErrorRate(this);
  }

  protected static String scheduleReasonNameCn(String reasonCode) {
    return MvpStoreScheduleUnscheduledReasonSupport.scheduleReasonNameCn(reasonCode);
  }

  protected static String resolveUnscheduledReasonCode(Map<String, Object> unscheduledRow) {
    return MvpStoreScheduleUnscheduledReasonSupport.resolveUnscheduledReasonCode(unscheduledRow);
  }

  protected static String normalizeReasonCode(String reasonCode) {
    return MvpStoreScheduleUnscheduledReasonSupport.normalizeReasonCode(reasonCode);
  }

  protected static String toLegacyReasonCode(String reasonCode) {
    return MvpStoreScheduleUnscheduledReasonSupport.toLegacyReasonCode(reasonCode);
  }

  protected Map<String, Object> normalizeUnscheduledRow(
    Map<String, Object> unscheduledRow,
    MvpDomain.ScheduleTask task,
    boolean showDetailedReasons
  ) {
    return MvpStoreScheduleUnscheduledRowSupport.normalizeUnscheduledRow(this, unscheduledRow, task, showDetailedReasons);
  }

  protected static String resolveTaskDependencyStatus(MvpDomain.ScheduleTask task, Map<String, Object> unscheduledRow) {
    return MvpStoreScheduleUnscheduledRowSupport.resolveTaskDependencyStatus(task, unscheduledRow);
  }

  protected static String resolveTaskLastBlockReason(
    MvpDomain.ScheduleTask task,
    Map<String, Object> unscheduledRow,
    String fallbackReasonCode
  ) {
    return MvpStoreScheduleUnscheduledRowSupport.resolveTaskLastBlockReason(task, unscheduledRow, fallbackReasonCode);
  }

  protected static String resolveTaskStatus(
    MvpDomain.ScheduleTask task,
    Map<String, Object> unscheduledRow,
    boolean hasAllocation
  ) {
    return MvpStoreScheduleUnscheduledRowSupport.resolveTaskStatus(task, unscheduledRow, hasAllocation);
  }

  protected static String topReasonCode(Map<String, Integer> reasonCountByCode) {
    return MvpStoreScheduleUnscheduledReasonSupport.topReasonCode(reasonCountByCode);
  }

  protected static String buildProcessAllocationExplainCn(
    String processCode,
    double targetQty,
    double scheduledQty,
    double unscheduledQty,
    int orderCount,
    String topReasonCode
  ) {
    return MvpStoreScheduleExplainTextSupport.buildProcessAllocationExplainCn(
      processCode,
      targetQty,
      scheduledQty,
      unscheduledQty,
      orderCount,
      topReasonCode
    );
  }

  protected static String formatQtyText(double value) {
    return MvpStoreScheduleExplainTextSupport.formatQtyText(value);
  }

  protected static String formatPercentText(double value) {
    return MvpStoreScheduleExplainTextSupport.formatPercentText(value);
  }

  protected static double estimateResourceCapacity(
    MvpDomain.ProcessConfig processConfig,
    int workersAvailable,
    int machinesAvailable
  ) {
    return MvpStoreScheduleExplainTextSupport.estimateResourceCapacity(processConfig, workersAvailable, machinesAvailable);
  }

  protected static double calcTaskProducedBeforeShift(
    MvpDomain.Allocation current,
    List<MvpDomain.Allocation> taskAllocations,
    Map<String, Integer> shiftIndexByShiftId
  ) {
    return MvpStoreScheduleExplainTextSupport.calcTaskProducedBeforeShift(current, taskAllocations, shiftIndexByShiftId);
  }

  protected static String buildMaxAllocationExplainCn(
    String processCode,
    MvpDomain.Allocation maxAllocation,
    double taskRemainingBeforeShift,
    double resourceCapacity,
    double materialAvailable
  ) {
    return MvpStoreScheduleExplainTextSupport.buildMaxAllocationExplainCn(
      processCode,
      maxAllocation,
      taskRemainingBeforeShift,
      resourceCapacity,
      materialAvailable
    );
  }
}

