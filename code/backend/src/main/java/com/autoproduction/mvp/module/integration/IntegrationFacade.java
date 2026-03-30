package com.autoproduction.mvp.module.integration;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class IntegrationFacade {
  private final IntegrationQueryService integrationQueryService;
  private final IntegrationCommandService integrationCommandService;
  private final ErpRefreshService erpRefreshService;

  public IntegrationFacade(
    IntegrationQueryService integrationQueryService,
    IntegrationCommandService integrationCommandService,
    ErpRefreshService erpRefreshService
  ) {
    this.integrationQueryService = integrationQueryService;
    this.integrationCommandService = integrationCommandService;
    this.erpRefreshService = erpRefreshService;
  }

  public List<Map<String, Object>> listSalesOrderLines() {
    return integrationQueryService.listSalesOrderLines();
  }

  public List<Map<String, Object>> listPlanOrders() {
    return integrationQueryService.listPlanOrders();
  }

  public List<Map<String, Object>> listProductionOrders() {
    return integrationQueryService.listProductionOrders();
  }

  public List<Map<String, Object>> listPurchaseOrders() {
    return integrationQueryService.listPurchaseOrders();
  }

  public List<Map<String, Object>> listErpSalesOrderHeadersRaw() {
    return integrationQueryService.listErpSalesOrderHeadersRaw();
  }

  public List<Map<String, Object>> listErpSalesOrderLinesRaw() {
    return integrationQueryService.listErpSalesOrderLinesRaw();
  }

  public List<Map<String, Object>> listErpProductionOrderHeadersRaw() {
    return integrationQueryService.listErpProductionOrderHeadersRaw();
  }

  public List<Map<String, Object>> listErpProductionOrderLinesRaw() {
    return integrationQueryService.listErpProductionOrderLinesRaw();
  }

  public List<Map<String, Object>> listScheduleControls() {
    return integrationQueryService.listScheduleControls();
  }

  public List<Map<String, Object>> listMrpLinks() {
    return integrationQueryService.listMrpLinks();
  }

  public List<Map<String, Object>> listDeliveryProgress() {
    return integrationQueryService.listDeliveryProgress();
  }

  public List<Map<String, Object>> listMaterialAvailability() {
    return integrationQueryService.listMaterialAvailability();
  }

  public List<Map<String, Object>> listEquipments() {
    return integrationQueryService.listEquipments();
  }

  public List<Map<String, Object>> listProcessRoutes() {
    return integrationQueryService.listProcessRoutes();
  }

  public List<Map<String, Object>> listReportingsForMes(Map<String, String> filters) {
    return integrationQueryService.listReportingsForMes(filters);
  }

  public List<Map<String, Object>> listEquipmentProcessCapabilities() {
    return integrationQueryService.listEquipmentProcessCapabilities();
  }

  public List<Map<String, Object>> listEmployeeSkills() {
    return integrationQueryService.listEmployeeSkills();
  }

  public List<Map<String, Object>> listShiftCalendar() {
    return integrationQueryService.listShiftCalendar();
  }

  public List<Map<String, Object>> listWorkshopWeeklyPlanRows(String versionNo) {
    return integrationQueryService.listWorkshopWeeklyPlanRows(versionNo);
  }

  public List<Map<String, Object>> listWorkshopMonthlyPlanRows(String versionNo) {
    return integrationQueryService.listWorkshopMonthlyPlanRows(versionNo);
  }

  public byte[] exportWorkshopWeeklyPlanXlsx(String versionNo) {
    return integrationQueryService.exportWorkshopWeeklyPlanXlsx(versionNo);
  }

  public byte[] exportWorkshopMonthlyPlanXls(String versionNo) {
    return integrationQueryService.exportWorkshopMonthlyPlanXls(versionNo);
  }

  public Map<String, Object> writeScheduleResults(Map<String, Object> payload, String requestId, String operator) {
    return integrationCommandService.writeScheduleResults(payload, requestId, operator);
  }

  public Map<String, Object> writeScheduleStatus(Map<String, Object> payload, String requestId, String operator) {
    return integrationCommandService.writeScheduleStatus(payload, requestId, operator);
  }

  public Map<String, Object> ingestWipLotEvent(Map<String, Object> payload, String requestId, String operator) {
    return integrationCommandService.ingestWipLotEvent(payload, requestId, operator);
  }

  public void triggerReplanJob(Map<String, Object> payload, String requestId, String operator) {
    integrationCommandService.triggerReplanJob(payload, requestId, operator);
  }

  public List<Map<String, Object>> listIntegrationInbox(Map<String, String> filters) {
    return integrationQueryService.listIntegrationInbox(filters);
  }

  public List<Map<String, Object>> listIntegrationOutbox(Map<String, String> filters) {
    return integrationQueryService.listIntegrationOutbox(filters);
  }

  public Map<String, Object> retryOutboxMessage(String messageId, String requestId, String operator) {
    return integrationCommandService.retryOutboxMessage(messageId, requestId, operator);
  }

  public Map<String, Object> getErpRefreshStatus(String requestId) {
    return erpRefreshService.getRefreshStatus(requestId);
  }

  public Map<String, Object> refreshErpData(String requestId, String operator, String reason) {
    return erpRefreshService.refreshManual(requestId, operator, reason);
  }
}
