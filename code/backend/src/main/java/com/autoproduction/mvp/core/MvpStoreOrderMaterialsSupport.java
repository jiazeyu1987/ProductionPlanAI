package com.autoproduction.mvp.core;

import com.autoproduction.mvp.core.MvpStoreRuntimeBase.CachedOrderPoolMaterials;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.autoproduction.mvp.core.MvpStoreCoreExceptionSupport.number;
import static com.autoproduction.mvp.core.MvpStoreCoreExceptionSupport.string;
import static com.autoproduction.mvp.core.MvpStoreCoreNormalizationSupport.copyMaterialRows;
import static com.autoproduction.mvp.core.MvpStoreCoreNormalizationSupport.freezeMaterialRows;
import static com.autoproduction.mvp.core.MvpStoreCoreNormalizationSupport.isSameMaterialCode;
import static com.autoproduction.mvp.core.MvpStoreCoreNormalizationSupport.materialListBaseCode;
import static com.autoproduction.mvp.core.MvpStoreCoreNormalizationSupport.normalizeMaterialCode;

final class MvpStoreOrderMaterialsSupport {
  private MvpStoreOrderMaterialsSupport() {}

  static List<Map<String, Object>> listOrderPoolMaterials(
    MvpStoreOrderDomain domain,
    String orderNo,
    boolean refreshFromErp
  ) {
    String normalizedOrderNo = orderNo == null ? "" : orderNo.trim();
    if (normalizedOrderNo.isBlank()) {
      throw domain.badRequest("orderNo is required.");
    }

    CachedOrderPoolMaterials cached = domain.orderPoolMaterialsCache.get(normalizedOrderNo);
    if (!refreshFromErp && cached != null) {
      return copyMaterialRows(cached.rows);
    }

    Map<String, Object> orderRow = domain.erpDataManager.getProductionOrders().stream()
      .filter(row -> normalizedOrderNo.equals(string(row, "production_order_no", null)))
      .findFirst()
      .orElse(null);
    if (orderRow == null) {
      domain.orderPoolMaterialsCache.put(
        normalizedOrderNo,
        new CachedOrderPoolMaterials(List.of(), OffsetDateTime.now(ZoneOffset.UTC).toString())
      );
      return List.of();
    }
    String materialListNo = string(orderRow, "material_list_no", null);
    String productCode = string(orderRow, "product_code", null);
    List<Map<String, Object>> rawRows = domain.erpDataManager.getProductionMaterialIssuesByOrder(
      normalizedOrderNo,
      materialListNo,
      refreshFromErp
    );
    boolean hasExpandedBomRows = rawRows.stream()
      .anyMatch(row -> "ERP_API_BOM_VIEW_ENTRY".equalsIgnoreCase(string(row, "erp_source_table", "")));
    if (!hasExpandedBomRows) {
      String ppBomNo = rawRows.stream()
        .map(row -> string(row, "pick_material_bill_no", string(row, "source_bill_no", null)))
        .filter(value -> value != null && value.startsWith("PPBOM"))
        .findFirst()
        .orElse(null);
      if (ppBomNo != null) {
        List<Map<String, Object>> ppBomRows = domain.erpDataManager.getProductionMaterialIssuesByOrder(null, ppBomNo, refreshFromErp);
        if (!ppBomRows.isEmpty()) {
          rawRows = ppBomRows;
        }
      }
    }
    String normalizedProductCode = normalizeMaterialCode(productCode);
    String normalizedMaterialListNo = normalizeMaterialCode(materialListNo);
    String normalizedMaterialListBaseCode = materialListBaseCode(normalizedMaterialListNo);
    List<Map<String, Object>> rows = new ArrayList<>();
    Set<String> seenCodes = new HashSet<>();
    for (Map<String, Object> rawRow : rawRows) {
      String code = normalizeMaterialCode(string(rawRow, "child_material_code", null));
      if (code.isBlank() || "UNKNOWN".equals(code) || !seenCodes.add(code)) {
        continue;
      }
      if (
        isSameMaterialCode(code, normalizedProductCode)
          || isSameMaterialCode(code, normalizedMaterialListNo)
          || isSameMaterialCode(code, normalizedMaterialListBaseCode)
      ) {
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("order_no", normalizedOrderNo);
      row.put("material_list_no", materialListNo);
      row.put("child_material_code", code);
      row.put("child_material_name_cn", string(rawRow, "child_material_name_cn", ""));
      row.put("required_qty", number(rawRow, "required_qty", 0d));
      row.put("source_bill_no", string(rawRow, "source_bill_no", ""));
      row.put("source_bill_type", string(rawRow, "source_bill_type", ""));
      row.put("pick_material_bill_no", string(rawRow, "pick_material_bill_no", ""));
      row.put("issue_date", string(rawRow, "issue_date", ""));
      Map<String, Object> supplyInfo = domain.erpDataManager.getMaterialSupplyInfo(code);
      row.put("child_material_supply_type", string(supplyInfo, "supply_type", "UNKNOWN"));
      row.put("child_material_supply_type_name_cn", string(supplyInfo, "supply_type_name_cn", "未知"));
      rows.add(domain.localizeRow(row));
    }
    rows.sort(Comparator.comparing(row -> string(row, "child_material_code", "")));
    List<Map<String, Object>> cachedRows = freezeMaterialRows(rows);
    domain.orderPoolMaterialsCache.put(
      normalizedOrderNo,
      new CachedOrderPoolMaterials(cachedRows, OffsetDateTime.now(ZoneOffset.UTC).toString())
    );
    return copyMaterialRows(cachedRows);
  }

  static List<Map<String, Object>> listMaterialChildrenByParentCode(
    MvpStoreOrderDomain domain,
    String parentMaterialCode,
    boolean refreshFromErp
  ) {
    String normalizedParentCode = normalizeMaterialCode(parentMaterialCode);
    if (normalizedParentCode.isBlank()) {
      throw domain.badRequest("parentMaterialCode is required.");
    }

    CachedOrderPoolMaterials cached = domain.materialChildrenByParentCache.get(normalizedParentCode);
    if (!refreshFromErp && cached != null) {
      return copyMaterialRows(cached.rows);
    }

    List<Map<String, Object>> rawRows = domain.erpDataManager.getProductionMaterialIssuesByOrder(
      null,
      normalizedParentCode,
      refreshFromErp
    );
    String normalizedParentBaseCode = materialListBaseCode(normalizedParentCode);
    List<Map<String, Object>> rows = new ArrayList<>();
    Set<String> seenCodes = new HashSet<>();
    for (Map<String, Object> rawRow : rawRows) {
      String code = normalizeMaterialCode(string(rawRow, "child_material_code", null));
      if (code.isBlank() || "UNKNOWN".equals(code) || !seenCodes.add(code)) {
        continue;
      }
      if (
        isSameMaterialCode(code, normalizedParentCode)
          || isSameMaterialCode(code, normalizedParentBaseCode)
      ) {
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("parent_material_code", normalizedParentCode);
      row.put("child_material_code", code);
      row.put("child_material_name_cn", string(rawRow, "child_material_name_cn", ""));
      row.put("required_qty", number(rawRow, "required_qty", 0d));
      row.put("source_bill_no", string(rawRow, "source_bill_no", ""));
      row.put("source_bill_type", string(rawRow, "source_bill_type", ""));
      row.put("pick_material_bill_no", string(rawRow, "pick_material_bill_no", ""));
      row.put("issue_date", string(rawRow, "issue_date", ""));
      Map<String, Object> supplyInfo = domain.erpDataManager.getMaterialSupplyInfo(code);
      row.put("child_material_supply_type", string(supplyInfo, "supply_type", "UNKNOWN"));
      row.put("child_material_supply_type_name_cn", string(supplyInfo, "supply_type_name_cn", "未知"));
      rows.add(domain.localizeRow(row));
    }
    rows.sort(Comparator.comparing(row -> string(row, "child_material_code", "")));
    List<Map<String, Object>> cachedRows = freezeMaterialRows(rows);
    domain.materialChildrenByParentCache.put(
      normalizedParentCode,
      new CachedOrderPoolMaterials(cachedRows, OffsetDateTime.now(ZoneOffset.UTC).toString())
    );
    return copyMaterialRows(cachedRows);
  }
}

