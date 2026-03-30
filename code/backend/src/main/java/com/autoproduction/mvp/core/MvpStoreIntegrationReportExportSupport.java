package com.autoproduction.mvp.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

final class MvpStoreIntegrationReportExportSupport {
  private MvpStoreIntegrationReportExportSupport() {}

  static List<Map<String, Object>> listWorkshopWeeklyPlanRows(MvpStoreIntegrationDomain store, String versionNo) {
    synchronized (store.lock) {
      MvpStoreRuntimeBase.ReportVersionBinding binding = store.resolveReportVersionBinding(versionNo);
      List<Map<String, Object>> rows = new ArrayList<>();
      for (MvpDomain.Order order : store.state.orders) {
        if (binding.orderNos != null && !binding.orderNos.contains(order.orderNo)) {
          continue;
        }
        MvpDomain.OrderBusinessData business = store.businessData(order);
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("production_order_no", order.orderNo);
        row.put("customer_remark", business.customerRemark);
        row.put("product_name", business.productName);
        row.put("spec_model", business.specModel);
        row.put("production_batch_no", business.productionBatchNo);
        row.put("order_qty", order.items.stream().mapToDouble(item -> item.qty).sum());
        row.put("packaging_form", business.packagingForm);
        row.put("sales_order_no", business.salesOrderNo);
        row.put("workshop_outer_packaging_date", business.workshopOuterPackagingDate);
        row.put("process_schedule_remark", business.weeklyMonthlyPlanRemark);
        row.put("schedule_version_no", binding.versionNo);
        row.put("schedule_version_status", binding.status);
        row.put("schedule_version_status_name_cn", MvpStoreIntegrationDomain.statusNameCn(binding.status));
        rows.add(row);
      }
      return rows;
    }
  }

  static List<Map<String, Object>> listWorkshopMonthlyPlanRows(MvpStoreIntegrationDomain store, String versionNo) {
    synchronized (store.lock) {
      MvpStoreRuntimeBase.ReportVersionBinding binding = store.resolveReportVersionBinding(versionNo);
      List<Map<String, Object>> rows = new ArrayList<>();
      for (MvpDomain.Order order : store.state.orders) {
        if (binding.orderNos != null && !binding.orderNos.contains(order.orderNo)) {
          continue;
        }
        MvpDomain.OrderBusinessData business = store.businessData(order);
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("order_date", business.orderDate == null ? null : business.orderDate.toString());
        row.put("production_order_no", order.orderNo);
        row.put("customer_remark", business.customerRemark);
        row.put("product_name", business.productName);
        row.put("spec_model", business.specModel);
        row.put("production_batch_no", business.productionBatchNo);
        row.put("planned_finish_date_2", business.plannedFinishDate2);
        row.put("production_date_foreign_trade", business.productionDateForeignTrade);
        row.put("order_qty", order.items.stream().mapToDouble(item -> item.qty).sum());
        row.put("packaging_form", business.packagingForm);
        row.put("sales_order_no", business.salesOrderNo);
        row.put("purchase_due_date", business.purchaseDueDate);
        row.put("injection_due_date", business.injectionDueDate);
        row.put("market_remark_info", business.marketRemarkInfo);
        row.put("market_demand", business.marketDemand);
        row.put("planned_finish_date_1", business.plannedFinishDate1);
        row.put("semi_finished_code", business.semiFinishedCode);
        row.put("semi_finished_inventory", business.semiFinishedInventory);
        row.put("semi_finished_demand", business.semiFinishedDemand);
        row.put("semi_finished_wip", business.semiFinishedWip);
        row.put("need_order_qty", business.needOrderQty);
        row.put("pending_inbound_qty", business.pendingInboundQty);
        row.put("weekly_monthly_process_plan", business.weeklyMonthlyPlanRemark);
        row.put("workshop_outer_packaging_date", business.workshopOuterPackagingDate);
        row.put("note", business.note);
        row.put("workshop_completed_qty", business.workshopCompletedQty);
        row.put("workshop_completed_time", business.workshopCompletedTime);
        row.put("outer_completed_qty", business.outerCompletedQty);
        row.put("outer_completed_time", business.outerCompletedTime);
        row.put("match_status", business.matchStatus);
        row.put("schedule_version_no", binding.versionNo);
        row.put("schedule_version_status", binding.status);
        row.put("schedule_version_status_name_cn", MvpStoreIntegrationDomain.statusNameCn(binding.status));
        rows.add(row);
      }
      return rows;
    }
  }

  static byte[] exportWorkshopWeeklyPlanXlsx(MvpStoreIntegrationDomain store, String versionNo) {
    synchronized (store.lock) {
      try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
        Sheet sheet = workbook.createSheet(MvpStoreIntegrationDomain.WEEKLY_PLAN_SHEET_NAME);
        CellStyle titleStyle = MvpStoreIntegrationDomain.createTitleStyle(workbook);
        CellStyle headerStyle = MvpStoreIntegrationDomain.createHeaderStyle(workbook);
        CellStyle bodyStyle = MvpStoreIntegrationDomain.createBodyStyle(workbook);

        Row titleRow = sheet.createRow(0);
        MvpStoreIntegrationDomain.writeCell(titleRow, 0, MvpStoreIntegrationDomain.WEEKLY_PLAN_TITLE_CN, titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, MvpStoreIntegrationDomain.WEEKLY_PLAN_HEADERS_CN.length - 1));

        Row headerRow = sheet.createRow(1);
        for (int i = 0; i < MvpStoreIntegrationDomain.WEEKLY_PLAN_HEADERS_CN.length; i += 1) {
          MvpStoreIntegrationDomain.writeCell(headerRow, i, MvpStoreIntegrationDomain.WEEKLY_PLAN_HEADERS_CN[i], headerStyle);
        }

        List<Map<String, Object>> rows = listWorkshopWeeklyPlanRows(store, versionNo);
        for (int i = 0; i < rows.size(); i += 1) {
          Row dataRow = sheet.createRow(i + 2);
          MvpStoreIntegrationDomain.writeRowValues(dataRow, rows.get(i), MvpStoreIntegrationDomain.WEEKLY_PLAN_KEYS, bodyStyle);
        }

        for (int i = 0; i < MvpStoreIntegrationDomain.WEEKLY_PLAN_HEADERS_CN.length; i += 1) {
          sheet.setColumnWidth(i, 18 * 256);
        }
        for (String extraSheetName : MvpStoreIntegrationDomain.WEEKLY_PLAN_EXTRA_SHEETS) {
          workbook.createSheet(extraSheetName);
        }
        workbook.write(output);
        return output.toByteArray();
      } catch (IOException ex) {
        throw new MvpServiceException(500, "EXPORT_FAILED", "Failed to export workshop weekly plan.", true);
      }
    }
  }

  static byte[] exportWorkshopMonthlyPlanXls(MvpStoreIntegrationDomain store, String versionNo) {
    synchronized (store.lock) {
      try (HSSFWorkbook workbook = new HSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
        Sheet sheet = workbook.createSheet(MvpStoreIntegrationDomain.MONTHLY_PLAN_SHEET_NAME);
        CellStyle titleStyle = MvpStoreIntegrationDomain.createTitleStyle(workbook);
        CellStyle headerStyle = MvpStoreIntegrationDomain.createHeaderStyle(workbook);
        CellStyle bodyStyle = MvpStoreIntegrationDomain.createBodyStyle(workbook);

        Row titleRow = sheet.createRow(0);
        MvpStoreIntegrationDomain.writeCell(titleRow, 0, MvpStoreIntegrationDomain.MONTHLY_PLAN_TITLE_CN, titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, MvpStoreIntegrationDomain.MONTHLY_PLAN_HEADERS_CN.length - 1));

        Row groupRow = sheet.createRow(1);
        MvpStoreIntegrationDomain.writeCell(groupRow, 0, "订单基本信息", headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 13));
        MvpStoreIntegrationDomain.writeCell(groupRow, 16, "半成品信息", headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 16, 21));
        MvpStoreIntegrationDomain.writeCell(groupRow, 22, "订单排产信息", headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 22, 24));
        MvpStoreIntegrationDomain.writeCell(groupRow, 25, "订单进度", headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 25, 28));

        Row headerRow = sheet.createRow(2);
        for (int i = 0; i < MvpStoreIntegrationDomain.MONTHLY_PLAN_HEADERS_CN.length; i += 1) {
          MvpStoreIntegrationDomain.writeCell(headerRow, i, MvpStoreIntegrationDomain.MONTHLY_PLAN_HEADERS_CN[i], headerStyle);
        }

        List<Map<String, Object>> rows = listWorkshopMonthlyPlanRows(store, versionNo);
        for (int i = 0; i < rows.size(); i += 1) {
          Row dataRow = sheet.createRow(i + 3);
          MvpStoreIntegrationDomain.writeRowValues(dataRow, rows.get(i), MvpStoreIntegrationDomain.MONTHLY_PLAN_KEYS, bodyStyle);
        }

        for (int i = 0; i < MvpStoreIntegrationDomain.MONTHLY_PLAN_HEADERS_CN.length; i += 1) {
          sheet.setColumnWidth(i, 15 * 256);
        }
        workbook.write(output);
        return output.toByteArray();
      } catch (IOException ex) {
        throw new MvpServiceException(500, "EXPORT_FAILED", "Failed to export workshop monthly plan.", true);
      }
    }
  }
}
