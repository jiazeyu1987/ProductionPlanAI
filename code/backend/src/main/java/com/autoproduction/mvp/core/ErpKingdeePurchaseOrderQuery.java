package com.autoproduction.mvp.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class ErpKingdeePurchaseOrderQuery {
  private final ErpKingdeeBillQueryClient billQueryClient;
  private final ErpSqliteOrderRowMapper rowMapper;

  ErpKingdeePurchaseOrderQuery(ErpKingdeeBillQueryClient billQueryClient, ErpSqliteOrderRowMapper rowMapper) {
    this.billQueryClient = billQueryClient;
    this.rowMapper = rowMapper;
  }

  List<Map<String, Object>> queryPurchaseRowsFromApi(String fieldKeys, String orderString) {
    List<Map<String, Object>> result = new ArrayList<>();
    int startRow = 0;
    int limit = 200;
    while (true) {
      List<Map<String, Object>> page = billQueryClient.queryBillRowsFromApi(
        "PUR_PurchaseOrder",
        fieldKeys,
        "",
        orderString,
        startRow,
        limit
      );
      if (page.isEmpty()) {
        break;
      }
      int baseIndex = result.size();
      for (int i = 0; i < page.size(); i += 1) {
        result.add(rowMapper.toPurchaseOrderRowFromApi(page.get(i), baseIndex + i + 1));
      }
      if (page.size() < limit) {
        break;
      }
      startRow += limit;
      if (startRow >= 20_000) {
        break;
      }
    }
    return result;
  }
}

