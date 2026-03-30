package com.autoproduction.mvp.core;

import com.autoproduction.mvp.core.MvpStoreRuntimeBase.MaterialUnionAggregate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.autoproduction.mvp.core.MvpStoreCoreExceptionSupport.number;
import static com.autoproduction.mvp.core.MvpStoreCoreExceptionSupport.string;
import static com.autoproduction.mvp.core.MvpStoreCoreNormalizationSupport.normalizeMaterialCode;
import static com.autoproduction.mvp.core.MvpStoreCoreNormalizationSupport.round3;

final class MvpStoreOrderMaterialAvailabilityUnionSupport {
  private MvpStoreOrderMaterialAvailabilityUnionSupport() {}

  static List<Map<String, Object>> buildOrderMaterialAvailabilityUnionRows(
    MvpStoreOrderDomain domain,
    boolean refreshFromErp
  ) {
    List<Map<String, Object>> detailRows = domain.buildOrderMaterialAvailabilityRowsFromOrderPool(refreshFromErp);
    Map<String, MaterialUnionAggregate> aggregateByCode = new LinkedHashMap<>();
    for (Map<String, Object> row : detailRows) {
      String materialCode = normalizeMaterialCode(string(row, "child_material_code", null));
      if (materialCode == null || materialCode.isBlank()) {
        continue;
      }
      MaterialUnionAggregate aggregate = aggregateByCode.computeIfAbsent(materialCode, ignored -> {
        MaterialUnionAggregate item = new MaterialUnionAggregate();
        item.materialCode = materialCode;
        return item;
      });
      String materialName = string(row, "child_material_name_cn", "");
      if (aggregate.materialNameCn == null || aggregate.materialNameCn.isBlank()) {
        aggregate.materialNameCn = materialName;
      }
      aggregate.demandQty += Math.max(0d, number(row, "demand_qty", 0d));
      double stockQty = Math.max(0d, number(row, "stock_qty", 0d));
      if (!aggregate.stockAssigned) {
        aggregate.stockQty = stockQty;
        aggregate.stockAssigned = true;
      } else {
        aggregate.stockQty = Math.max(aggregate.stockQty, stockQty);
      }
      int inventoryMissingFlag = (int) Math.round(number(row, "inventory_missing_flag", 0d));
      if (inventoryMissingFlag == 1) {
        aggregate.inventoryMissing = true;
      }
      String orderNo = string(row, "order_no", null);
      if (orderNo != null && !orderNo.isBlank()) {
        aggregate.orderNos.add(orderNo);
      }
    }

    List<Map<String, Object>> rows = new ArrayList<>();
    for (MaterialUnionAggregate aggregate : aggregateByCode.values()) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("child_material_code", aggregate.materialCode);
      row.put("child_material_name_cn", aggregate.materialNameCn == null ? "" : aggregate.materialNameCn);
      row.put("related_order_count", aggregate.orderNos.size());
      row.put("related_order_nos", String.join("、", aggregate.orderNos));
      row.put("demand_qty", round3(Math.max(0d, aggregate.demandQty)));
      row.put("stock_qty", round3(Math.max(0d, aggregate.stockQty)));
      row.put("shortage_qty", round3(Math.max(0d, aggregate.demandQty - aggregate.stockQty)));
      row.put("data_status", aggregate.inventoryMissing ? "MISSING_STOCK" : "OK");
      rows.add(domain.localizeRow(row));
    }
    rows.sort(Comparator.comparing(item -> string(item, "child_material_code", "")));
    return rows;
  }
}

