package com.autoproduction.mvp.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class ErpKingdeeProductionOrderQuery {
  private final ErpKingdeeBillQueryClient billQueryClient;
  private final ErpSqliteOrderValidator validator;
  private final ErpSqliteOrderRowMapper rowMapper;

  ErpKingdeeProductionOrderQuery(
    ErpKingdeeBillQueryClient billQueryClient,
    ErpSqliteOrderValidator validator,
    ErpSqliteOrderRowMapper rowMapper
  ) {
    this.billQueryClient = billQueryClient;
    this.validator = validator;
    this.rowMapper = rowMapper;
  }

  List<Map<String, Object>> queryProductionRowsFromApi(String fieldKeys, String orderString) {
    String filter = validator.buildOrgDateFilter("FPrdOrgId.FNumber", "FDate", 180);
    List<Map<String, Object>> result = new ArrayList<>();
    int startRow = 0;
    int limit = 200;
    while (true) {
      List<Map<String, Object>> page = billQueryClient.queryBillRowsFromApi(
        "PRD_MO",
        fieldKeys,
        filter,
        orderString,
        startRow,
        limit
      );
      if (page.isEmpty()) {
        break;
      }
      int baseIndex = result.size();
      for (int i = 0; i < page.size(); i += 1) {
        result.add(rowMapper.toProductionOrderRowFromApi(page.get(i), baseIndex + i + 1));
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

