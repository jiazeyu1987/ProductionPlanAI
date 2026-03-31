package com.autoproduction.mvp.core;

import com.autoproduction.mvp.core.MvpStoreRuntimeBase.CachedOrderPoolMaterials;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    Map<String, Object> orderRow = resolveProductionOrderRow(domain, normalizedOrderNo);
    String materialListNo = orderRow == null ? null : string(orderRow, "material_list_no", null);
    String productCode = orderRow == null ? null : string(orderRow, "product_code", null);

    // Keep detail-page behavior aligned with the ERP test page:
    // query by production order number first, then optionally refine with material list no.
    List<Map<String, Object>> rawRows = domain.erpDataManager.getProductionMaterialIssuesByOrder(
      normalizedOrderNo,
      null,
      refreshFromErp
    );
    if ((rawRows == null || rawRows.isEmpty()) && materialListNo != null && !materialListNo.isBlank()) {
      rawRows = domain.erpDataManager.getProductionMaterialIssuesByOrder(
        normalizedOrderNo,
        materialListNo,
        refreshFromErp
      );
    }
    if (rawRows == null || rawRows.isEmpty()) {
      return List.of();
    }
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
    Map<String, Map<String, Object>> preferredRawRowsByCode = new LinkedHashMap<>();
    for (Map<String, Object> rawRow : rawRows) {
      String code = normalizeMaterialCode(string(rawRow, "child_material_code", null));
      if (code.isBlank() || "UNKNOWN".equals(code)) {
        continue;
      }
      if (
        isSameMaterialCode(code, normalizedProductCode)
          || isSameMaterialCode(code, normalizedMaterialListNo)
          || isSameMaterialCode(code, normalizedMaterialListBaseCode)
      ) {
        continue;
      }
      preferredRawRowsByCode.merge(code, rawRow, MvpStoreOrderMaterialsSupport::preferMoreCompleteMaterialRow);
    }
    List<Map<String, Object>> rows = new ArrayList<>();
    for (Map.Entry<String, Map<String, Object>> entry : preferredRawRowsByCode.entrySet()) {
      String code = entry.getKey();
      Map<String, Object> rawRow = entry.getValue();
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("order_no", normalizedOrderNo);
      row.put("material_list_no", materialListNo);
      row.put("child_material_code", code);
      row.put("child_material_name_cn", string(rawRow, "child_material_name_cn", ""));
      row.put("spec_model", string(rawRow, "spec_model", ""));
      row.put("issue_qty", number(rawRow, "issue_qty", number(rawRow, "required_qty", 0d)));
      row.put("required_qty", number(rawRow, "required_qty", 0d));
      row.put("source_bill_no", string(rawRow, "source_bill_no", ""));
      row.put("source_bill_type", string(rawRow, "source_bill_type", ""));
      row.put("pick_material_bill_no", string(rawRow, "pick_material_bill_no", ""));
      row.put("issue_date", string(rawRow, "issue_date", ""));
      Map<String, Object> supplyInfo = safeMaterialSupplyInfo(domain, code);
      row.put("child_material_supply_type", string(supplyInfo, "supply_type", "UNKNOWN"));
      row.put("child_material_supply_type_name_cn", string(supplyInfo, "supply_type_name_cn", "未知"));
      rows.add(domain.localizeRow(row));
    }
    rows.sort(Comparator.comparing(row -> string(row, "child_material_code", "")));
    if (rows.isEmpty()) {
      return List.of();
    }
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
    Map<String, Map<String, Object>> preferredRawRowsByCode = new LinkedHashMap<>();
    for (Map<String, Object> rawRow : rawRows) {
      String code = normalizeMaterialCode(string(rawRow, "child_material_code", null));
      if (code.isBlank() || "UNKNOWN".equals(code)) {
        continue;
      }
      if (
        isSameMaterialCode(code, normalizedParentCode)
          || isSameMaterialCode(code, normalizedParentBaseCode)
      ) {
        continue;
      }
      preferredRawRowsByCode.merge(code, rawRow, MvpStoreOrderMaterialsSupport::preferMoreCompleteMaterialRow);
    }
    List<Map<String, Object>> rows = new ArrayList<>();
    for (Map.Entry<String, Map<String, Object>> entry : preferredRawRowsByCode.entrySet()) {
      String code = entry.getKey();
      Map<String, Object> rawRow = entry.getValue();
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("parent_material_code", normalizedParentCode);
      row.put("child_material_code", code);
      row.put("child_material_name_cn", string(rawRow, "child_material_name_cn", ""));
      row.put("spec_model", string(rawRow, "spec_model", ""));
      row.put("issue_qty", number(rawRow, "issue_qty", number(rawRow, "required_qty", 0d)));
      row.put("required_qty", number(rawRow, "required_qty", 0d));
      row.put("source_bill_no", string(rawRow, "source_bill_no", ""));
      row.put("source_bill_type", string(rawRow, "source_bill_type", ""));
      row.put("pick_material_bill_no", string(rawRow, "pick_material_bill_no", ""));
      row.put("issue_date", string(rawRow, "issue_date", ""));
      Map<String, Object> supplyInfo = safeMaterialSupplyInfo(domain, code);
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

  private static Map<String, Object> resolveProductionOrderRow(MvpStoreOrderDomain domain, String normalizedOrderNo) {
    Map<String, Object> cachedOrderRow = findProductionOrderRow(domain.erpDataManager.getProductionOrders(), normalizedOrderNo);
    if (cachedOrderRow != null) {
      return cachedOrderRow;
    }
    return findProductionOrderRow(domain.erpDataManager.loadProductionOrdersLive(), normalizedOrderNo);
  }

  private static Map<String, Object> safeMaterialSupplyInfo(MvpStoreOrderDomain domain, String materialCode) {
    try {
      Map<String, Object> supplyInfo = domain.erpDataManager.getMaterialSupplyInfo(materialCode);
      if (supplyInfo != null && !supplyInfo.isEmpty()) {
        return supplyInfo;
      }
    } catch (RuntimeException ignore) {
      // Material attribute is auxiliary metadata. Keep root and child rows renderable when ERP metadata lookup fails.
    }
    return Map.of(
      "supply_type", "UNKNOWN",
      "supply_type_name_cn", "\u672A\u77E5"
    );
  }

  private static Map<String, Object> findProductionOrderRow(List<Map<String, Object>> rows, String normalizedOrderNo) {
    if (rows == null || rows.isEmpty()) {
      return null;
    }
    return rows.stream()
      .filter(row -> normalizedOrderNo.equals(string(row, "production_order_no", null)))
      .findFirst()
      .orElse(null);
  }

  private static Map<String, Object> preferMoreCompleteMaterialRow(
    Map<String, Object> current,
    Map<String, Object> candidate
  ) {
    if (candidate == null) {
      return current;
    }
    if (current == null) {
      return candidate;
    }
    int currentScore = materialRowCompletenessScore(current);
    int candidateScore = materialRowCompletenessScore(candidate);
    if (candidateScore > currentScore) {
      return candidate;
    }
    return current;
  }

  private static int materialRowCompletenessScore(Map<String, Object> row) {
    int score = 0;
    if (!string(row, "child_material_name_cn", "").isBlank()) {
      score += 4;
    }
    if (!string(row, "spec_model", "").isBlank()) {
      score += 2;
    }
    if ("ERP_API_BOM_VIEW_ENTRY".equalsIgnoreCase(string(row, "erp_source_table", ""))) {
      score += 1;
    }
    return score;
  }
}
