package com.autoproduction.mvp.core;

import com.autoproduction.mvp.module.integration.erp.ErpDataManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Service;

@Service
abstract class MvpStoreLocalizationAndExportSupport extends MvpStoreScheduleExplainMetricsSupport {
  protected MvpStoreLocalizationAndExportSupport(ErpDataManager erpDataManager) {
    super(erpDataManager);
  }

  protected static CellStyle createTitleStyle(Workbook workbook) {
    return MvpStoreExcelPoiSupport.createTitleStyle(workbook);
  }

  protected static CellStyle createHeaderStyle(Workbook workbook) {
    return MvpStoreExcelPoiSupport.createHeaderStyle(workbook);
  }

  protected static CellStyle createBodyStyle(Workbook workbook) {
    return MvpStoreExcelPoiSupport.createBodyStyle(workbook);
  }

  protected static void setCellBorder(CellStyle style) {
    MvpStoreExcelPoiSupport.setCellBorder(style);
  }

  protected static void writeRowValues(Row row, Map<String, Object> source, String[] keys, CellStyle style) {
    MvpStoreExcelPoiSupport.writeRowValues(row, source, keys, style);
  }

  protected static void writeCell(Row row, int columnIndex, Object value, CellStyle style) {
    MvpStoreExcelPoiSupport.writeCell(row, columnIndex, value, style);
  }

  protected Map<String, Object> localizeRow(Map<String, Object> source) {
    return MvpStoreLocalizationLookupSupport.localizeRow(source);
  }

  protected static String firstString(Map<String, Object> row, String... keys) {
    return MvpStoreLocalizationLookupSupport.firstString(row, keys);
  }

  protected static String firstNonBlank(String... values) {
    return MvpStoreLocalizationLookupSupport.firstNonBlank(values);
  }

  protected static String productNameCn(String productCode) {
    return MvpStoreLocalizationLookupSupport.productNameCn(productCode);
  }

  protected static String processNameCn(String processCode) {
    return MvpStoreLocalizationLookupSupport.processNameCn(processCode);
  }

  protected static String statusNameCn(String status) {
    return MvpStoreLocalizationLookupSupport.statusNameCn(status);
  }

  protected static String actionNameCn(String action) {
    return MvpStoreLocalizationLookupSupport.actionNameCn(action);
  }

  protected static String eventTypeNameCn(String eventType) {
    return MvpStoreLocalizationLookupSupport.eventTypeNameCn(eventType);
  }

  protected static String topicNameCn(String topic) {
    return MvpStoreLocalizationLookupSupport.topicNameCn(topic);
  }

  protected static String systemNameCn(String system) {
    return MvpStoreLocalizationLookupSupport.systemNameCn(system);
  }

  protected static String scenarioNameCn(String scenario) {
    return MvpStoreLocalizationLookupSupport.scenarioNameCn(scenario);
  }

  protected static String shiftNameCn(String shiftCode) {
    return MvpStoreLocalizationLookupSupport.shiftNameCn(shiftCode);
  }

  protected static String normalizeShiftCode(String shiftCode) {
    return MvpStoreLocalizationLookupSupport.normalizeShiftCode(shiftCode);
  }

  protected static String normalizeShiftCodeLabel(String shiftCode) {
    return MvpStoreShiftModeSupport.normalizeShiftCodeLabel(shiftCode);
  }

  protected static int shiftSortIndex(String shiftCode) {
    return MvpStoreShiftModeSupport.shiftSortIndex(shiftCode);
  }

  protected static String normalizeWeekendRestMode(String modeText) {
    return MvpStoreShiftModeSupport.normalizeWeekendRestMode(modeText);
  }

  protected static String normalizeDateShiftMode(String modeText) {
    return MvpStoreShiftModeSupport.normalizeDateShiftMode(modeText);
  }

  protected static Map<String, String> normalizeDateShiftModeByDate(Object input) {
    return MvpStoreShiftModeSupport.normalizeDateShiftModeByDate(input);
  }

  protected static boolean isCnStatutoryHoliday(LocalDate date) {
    return MvpStoreShiftModeSupport.isCnStatutoryHoliday(date);
  }

  protected String resolveDateShiftMode(LocalDate date) {
    return MvpStoreShiftModeSupport.resolveDateShiftMode(this, date);
  }

  protected static boolean isShiftOpenInDateMode(String shiftCode, String modeText) {
    return MvpStoreShiftModeSupport.isShiftOpenInDateMode(shiftCode, modeText);
  }

  protected static String dependencyNameCn(String dependencyType) {
    return MvpStoreLocalizationLookupSupport.dependencyNameCn(dependencyType);
  }

  protected static String alertTypeNameCn(String alertType) {
    return MvpStoreLocalizationLookupSupport.alertTypeNameCn(alertType);
  }

  protected static String severityNameCn(String severity) {
    return MvpStoreLocalizationLookupSupport.severityNameCn(severity);
  }

  protected String companyCodeForProcess(String processCode) {
    return MvpStoreLineBindingLookupSupport.companyCodeForProcess(this, processCode);
  }

  protected String workshopCodeForProcess(String processCode) {
    return MvpStoreLineBindingLookupSupport.workshopCodeForProcess(this, processCode);
  }

  protected String lineCodeForProcess(String processCode) {
    return MvpStoreLineBindingLookupSupport.lineCodeForProcess(this, processCode);
  }

  protected String lineNameForCode(String lineCode) {
    return MvpStoreLineBindingLookupSupport.lineNameForCode(this, lineCode);
  }

  protected String lineCodesForProcessSummary(String processCode) {
    return MvpStoreLineBindingLookupSupport.lineCodesForProcessSummary(this, processCode);
  }

  protected String workshopCodesForProcessSummary(String processCode) {
    return MvpStoreLineBindingLookupSupport.workshopCodesForProcessSummary(this, processCode);
  }

  protected String companyCodesForProcessSummary(String processCode) {
    return MvpStoreLineBindingLookupSupport.companyCodesForProcessSummary(this, processCode);
  }

  protected String companyCodesSummaryAll() {
    return MvpStoreLineBindingLookupSupport.companyCodesSummaryAll(this);
  }

  protected String workshopCodesSummaryAll() {
    return MvpStoreLineBindingLookupSupport.workshopCodesSummaryAll(this);
  }

  protected List<MvpDomain.LineProcessBinding> lineBindingsForProcess(String processCode, boolean enabledOnly) {
    return MvpStoreLineBindingLookupSupport.lineBindingsForProcess(this, processCode, enabledOnly);
  }

  protected MvpDomain.LineProcessBinding defaultLineBindingForProcess(String processCode) {
    return MvpStoreLineBindingLookupSupport.defaultLineBindingForProcess(this, processCode);
  }

  protected static String routeNameCn(String routeNo) {
    return MvpStoreLocalizationLookupSupport.routeNameCn(routeNo);
  }

  protected static String syncFlowCn(String source, String target) {
    return MvpStoreLocalizationLookupSupport.syncFlowCn(source, target);
  }

}
