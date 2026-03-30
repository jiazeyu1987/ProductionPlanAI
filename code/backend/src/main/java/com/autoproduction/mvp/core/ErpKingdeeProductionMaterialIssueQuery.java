package com.autoproduction.mvp.core;

import java.util.List;
import java.util.Map;

final class ErpKingdeeProductionMaterialIssueQuery {
  private final ErpKingdeeProductionMaterialIssueQueryDelegate delegate;

  ErpKingdeeProductionMaterialIssueQuery(
    ErpKingdeeBillQueryClient billQueryClient,
    ErpOrderPayloadMapper payloadMapper,
    ErpOrderNormalizer normalizer,
    ErpSqliteOrderValidator validator,
    ErpSqliteOrderRowMapper rowMapper
  ) {
    this.delegate = new ErpKingdeeProductionMaterialIssueQueryDelegate(
      billQueryClient,
      payloadMapper,
      normalizer,
      validator,
      rowMapper
    );
  }

  List<Map<String, Object>> loadProductionMaterialIssuesFromApiByOrder(
    String productionOrderNo,
    String materialListNo,
    boolean forceRefresh
  ) {
    return delegate.loadProductionMaterialIssuesFromApiByOrder(productionOrderNo, materialListNo, forceRefresh);
  }
}
