package com.autoproduction.mvp.module.integration;

import com.autoproduction.mvp.core.MvpStoreService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class IntegrationQueryService {
  private final MvpStoreService store;
  private final MaterialAvailabilityQueryAdapter materialAvailabilityQueryAdapter;

  public IntegrationQueryService(
    MvpStoreService store,
    MaterialAvailabilityQueryAdapter materialAvailabilityQueryAdapter
  ) {
    this.store = store;
    this.materialAvailabilityQueryAdapter = materialAvailabilityQueryAdapter;
  }

  public List<Map<String, Object>> listSalesOrderLines() {
    return store.listSalesOrderLines();
  }

  public List<Map<String, Object>> listPlanOrders() {
    return store.listPlanOrders();
  }

  public List<Map<String, Object>> listProductionOrders() {
    return store.listProductionOrders();
  }

  public List<Map<String, Object>> listPurchaseOrders() {
    return store.listPurchaseOrders();
  }

  public List<Map<String, Object>> listErpSalesOrderHeadersRaw() {
    return store.listErpSalesOrderHeadersRaw();
  }

  public List<Map<String, Object>> listErpSalesOrderLinesRaw() {
    return store.listErpSalesOrderLinesRaw();
  }

  public List<Map<String, Object>> listErpProductionOrderHeadersRaw() {
    return store.listErpProductionOrderHeadersRaw();
  }

  public List<Map<String, Object>> listErpProductionOrderLinesRaw() {
    return store.listErpProductionOrderLinesRaw();
  }

  public List<Map<String, Object>> listScheduleControls() {
    return store.listScheduleControls();
  }

  public List<Map<String, Object>> listMrpLinks() {
    return store.listMrpLinks();
  }

  public List<Map<String, Object>> listDeliveryProgress() {
    return store.listDeliveryProgress();
  }

  public List<Map<String, Object>> listMaterialAvailability() {
    return materialAvailabilityQueryAdapter.listMaterialAvailability();
  }

  public List<Map<String, Object>> listEquipments() {
    return store.listEquipments();
  }

  public List<Map<String, Object>> listProcessRoutes() {
    return store.listProcessRoutes();
  }

  public List<Map<String, Object>> listReportingsForMes(Map<String, String> filters) {
    return store.listReportingsForMes(filters);
  }

  public List<Map<String, Object>> listEquipmentProcessCapabilities() {
    return store.listEquipmentProcessCapabilities();
  }

  public List<Map<String, Object>> listEmployeeSkills() {
    return store.listEmployeeSkills();
  }

  public List<Map<String, Object>> listShiftCalendar() {
    return store.listShiftCalendar();
  }

  public List<Map<String, Object>> listWorkshopWeeklyPlanRows(String versionNo) {
    return store.listWorkshopWeeklyPlanRows(versionNo);
  }

  public List<Map<String, Object>> listWorkshopMonthlyPlanRows(String versionNo) {
    return store.listWorkshopMonthlyPlanRows(versionNo);
  }

  public byte[] exportWorkshopWeeklyPlanXlsx(String versionNo) {
    return store.exportWorkshopWeeklyPlanXlsx(versionNo);
  }

  public byte[] exportWorkshopMonthlyPlanXls(String versionNo) {
    return store.exportWorkshopMonthlyPlanXls(versionNo);
  }

  public List<Map<String, Object>> listIntegrationInbox(Map<String, String> filters) {
    return store.listIntegrationInbox(filters);
  }

  public List<Map<String, Object>> listIntegrationOutbox(Map<String, String> filters) {
    return store.listIntegrationOutbox(filters);
  }
}
