package com.autoproduction.mvp.module.integration.erp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.autoproduction.mvp.core.ErpSqliteOrderLoader;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ErpDataManagerTest {

  @Test
  void manualRefreshUpdatesCacheAndStatus() {
    ErpSqliteOrderLoader loader = mock(ErpSqliteOrderLoader.class);
    when(loader.loadSalesOrderLines()).thenReturn(List.of(Map.of("sales_order_no", "SO-001")));
    when(loader.loadProductionOrders()).thenReturn(List.of(Map.of("production_order_no", "MO-001")));
    when(loader.loadSalesOrderHeadersRaw()).thenReturn(List.of(Map.of("erp_record_id", "sales_orders_raw:1")));
    when(loader.loadSalesOrderLinesRaw()).thenReturn(List.of(Map.of("erp_line_id", "sales_orders_raw:1:1")));
    when(loader.loadProductionOrderHeadersRaw()).thenReturn(List.of(Map.of("erp_record_id", "production_orders_raw:1")));
    when(loader.loadProductionOrderLinesRaw()).thenReturn(List.of(Map.of("erp_line_id", "production_orders_raw:1:1")));

    ErpDataManager manager = new ErpDataManager(loader, true, 5_000L, true);
    Map<String, Object> result = manager.refreshManual("req-erp-manual", "tester", "manual refresh");

    assertEquals("SUCCESS", result.get("status"));
    assertEquals(1, manager.getSalesOrderLines().size());
    assertEquals(1, manager.getProductionOrders().size());

    Map<String, Object> status = manager.getRefreshStatus("req-status");
    Map<String, Object> refreshState = map(status.get("refresh_state"));
    assertEquals(1L, number(refreshState.get("success_count")));
  }

  @Test
  void triggerRefreshHonorsCooldown() {
    ErpSqliteOrderLoader loader = mock(ErpSqliteOrderLoader.class);
    when(loader.loadSalesOrderLines()).thenReturn(List.of());
    when(loader.loadProductionOrders()).thenReturn(List.of());
    when(loader.loadSalesOrderHeadersRaw()).thenReturn(List.of());
    when(loader.loadSalesOrderLinesRaw()).thenReturn(List.of());
    when(loader.loadProductionOrderHeadersRaw()).thenReturn(List.of());
    when(loader.loadProductionOrderLinesRaw()).thenReturn(List.of());

    ErpDataManager manager = new ErpDataManager(loader, true, 600_000L, true);
    manager.refreshManual("req-initial", "tester", "seed");
    Map<String, Object> result = manager.refreshTriggered("MES_REPORTING", "req-trigger", "event");

    assertEquals("SKIPPED", result.get("status"));
    assertTrue(Boolean.TRUE.equals(result.get("skipped")));
    verify(loader, times(1)).loadSalesOrderLines();
  }

  @Test
  void scheduledRefreshSkipsWhenDisabled() {
    ErpSqliteOrderLoader loader = mock(ErpSqliteOrderLoader.class);
    ErpDataManager manager = new ErpDataManager(loader, false, 5_000L, true);

    Map<String, Object> result = manager.refreshScheduled();

    assertEquals("SKIPPED", result.get("status"));
    verifyNoInteractions(loader);
  }

  @Test
  void failedRefreshKeepsLastSuccessfulSnapshot() {
    ErpSqliteOrderLoader loader = mock(ErpSqliteOrderLoader.class);
    when(loader.loadSalesOrderLines())
      .thenReturn(List.of(Map.of("sales_order_no", "SO-001")))
      .thenThrow(new RuntimeException("boom"));
    when(loader.loadProductionOrders()).thenReturn(List.of());
    when(loader.loadSalesOrderHeadersRaw()).thenReturn(List.of());
    when(loader.loadSalesOrderLinesRaw()).thenReturn(List.of());
    when(loader.loadProductionOrderHeadersRaw()).thenReturn(List.of());
    when(loader.loadProductionOrderLinesRaw()).thenReturn(List.of());

    ErpDataManager manager = new ErpDataManager(loader, true, 0L, true);
    Map<String, Object> first = manager.refreshManual("req-ok", "tester", "first");
    Map<String, Object> second = manager.refreshManual("req-fail", "tester", "second");

    assertEquals("SUCCESS", first.get("status"));
    assertEquals("FAILED", second.get("status"));
    assertEquals(1, manager.getSalesOrderLines().size());

    Map<String, Object> status = manager.getRefreshStatus("req-status");
    Map<String, Object> refreshState = map(status.get("refresh_state"));
    assertEquals(1L, number(refreshState.get("failure_count")));
  }

  @Test
  void materialIssueQueryUsesShortTermCache() {
    ErpSqliteOrderLoader loader = mock(ErpSqliteOrderLoader.class);
    when(loader.loadProductionMaterialIssuesByOrder("MO-001", "PPBOM001", false))
      .thenReturn(List.of(Map.of("child_material_code", "A001")));

    ErpDataManager manager = new ErpDataManager(loader, true, 5_000L, true);
    List<Map<String, Object>> first = manager.getProductionMaterialIssuesByOrder("MO-001", "PPBOM001");
    List<Map<String, Object>> second = manager.getProductionMaterialIssuesByOrder("MO-001", "PPBOM001");

    assertEquals(1, first.size());
    assertEquals(first, second);
    verify(loader, times(1)).loadProductionMaterialIssuesByOrder("MO-001", "PPBOM001", false);
  }

  @Test
  void materialIssueQueryForceRefreshBypassesCache() {
    ErpSqliteOrderLoader loader = mock(ErpSqliteOrderLoader.class);
    when(loader.loadProductionMaterialIssuesByOrder("MO-001", "PPBOM001", false))
      .thenReturn(List.of(Map.of("child_material_code", "A001")));
    when(loader.loadProductionMaterialIssuesByOrder("MO-001", "PPBOM001", true))
      .thenReturn(List.of(Map.of("child_material_code", "A002")));

    ErpDataManager manager = new ErpDataManager(loader, true, 5_000L, true);
    List<Map<String, Object>> cached = manager.getProductionMaterialIssuesByOrder("MO-001", "PPBOM001");
    List<Map<String, Object>> refreshed = manager.getProductionMaterialIssuesByOrder("MO-001", "PPBOM001", true);
    List<Map<String, Object>> afterRefresh = manager.getProductionMaterialIssuesByOrder("MO-001", "PPBOM001");

    assertEquals("A001", cached.get(0).get("child_material_code"));
    assertEquals("A002", refreshed.get(0).get("child_material_code"));
    assertEquals("A002", afterRefresh.get(0).get("child_material_code"));
    verify(loader, times(1)).loadProductionMaterialIssuesByOrder("MO-001", "PPBOM001", false);
    verify(loader, times(1)).loadProductionMaterialIssuesByOrder("MO-001", "PPBOM001", true);
  }

  @Test
  void materialSupplyInfoUsesCodeCache() {
    ErpSqliteOrderLoader loader = mock(ErpSqliteOrderLoader.class);
    when(loader.loadMaterialSupplyInfo("A001.02.012.2005"))
      .thenReturn(Map.of("material_code", "A001.02.012.2005", "supply_type", "SELF_MADE"));

    ErpDataManager manager = new ErpDataManager(loader, true, 5_000L, true);
    Map<String, Object> first = manager.getMaterialSupplyInfo("A001.02.012.2005");
    Map<String, Object> second = manager.getMaterialSupplyInfo("A001.02.012.2005");

    assertEquals("SELF_MADE", first.get("supply_type"));
    assertEquals(first, second);
    verify(loader, times(1)).loadMaterialSupplyInfo("A001.02.012.2005");
  }

  @Test
  void materialSupplyInfoFallsBackToPurchaseOrdersWhenUnknown() {
    ErpSqliteOrderLoader loader = mock(ErpSqliteOrderLoader.class);
    when(loader.loadMaterialSupplyInfo("A001.02.012.2005"))
      .thenReturn(Map.of("material_code", "A001.02.012.2005", "supply_type", "UNKNOWN"));
    when(loader.loadPurchaseOrders())
      .thenReturn(List.of(Map.of("material_code", "A001.02.012.2005")));
    when(loader.loadSalesOrderLines()).thenReturn(List.of());
    when(loader.loadProductionOrders()).thenReturn(List.of());
    when(loader.loadSalesOrderHeadersRaw()).thenReturn(List.of());
    when(loader.loadSalesOrderLinesRaw()).thenReturn(List.of());
    when(loader.loadProductionOrderHeadersRaw()).thenReturn(List.of());
    when(loader.loadProductionOrderLinesRaw()).thenReturn(List.of());

    ErpDataManager manager = new ErpDataManager(loader, true, 5_000L, true);
    Map<String, Object> result = manager.getMaterialSupplyInfo("A001.02.012.2005");

    assertEquals("PURCHASED", result.get("supply_type"));
    assertEquals("\u5916\u8D2D", result.get("supply_type_name_cn"));
    verify(loader, times(1)).loadMaterialSupplyInfo("A001.02.012.2005");
  }

  @Test
  void materialSupplyInfoFallsBackToProductionOrdersWhenUnknown() {
    ErpSqliteOrderLoader loader = mock(ErpSqliteOrderLoader.class);
    when(loader.loadMaterialSupplyInfo("YXN.009.020.1047"))
      .thenReturn(Map.of("material_code", "YXN.009.020.1047", "supply_type", "UNKNOWN"));
    when(loader.loadProductionOrders())
      .thenReturn(List.of(Map.of("product_code", "YXN.009.020.1047")));
    when(loader.loadPurchaseOrders()).thenReturn(List.of());
    when(loader.loadSalesOrderLines()).thenReturn(List.of());
    when(loader.loadSalesOrderHeadersRaw()).thenReturn(List.of());
    when(loader.loadSalesOrderLinesRaw()).thenReturn(List.of());
    when(loader.loadProductionOrderHeadersRaw()).thenReturn(List.of());
    when(loader.loadProductionOrderLinesRaw()).thenReturn(List.of());

    ErpDataManager manager = new ErpDataManager(loader, true, 5_000L, true);
    Map<String, Object> result = manager.getMaterialSupplyInfo("YXN.009.020.1047");

    assertEquals("SELF_MADE", result.get("supply_type"));
    assertEquals("\u81EA\u5236", result.get("supply_type_name_cn"));
    verify(loader, times(1)).loadMaterialSupplyInfo("YXN.009.020.1047");
  }

  @Test
  void materialSupplyInfoFallsBackToCodeRuleWhenSnapshotMissed() {
    ErpSqliteOrderLoader loader = mock(ErpSqliteOrderLoader.class);
    when(loader.loadMaterialSupplyInfo("A002.11.001.000008"))
      .thenReturn(Map.of("material_code", "A002.11.001.000008", "supply_type", "UNKNOWN"));
    when(loader.loadProductionOrders()).thenReturn(List.of());
    when(loader.loadPurchaseOrders()).thenReturn(List.of());
    when(loader.loadSalesOrderLines()).thenReturn(List.of());
    when(loader.loadSalesOrderHeadersRaw()).thenReturn(List.of());
    when(loader.loadSalesOrderLinesRaw()).thenReturn(List.of());
    when(loader.loadProductionOrderHeadersRaw()).thenReturn(List.of());
    when(loader.loadProductionOrderLinesRaw()).thenReturn(List.of());

    ErpDataManager manager = new ErpDataManager(loader, true, 5_000L, true);
    Map<String, Object> result = manager.getMaterialSupplyInfo("A002.11.001.000008");

    assertEquals("PURCHASED", result.get("supply_type"));
    assertEquals("\u5916\u8D2D", result.get("supply_type_name_cn"));
    verify(loader, times(1)).loadMaterialSupplyInfo("A002.11.001.000008");
  }

  @Test
  void materialInventoryByCodesUsesCacheByDefault() {
    ErpSqliteOrderLoader loader = mock(ErpSqliteOrderLoader.class);
    when(loader.loadMaterialInventoryByCodes(List.of("A001.02.012.2005"), false))
      .thenReturn(List.of(Map.of("material_code", "A001.02.012.2005", "stock_qty", 321.0)));

    ErpDataManager manager = new ErpDataManager(loader, true, 5_000L, true);
    Map<String, Map<String, Object>> first = manager.getMaterialInventoryByCodes(List.of("A001.02.012.2005"), false);
    Map<String, Map<String, Object>> second = manager.getMaterialInventoryByCodes(List.of("A001.02.012.2005"), false);

    assertEquals(321.0, ((Number) first.get("A001.02.012.2005").get("stock_qty")).doubleValue());
    assertEquals(321.0, ((Number) second.get("A001.02.012.2005").get("stock_qty")).doubleValue());
    verify(loader, times(1)).loadMaterialInventoryByCodes(List.of("A001.02.012.2005"), false);
  }

  @Test
  void materialInventoryByCodesSupportsForceRefresh() {
    ErpSqliteOrderLoader loader = mock(ErpSqliteOrderLoader.class);
    when(loader.loadMaterialInventoryByCodes(List.of("A001.02.012.2005"), false))
      .thenReturn(List.of(Map.of("material_code", "A001.02.012.2005", "stock_qty", 321.0)));
    when(loader.loadMaterialInventoryByCodes(List.of("A001.02.012.2005"), true))
      .thenReturn(List.of(Map.of("material_code", "A001.02.012.2005", "stock_qty", 111.0)));

    ErpDataManager manager = new ErpDataManager(loader, true, 5_000L, true);
    Map<String, Map<String, Object>> cached = manager.getMaterialInventoryByCodes(List.of("A001.02.012.2005"), false);
    Map<String, Map<String, Object>> refreshed = manager.getMaterialInventoryByCodes(List.of("A001.02.012.2005"), true);
    Map<String, Map<String, Object>> afterRefresh = manager.getMaterialInventoryByCodes(List.of("A001.02.012.2005"), false);

    assertEquals(321.0, ((Number) cached.get("A001.02.012.2005").get("stock_qty")).doubleValue());
    assertEquals(111.0, ((Number) refreshed.get("A001.02.012.2005").get("stock_qty")).doubleValue());
    assertEquals(111.0, ((Number) afterRefresh.get("A001.02.012.2005").get("stock_qty")).doubleValue());
    verify(loader, times(1)).loadMaterialInventoryByCodes(List.of("A001.02.012.2005"), false);
    verify(loader, times(1)).loadMaterialInventoryByCodes(List.of("A001.02.012.2005"), true);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value) {
    return (Map<String, Object>) value;
  }

  private static long number(Object value) {
    return value instanceof Number number ? number.longValue() : 0L;
  }
}
