package com.autoproduction.mvp.core;

import com.autoproduction.mvp.module.integration.erp.ErpDataManager;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service

abstract class MvpStoreIntegrationDomain extends MvpStoreMasterdataDomain {
  protected MvpStoreIntegrationDomain(ErpDataManager erpDataManager) {
    super(erpDataManager);
  }

  public List<Map<String, Object>> listSalesOrderLines() {
    return MvpStoreIntegrationSalesOrderSupport.listSalesOrderLines(this);
  }

  public List<Map<String, Object>> listErpSalesOrderHeadersRaw() {
    return MvpStoreIntegrationSalesOrderSupport.listErpSalesOrderHeadersRaw(this);
  }

  public List<Map<String, Object>> listErpSalesOrderLinesRaw() {
    return MvpStoreIntegrationSalesOrderSupport.listErpSalesOrderLinesRaw(this);
  }

  public List<Map<String, Object>> listPlanOrders() {
    return MvpStoreIntegrationProductionOrderSupport.listPlanOrders(this);
  }

  public List<Map<String, Object>> listPurchaseOrders() {
    return MvpStoreIntegrationProductionOrderSupport.listPurchaseOrders(this);
  }

  public List<Map<String, Object>> listProductionOrders() {
    return MvpStoreIntegrationProductionOrderSupport.listProductionOrders(this);
  }

  public List<Map<String, Object>> listErpProductionOrderHeadersRaw() {
    return MvpStoreIntegrationProductionOrderSupport.listErpProductionOrderHeadersRaw(this);
  }

  public List<Map<String, Object>> listErpProductionOrderLinesRaw() {
    return MvpStoreIntegrationProductionOrderSupport.listErpProductionOrderLinesRaw(this);
  }

  public List<Map<String, Object>> listWorkshopWeeklyPlanRows() {
    return MvpStoreIntegrationReportExportSupport.listWorkshopWeeklyPlanRows(this, null);
  }

  public List<Map<String, Object>> listWorkshopWeeklyPlanRows(String versionNo) {
    return MvpStoreIntegrationReportExportSupport.listWorkshopWeeklyPlanRows(this, versionNo);
  }

  public List<Map<String, Object>> listWorkshopMonthlyPlanRows() {
    return MvpStoreIntegrationReportExportSupport.listWorkshopMonthlyPlanRows(this, null);
  }

  public List<Map<String, Object>> listWorkshopMonthlyPlanRows(String versionNo) {
    return MvpStoreIntegrationReportExportSupport.listWorkshopMonthlyPlanRows(this, versionNo);
  }

  public byte[] exportWorkshopWeeklyPlanXlsx() {
    return MvpStoreIntegrationReportExportSupport.exportWorkshopWeeklyPlanXlsx(this, null);
  }

  public byte[] exportWorkshopWeeklyPlanXlsx(String versionNo) {
    return MvpStoreIntegrationReportExportSupport.exportWorkshopWeeklyPlanXlsx(this, versionNo);
  }

  public byte[] exportWorkshopMonthlyPlanXls() {
    return MvpStoreIntegrationReportExportSupport.exportWorkshopMonthlyPlanXls(this, null);
  }

  public byte[] exportWorkshopMonthlyPlanXls(String versionNo) {
    return MvpStoreIntegrationReportExportSupport.exportWorkshopMonthlyPlanXls(this, versionNo);
  }

  public List<Map<String, Object>> listScheduleControls() {
    return MvpStoreIntegrationScheduleOpsSupport.listScheduleControls(this);
  }

  public List<Map<String, Object>> listMrpLinks() {
    return MvpStoreIntegrationScheduleOpsSupport.listMrpLinks(this);
  }

  public List<Map<String, Object>> listDeliveryProgress() {
    return MvpStoreIntegrationScheduleOpsSupport.listDeliveryProgress(this);
  }

  public List<Map<String, Object>> listMaterialAvailability() {
    return MvpStoreIntegrationResourceQuerySupport.listMaterialAvailability(this);
  }

  public List<Map<String, Object>> listEquipments() {
    return MvpStoreIntegrationResourceQuerySupport.listEquipments(this);
  }

  public List<Map<String, Object>> listProcessRoutes() {
    return MvpStoreIntegrationResourceQuerySupport.listProcessRoutes(this);
  }

  public List<Map<String, Object>> listEquipmentProcessCapabilities() {
    return MvpStoreIntegrationResourceQuerySupport.listEquipmentProcessCapabilities(this);
  }

  public List<Map<String, Object>> listEmployeeSkills() {
    return MvpStoreIntegrationResourceQuerySupport.listEmployeeSkills(this);
  }

  public List<Map<String, Object>> listShiftCalendar() {
    return MvpStoreIntegrationResourceQuerySupport.listShiftCalendar(this);
  }

}

