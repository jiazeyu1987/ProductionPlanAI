package com.autoproduction.mvp.core;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

final class MvpStoreOrderErpImportSupport {
  private MvpStoreOrderErpImportSupport() {}

  static Map<String, Object> importProductionOrdersFromErp(
    MvpStoreOrderDomain domain,
    Map<String, Object> payload,
    String requestId,
    String operator
  ) {
    String keyword = domain.string(payload, "keyword", domain.string(payload, "name_contains", domain.string(payload, "contains", "")));
    if (keyword == null || keyword.isBlank()) {
      throw domain.badRequest("keyword is required.");
    }

    int limit = (int) Math.round(domain.number(payload, "n", domain.number(payload, "limit", 4d)));
    limit = Math.max(1, Math.min(200, limit));
    boolean completed = domain.bool(payload, "completed", domain.bool(payload, "done", false));

    List<Map<String, Object>> erpRows = domain.erpDataManager.getProductionOrders();
    if (erpRows == null || erpRows.isEmpty()) {
      throw domain.badRequest("ERP production order list is empty.");
    }

    String needle = keyword.trim().toLowerCase(Locale.ROOT);
    List<Map<String, Object>> matched = new ArrayList<>();
    for (Map<String, Object> row : erpRows) {
      if (row == null || row.isEmpty()) {
        continue;
      }
      String name = domain.string(row, "product_name_cn", domain.string(row, "product_name", ""));
      if (name == null) {
        name = "";
      }
      if (name.toLowerCase(Locale.ROOT).contains(needle)) {
        matched.add(row);
      }
    }

    matched.sort(Comparator.comparing((Map<String, Object> row) -> parseSortTime(row)).reversed());
    List<Map<String, Object>> selected = matched.size() <= limit ? matched : matched.subList(0, limit);

    List<String> importedOrderNos = new ArrayList<>();
    for (Map<String, Object> row : selected) {
      Map<String, Object> orderPayload = buildOrderPayload(domain, row, completed);
      if (orderPayload == null) {
        continue;
      }
      Map<String, Object> result = MvpStoreOrderWriteSupport.upsertOrder(domain, orderPayload, requestId, operator);
      importedOrderNos.add(domain.string(result, "order_no", domain.string(result, "orderNo", "")));
    }

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("request_id", requestId);
    out.put("keyword", keyword);
    out.put("requested_count", limit);
    out.put("matched_count", matched.size());
    out.put("imported_count", importedOrderNos.size());
    out.put("completed", completed);
    out.put("imported_order_nos", importedOrderNos);
    return out;
  }

  private static OffsetDateTime parseSortTime(Map<String, Object> row) {
    String raw = MvpStoreCoreExceptionSupport.string(row, "last_update_time", null);
    if (raw == null || raw.isBlank()) {
      raw = MvpStoreCoreExceptionSupport.string(row, "release_time", null);
    }
    if (raw == null || raw.isBlank()) {
      raw = MvpStoreCoreExceptionSupport.string(row, "order_date", null);
    }
    if (raw == null || raw.isBlank()) {
      raw = MvpStoreCoreExceptionSupport.string(row, "expected_finish_time", null);
    }
    if (raw == null || raw.isBlank()) {
      raw = MvpStoreCoreExceptionSupport.string(row, "expected_finish_date", null);
    }
    if (raw == null || raw.isBlank()) {
      return OffsetDateTime.MIN;
    }
    try {
      return OffsetDateTime.parse(raw);
    } catch (Exception ignore) {
      try {
        return LocalDateTime.parse(raw).atOffset(ZoneOffset.UTC);
      } catch (Exception ignoreAgain) {
        LocalDate parsed = MvpStoreEntityMappingDateSupport.parseLocalDateFlexible(raw, null);
        if (parsed == null) {
          return OffsetDateTime.MIN;
        }
        return parsed.atStartOfDay().atOffset(ZoneOffset.UTC);
      }
    }
  }

  private static Map<String, Object> buildOrderPayload(MvpStoreOrderDomain domain, Map<String, Object> row, boolean completed) {
    if (row == null || row.isEmpty()) {
      return null;
    }

    String orderNo = domain.string(
      row,
      "production_order_no",
      domain.string(row, "order_no", domain.string(row, "mo_no", null))
    );
    if (orderNo == null || orderNo.isBlank()) {
      return null;
    }

    String productCode = domain.string(row, "product_code", domain.string(row, "productCode", "UNKNOWN"));
    double qty = domain.number(row, "plan_qty", domain.number(row, "order_qty", domain.number(row, "qty", 0d)));
    if (qty <= 0d) {
      qty = 1d;
    }

    String dueDateRaw = domain.string(
      row,
      "expected_finish_date",
      domain.string(
        row,
        "planned_finish_date_1",
        domain.string(row, "planned_finish_date_2", domain.string(row, "expected_due_date", null))
      )
    );
    LocalDate dueDate = domain.parseLocalDateFlexible(dueDateRaw, domain.state.startDate);

    Map<String, Object> payload = new LinkedHashMap<>(row);
    payload.put("orderNo", orderNo);
    payload.put("orderType", "production");
    payload.put("dueDate", dueDate == null ? domain.state.startDate.toString() : dueDate.toString());
    payload.put("status", completed ? "DONE" : "OPEN");

    Map<String, Object> item = new LinkedHashMap<>();
    item.put("productCode", productCode);
    item.put("qty", qty);
    item.put("completedQty", completed ? qty : 0d);
    payload.put("items", List.of(item));

    if (!payload.containsKey("product_name_cn") && !payload.containsKey("product_name")) {
      payload.put("product_name_cn", domain.productNameCn(productCode));
    }

    String normalizedStatus = completed ? "DONE" : "OPEN";
    if (!Objects.equals(payload.get("order_status"), normalizedStatus)) {
      payload.put("order_status", normalizedStatus);
    }
    return payload;
  }
}

