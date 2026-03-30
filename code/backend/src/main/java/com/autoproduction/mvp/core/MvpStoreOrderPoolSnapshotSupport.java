package com.autoproduction.mvp.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class MvpStoreOrderPoolSnapshotSupport {
  private MvpStoreOrderPoolSnapshotSupport() {}

  static boolean enabled() {
    String raw = System.getenv("MVP_ORDER_POOL_SNAPSHOT_ENABLED");
    if (raw == null || raw.isBlank()) {
      raw = System.getenv("MVP_ORDER_POOL_PERSIST_ENABLED");
    }
    if (raw == null || raw.isBlank()) {
      return false;
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    return normalized.equals("1") || normalized.equals("true") || normalized.equals("yes") || normalized.equals("on");
  }

  static Path resolvePath() {
    String raw = System.getenv("MVP_ORDER_POOL_SNAPSHOT_PATH");
    if (raw == null || raw.isBlank()) {
      raw = System.getenv("MVP_ORDER_POOL_PERSIST_PATH");
    }
    if (raw != null && !raw.isBlank()) {
      return Path.of(raw.trim());
    }
    String home = System.getProperty("user.home");
    if (home == null || home.isBlank()) {
      return Path.of(".autoproduction", "order-pool.snapshot.json");
    }
    return Path.of(home, ".autoproduction", "order-pool.snapshot.json");
  }

  static void restoreInto(MvpStoreOrderDomain domain) {
    if (!enabled()) {
      return;
    }
    Path path = resolvePath();
    if (!Files.exists(path)) {
      return;
    }
    try {
      String raw = Files.readString(path, StandardCharsets.UTF_8);
      if (raw == null || raw.isBlank()) {
        return;
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> decoded = domain.objectMapper.readValue(raw, Map.class);
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> orders = decoded == null ? null : (List<Map<String, Object>>) decoded.get("orders");
      if (orders == null || orders.isEmpty()) {
        return;
      }

      int restored = 0;
      for (Map<String, Object> payload : orders) {
        if (payload == null || payload.isEmpty()) {
          continue;
        }
        String orderType = domain.string(payload, "orderType", domain.string(payload, "order_type", "production"));
        if (!"production".equalsIgnoreCase(orderType)) {
          continue;
        }
        String orderNo = domain.string(payload, "orderNo", domain.string(payload, "order_no", null));
        if (orderNo == null || orderNo.isBlank()) {
          continue;
        }
        MvpDomain.Order existing = domain.state.orders.stream()
          .filter(item -> orderNo.equals(item.orderNo))
          .findFirst()
          .orElse(null);
        if (existing == null) {
          domain.state.orders.add(domain.fromOrderPayload(payload));
        } else {
          domain.applyOrderPatch(existing, payload);
        }
        restored++;
      }

      if (restored > 0) {
        domain.orderPoolMaterialsCache.clear();
        domain.materialChildrenByParentCache.clear();
        domain.finalCompletedByOrderProductCache.clear();
      }
    } catch (Exception e) {
      System.err.println("[WARN] Failed to restore order-pool snapshot: " + e.getMessage());
    }
  }

  static void persistFrom(MvpStoreOrderDomain domain) {
    if (!enabled()) {
      return;
    }

    List<Map<String, Object>> rows = new ArrayList<>();
    for (MvpDomain.Order order : domain.state.orders) {
      if (order == null || order.orderNo == null) {
        continue;
      }
      if (!"production".equalsIgnoreCase(String.valueOf(order.orderType))) {
        continue;
      }
      rows.add(buildPersistablePayload(domain, order));
    }

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("snapshot_at", OffsetDateTime.now(ZoneOffset.UTC).toString());
    out.put("orders", rows);

    Path path = resolvePath();
    try {
      Path parent = path.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Path tmp = parent == null
        ? Path.of(path.getFileName().toString() + ".tmp")
        : parent.resolve(path.getFileName().toString() + ".tmp");

      String json = domain.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(out);
      Files.writeString(tmp, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      try {
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (Exception ignore) {
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException e) {
      System.err.println("[WARN] Failed to persist order-pool snapshot: " + e.getMessage());
    }
  }

  private static Map<String, Object> buildPersistablePayload(MvpStoreOrderDomain domain, MvpDomain.Order order) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("orderNo", order.orderNo);
    payload.put("order_no", order.orderNo);
    payload.put("orderType", order.orderType);
    payload.put("order_type", order.orderType);
    payload.put("dueDate", order.dueDate == null ? null : order.dueDate.toString());
    payload.put("due_date", order.dueDate == null ? null : order.dueDate.toString());
    payload.put("urgent", order.urgent);
    payload.put("urgent_flag", order.urgent ? 1 : 0);
    payload.put("frozen", order.frozen);
    payload.put("frozen_flag", order.frozen ? 1 : 0);
    payload.put("lockFlag", order.lockFlag);
    payload.put("lock_flag", order.lockFlag ? 1 : 0);
    payload.put("status", order.status);
    payload.put("order_status", order.status);
    payload.put("expected_start_date", order.expectedStartDate == null ? null : order.expectedStartDate.toString());

    List<Map<String, Object>> items = new ArrayList<>();
    if (order.items != null) {
      for (MvpDomain.OrderItem item : order.items) {
        Map<String, Object> itemRow = new LinkedHashMap<>();
        itemRow.put("productCode", item.productCode);
        itemRow.put("product_code", item.productCode);
        itemRow.put("qty", item.qty);
        itemRow.put("completedQty", item.completedQty);
        itemRow.put("completed_qty", item.completedQty);
        items.add(itemRow);
      }
    }
    payload.put("items", items);

    MvpDomain.OrderBusinessData business = domain.businessData(order);
    if (business != null) {
      payload.put("order_date", business.orderDate == null ? null : business.orderDate.toString());
      payload.put("customer_remark", business.customerRemark);
      payload.put("product_name_cn", business.productName);
      payload.put("spec_model", business.specModel);
      payload.put("production_batch_no", business.productionBatchNo);
      payload.put("packaging_form", business.packagingForm);
      payload.put("sales_order_no", business.salesOrderNo);
      payload.put("production_date_foreign_trade", business.productionDateForeignTrade);
      payload.put("purchase_due_date", business.purchaseDueDate);
      payload.put("injection_due_date", business.injectionDueDate);
      payload.put("market_remark_info", business.marketRemarkInfo);
      payload.put("market_demand", business.marketDemand);
      payload.put("planned_finish_date_1", business.plannedFinishDate1);
      payload.put("planned_finish_date_2", business.plannedFinishDate2);
      payload.put("semi_finished_code", business.semiFinishedCode);
      payload.put("semi_finished_inventory", business.semiFinishedInventory);
      payload.put("semi_finished_demand", business.semiFinishedDemand);
      payload.put("semi_finished_wip", business.semiFinishedWip);
      payload.put("need_order_qty", business.needOrderQty);
      payload.put("pending_inbound_qty", business.pendingInboundQty);
      payload.put("weekly_monthly_process_plan", business.weeklyMonthlyPlanRemark);
      payload.put("workshop_outer_packaging_date", business.workshopOuterPackagingDate);
      payload.put("note", business.note);
      payload.put("workshop_completed_qty", business.workshopCompletedQty);
      payload.put("workshop_completed_time", business.workshopCompletedTime);
      payload.put("outer_completed_qty", business.outerCompletedQty);
      payload.put("outer_completed_time", business.outerCompletedTime);
      payload.put("match_status", business.matchStatus);
    }
    return payload;
  }
}
