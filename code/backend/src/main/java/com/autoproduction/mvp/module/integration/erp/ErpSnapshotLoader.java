package com.autoproduction.mvp.module.integration.erp;

import com.autoproduction.mvp.core.ErpSqliteOrderLoader;
import com.autoproduction.mvp.module.integration.erp.manager.ErpSnapshot;
import java.util.List;
import java.util.Map;

final class ErpSnapshotLoader {
  private final ErpSqliteOrderLoader loader;

  ErpSnapshotLoader(ErpSqliteOrderLoader loader) {
    this.loader = loader;
  }

  ErpSnapshot loadSnapshot() {
    List<Map<String, Object>> salesOrderLines = ErpImmutableRows.safeRows(loader.loadSalesOrderLines());
    List<Map<String, Object>> productionOrders = ErpImmutableRows.safeRows(loader.loadProductionOrders());
    List<Map<String, Object>> purchaseOrders = ErpImmutableRows.safeRows(loader.loadPurchaseOrders());
    List<Map<String, Object>> salesOrderHeadersRaw = ErpImmutableRows.safeRows(loader.loadSalesOrderHeadersRaw());
    List<Map<String, Object>> salesOrderLinesRaw = ErpImmutableRows.safeRows(loader.loadSalesOrderLinesRaw());
    List<Map<String, Object>> productionOrderHeadersRaw = ErpImmutableRows.safeRows(loader.loadProductionOrderHeadersRaw());
    List<Map<String, Object>> productionOrderLinesRaw = ErpImmutableRows.safeRows(loader.loadProductionOrderLinesRaw());
    return new ErpSnapshot(
      ErpImmutableRows.freezeRows(salesOrderLines),
      ErpImmutableRows.freezeRows(productionOrders),
      ErpImmutableRows.freezeRows(purchaseOrders),
      ErpImmutableRows.freezeRows(salesOrderHeadersRaw),
      ErpImmutableRows.freezeRows(salesOrderLinesRaw),
      ErpImmutableRows.freezeRows(productionOrderHeadersRaw),
      ErpImmutableRows.freezeRows(productionOrderLinesRaw)
    );
  }
}

