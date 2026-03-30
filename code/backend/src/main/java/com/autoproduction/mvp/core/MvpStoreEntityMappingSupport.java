package com.autoproduction.mvp.core;

import com.autoproduction.mvp.module.integration.erp.ErpDataManager;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
abstract class MvpStoreEntityMappingSupport extends MvpStoreLocalizationAndExportSupport {
  protected MvpStoreEntityMappingSupport(ErpDataManager erpDataManager) {
    super(erpDataManager);
  }

  protected static String formatShortDate(LocalDate date) {
    return MvpStoreEntityMappingDateSupport.formatShortDate(date);
  }

  protected static OffsetDateTime parseOffsetDateTimeFilter(Map<String, String> filters, String... keys) {
    return MvpStoreEntityMappingDateSupport.parseOffsetDateTimeFilter(filters, keys);
  }

  protected static LocalDate parseLocalDateFlexible(String value, LocalDate fallback) {
    return MvpStoreEntityMappingDateSupport.parseLocalDateFlexible(value, fallback);
  }

  protected MvpDomain.Order findOrder(String orderNo) {
    return MvpStoreEntityMappingLookupSupport.findOrder(this, orderNo);
  }

  protected MvpDomain.ScheduleVersion getScheduleEntity(String versionNo) {
    return MvpStoreEntityMappingLookupSupport.getScheduleEntity(this, versionNo);
  }

  protected ReportVersionBinding resolveReportVersionBinding(String versionNo) {
    return MvpStoreEntityMappingLookupSupport.resolveReportVersionBinding(this, versionNo);
  }

  protected LocalDate resolveExpectedStartDate(MvpDomain.Order order) {
    return MvpStoreEntityMappingEstimationSupport.resolveExpectedStartDate(this, order);
  }

  protected String estimateExpectedFinishTime(MvpDomain.Order order) {
    return MvpStoreEntityMappingEstimationSupport.estimateExpectedFinishTime(this, order);
  }

  protected int estimateRequiredShifts(MvpDomain.Order order) {
    return MvpStoreEntityMappingEstimationSupport.estimateRequiredShifts(this, order);
  }

  protected double estimateProcessCapacityPerShift(String processCode) {
    return MvpStoreEntityMappingEstimationSupport.estimateProcessCapacityPerShift(this, processCode);
  }

  protected int maxResourceForProcess(List<MvpDomain.ResourceRow> rows, String processCode) {
    return MvpStoreEntityMappingEstimationSupport.maxResourceForProcess(this, rows, processCode);
  }

  protected String orderPrimaryProductCode(MvpDomain.Order order) {
    return MvpStoreEntityMappingEstimationSupport.orderPrimaryProductCode(this, order);
  }

  protected List<Map<String, Object>> buildProcessContextsForProduct(String productCode) {
    return MvpStoreEntityMappingProcessContextSupport.buildProcessContextsForProduct(this, productCode);
  }

  protected String summarizeProcessContexts(List<Map<String, Object>> rows) {
    return MvpStoreEntityMappingProcessContextSupport.summarizeProcessContexts(this, rows);
  }

  protected String joinContextValues(List<Map<String, Object>> rows, String key) {
    return MvpStoreEntityMappingProcessContextSupport.joinContextValues(this, rows, key);
  }

  protected Map<String, Object> toOrderMap(MvpDomain.Order order) {
    return MvpStoreEntityMappingRowSupport.toOrderMap(this, order);
  }

  protected boolean filterOrderPoolRow(Map<String, Object> row, Map<String, String> filters) {
    return MvpStoreEntityMappingRowSupport.filterOrderPoolRow(this, row, filters);
  }

  protected Map<String, Object> toOrderPoolItemFromErp(Map<String, Object> erpRow) {
    return MvpStoreEntityMappingRowSupport.toOrderPoolItemFromErp(this, erpRow);
  }

  protected Map<String, Object> toOrderPoolItem(MvpDomain.Order order) {
    return MvpStoreEntityMappingRowSupport.toOrderPoolItem(this, order);
  }

  protected Map<String, Object> toScheduleMap(MvpDomain.ScheduleVersion schedule) {
    return MvpStoreEntityMappingRowSupport.toScheduleMap(this, schedule);
  }

  protected Map<String, Object> toReportingMap(MvpDomain.Reporting reporting) {
    return MvpStoreEntityMappingRowSupport.toReportingMap(this, reporting);
  }

  protected void appendAudit(
    String entityType,
    String entityId,
    String action,
    String operator,
    String requestId,
    String reason
  ) {
    MvpStoreEntityMappingAuditSupport.appendAudit(this, entityType, entityId, action, operator, requestId, reason);
  }

  protected void appendAudit(
    String entityType,
    String entityId,
    String action,
    String operator,
    String requestId,
    String reason,
    Map<String, Object> perfContext
  ) {
    MvpStoreEntityMappingAuditSupport.appendAudit(
      this,
      entityType,
      entityId,
      action,
      operator,
      requestId,
      reason,
      perfContext
    );
  }

  protected void appendInbox(String topic, String entityId, String requestId, String status, String errorMsg) {
    MvpStoreEntityMappingAuditSupport.appendInbox(this, topic, entityId, requestId, status, errorMsg);
  }

  protected void appendOutbox(String topic, String entityId, String requestId, String status, String errorMsg) {
    MvpStoreEntityMappingAuditSupport.appendOutbox(this, topic, entityId, requestId, status, errorMsg);
  }

  protected boolean matchIntegrationFilters(Map<String, Object> row, Map<String, String> filters) {
    return MvpStoreEntityMappingAuditSupport.matchIntegrationFilters(row, filters);
  }
}

