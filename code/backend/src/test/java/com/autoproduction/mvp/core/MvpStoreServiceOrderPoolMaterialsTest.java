package com.autoproduction.mvp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autoproduction.mvp.module.integration.erp.ErpDataManager;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MvpStoreServiceOrderPoolMaterialsTest {

  @Test
  void listOrderPoolMaterialsFiltersProductAndUnknownCodes() {
    ErpDataManager erpDataManager = Mockito.mock(ErpDataManager.class);
    when(erpDataManager.getProductionOrders())
      .thenReturn(List.of(Map.of(
        "production_order_no", "881MO090955",
        "material_list_no", "YXN.009.020.1047_V1.3",
        "product_code", "YXN.009.020.1047"
      )));

    when(erpDataManager.getProductionMaterialIssuesByOrder("881MO090955", null, false))
      .thenReturn(List.of(
        Map.of(
          "child_material_code", "YXN.009.020.1047",
          "required_qty", 1.0,
          "source_bill_no", "YXN.009.020.1047_V1.3",
          "source_bill_type", "PRD_PPBOM",
          "erp_source_table", "ERP_API_BOM_VIEW_ENTRY"
        ),
        Map.of(
          "child_material_code", "UNKNOWN",
          "required_qty", 2.0,
          "source_bill_no", "YXN.009.020.1047_V1.3",
          "source_bill_type", "PRD_PPBOM",
          "erp_source_table", "ERP_API_BOM_VIEW_ENTRY"
        ),
        Map.of(
          "child_material_code", "A002.02.003.232029",
          "child_material_name_cn", "Liner Plate",
          "required_qty", 496.0,
          "source_bill_no", "YXN.009.020.1047_V1.3",
          "source_bill_type", "PRD_PPBOM",
          "erp_source_table", "ERP_API_BOM_VIEW_ENTRY"
        ),
        Map.of(
          "child_material_code", "A002.02.003.232029",
          "child_material_name_cn", "Liner Plate",
          "required_qty", 999.0,
          "source_bill_no", "YXN.009.020.1047_V1.3",
          "source_bill_type", "PRD_PPBOM",
          "erp_source_table", "ERP_API_BOM_VIEW_ENTRY"
        )
      ));
    when(erpDataManager.getMaterialSupplyInfo("A002.02.003.232029"))
      .thenReturn(Map.of("supply_type", "PURCHASED", "supply_type_name_cn", "Purchased"));

    MvpStoreService service = MvpStoreServiceTestFactory.create(erpDataManager);

    List<Map<String, Object>> rows = service.listOrderPoolMaterials("881MO090955");

    assertEquals(1, rows.size());
    assertEquals("A002.02.003.232029", rows.get(0).get("child_material_code"));
    assertEquals(496.0, ((Number) rows.get(0).get("issue_qty")).doubleValue());
    assertEquals(496.0, ((Number) rows.get(0).get("required_qty")).doubleValue());
    assertEquals("PURCHASED", rows.get(0).get("child_material_supply_type"));
    verify(erpDataManager).getProductionMaterialIssuesByOrder("881MO090955", null, false);
  }

  @Test
  void listOrderPoolMaterialsFallsBackToPpBomEntriesWhenAvailable() {
    ErpDataManager erpDataManager = Mockito.mock(ErpDataManager.class);
    when(erpDataManager.getProductionOrders())
      .thenReturn(List.of(Map.of(
        "production_order_no", "881MO090955",
        "material_list_no", "YXN.009.020.1047_V1.3",
        "product_code", "YXN.009.020.1047"
      )));

    when(erpDataManager.getProductionMaterialIssuesByOrder("881MO090955", null, false))
      .thenReturn(List.of(
        Map.of(
          "child_material_code", "YXN.009.020.1047",
          "pick_material_bill_no", "PPBOM00308548",
          "required_qty", 1.0
        )
      ));

    when(erpDataManager.getProductionMaterialIssuesByOrder(null, "PPBOM00308548", false))
      .thenReturn(List.of(
        Map.of(
          "child_material_code", "A003.017.01.004",
          "child_material_name_cn", "\u5F2F\u66F2\u8FDE\u63A5\u4EF6",
          "spec_model", "(83819) \u672C\u8272",
          "required_qty", 496.0,
          "erp_source_table", "ERP_API_BOM_VIEW_ENTRY"
        ),
        Map.of(
          "child_material_code", "A004.002.09.200",
          "child_material_name_cn", "\u5BFC\u7BA1\u76D8\u7BA1 PTCA",
          "spec_model", "4.4*5.72*1520mm",
          "required_qty", 496.0,
          "erp_source_table", "ERP_API_BOM_VIEW_ENTRY"
        )
      ));
    when(erpDataManager.getMaterialSupplyInfo("A004.002.09.200"))
      .thenReturn(Map.of("supply_type", "SELF_MADE", "supply_type_name_cn", "Self-made"));
    when(erpDataManager.getMaterialSupplyInfo("A003.017.01.004"))
      .thenReturn(Map.of("supply_type", "PURCHASED", "supply_type_name_cn", "Purchased"));

    MvpStoreService service = MvpStoreServiceTestFactory.create(erpDataManager);

    List<Map<String, Object>> rows = service.listOrderPoolMaterials("881MO090955");

    assertEquals(2, rows.size());
    assertEquals("A003.017.01.004", rows.get(0).get("child_material_code"));
    assertEquals("\u5F2F\u66F2\u8FDE\u63A5\u4EF6", rows.get(0).get("child_material_name_cn"));
    assertEquals("(83819) \u672C\u8272", rows.get(0).get("spec_model"));
    assertEquals("A004.002.09.200", rows.get(1).get("child_material_code"));
    assertEquals("\u5BFC\u7BA1\u76D8\u7BA1 PTCA", rows.get(1).get("child_material_name_cn"));
    assertEquals("4.4*5.72*1520mm", rows.get(1).get("spec_model"));
    assertTrue(rows.stream().noneMatch(row -> "YXN.009.020.1047".equals(row.get("child_material_code"))));
    verify(erpDataManager).getProductionMaterialIssuesByOrder(null, "PPBOM00308548", false);
    verify(erpDataManager).getMaterialSupplyInfo("A004.002.09.200");
    verify(erpDataManager).getMaterialSupplyInfo("A003.017.01.004");
  }

  @Test
  void listOrderPoolMaterialsUsesCacheByDefaultAndRefreshesWhenRequested() {
    ErpDataManager erpDataManager = Mockito.mock(ErpDataManager.class);
    when(erpDataManager.getProductionOrders())
      .thenReturn(List.of(Map.of(
        "production_order_no", "881MO090955",
        "material_list_no", "YXN.009.020.1047_V1.3",
        "product_code", "YXN.009.020.1047"
      )));

    when(erpDataManager.getProductionMaterialIssuesByOrder("881MO090955", null, false))
      .thenReturn(List.of(
        Map.of(
          "child_material_code", "A001.02.012.2005",
          "child_material_name_cn", "Embryo Tube",
          "required_qty", 496.0,
          "erp_source_table", "ERP_API_BOM_VIEW_ENTRY"
        )
      ));
    when(erpDataManager.getMaterialSupplyInfo("A001.02.012.2005"))
      .thenReturn(Map.of("supply_type", "SELF_MADE", "supply_type_name_cn", "Self-made"));

    when(erpDataManager.getProductionMaterialIssuesByOrder("881MO090955", null, true))
      .thenReturn(List.of(
        Map.of(
          "child_material_code", "A002.02.003.232029",
          "child_material_name_cn", "Liner Plate",
          "required_qty", 496.0,
          "erp_source_table", "ERP_API_BOM_VIEW_ENTRY"
        )
      ));
    when(erpDataManager.getMaterialSupplyInfo("A002.02.003.232029"))
      .thenReturn(Map.of("supply_type", "PURCHASED", "supply_type_name_cn", "Purchased"));

    MvpStoreService service = MvpStoreServiceTestFactory.create(erpDataManager);

    List<Map<String, Object>> first = service.listOrderPoolMaterials("881MO090955");
    List<Map<String, Object>> second = service.listOrderPoolMaterials("881MO090955");
    List<Map<String, Object>> refreshed = service.listOrderPoolMaterials("881MO090955", true);
    List<Map<String, Object>> third = service.listOrderPoolMaterials("881MO090955");

    assertEquals("A001.02.012.2005", first.get(0).get("child_material_code"));
    assertEquals("A001.02.012.2005", second.get(0).get("child_material_code"));
    assertEquals("A002.02.003.232029", refreshed.get(0).get("child_material_code"));
    assertEquals("A002.02.003.232029", third.get(0).get("child_material_code"));
    verify(erpDataManager, times(1))
      .getProductionMaterialIssuesByOrder("881MO090955", null, false);
    verify(erpDataManager, times(1))
      .getProductionMaterialIssuesByOrder("881MO090955", null, true);
  }

  @Test
  void listOrderPoolMaterialsFallsBackToRequiredQtyWhenIssueQtyMissing() {
    ErpDataManager erpDataManager = Mockito.mock(ErpDataManager.class);
    when(erpDataManager.getProductionOrders())
      .thenReturn(List.of(Map.of(
        "production_order_no", "881MO090955",
        "material_list_no", "YXN.009.020.1047_V1.3",
        "product_code", "YXN.009.020.1047"
      )));
    when(erpDataManager.getProductionMaterialIssuesByOrder("881MO090955", null, false))
      .thenReturn(List.of(
        Map.of(
          "child_material_code", "A001.02.012.2005",
          "child_material_name_cn", "Embryo Tube",
          "required_qty", 496.0,
          "erp_source_table", "ERP_API_BOM_VIEW_ENTRY"
        )
      ));
    when(erpDataManager.getMaterialSupplyInfo("A001.02.012.2005"))
      .thenReturn(Map.of("supply_type", "SELF_MADE", "supply_type_name_cn", "Self-made"));

    MvpStoreService service = MvpStoreServiceTestFactory.create(erpDataManager);

    List<Map<String, Object>> rows = service.listOrderPoolMaterials("881MO090955");

    assertEquals(1, rows.size());
    assertEquals(496.0, ((Number) rows.get(0).get("issue_qty")).doubleValue());
    assertEquals(496.0, ((Number) rows.get(0).get("required_qty")).doubleValue());
  }

  @Test
  void listOrderPoolMaterialsPrefersMoreCompleteDuplicateRows() {
    ErpDataManager erpDataManager = Mockito.mock(ErpDataManager.class);
    when(erpDataManager.getProductionOrders())
      .thenReturn(List.of(Map.of(
        "production_order_no", "881MO090955",
        "material_list_no", "YXN.009.020.1047_V1.3",
        "product_code", "YXN.009.020.1047"
      )));

    when(erpDataManager.getProductionMaterialIssuesByOrder("881MO090955", null, false))
      .thenReturn(List.of(
        Map.of(
          "child_material_code", "A003.017.01.004",
          "child_material_name_cn", "",
          "spec_model", "",
          "required_qty", 496.0,
          "erp_source_table", "ERP_API_PRODUCTION_MATERIAL_ISSUE"
        ),
        Map.of(
          "child_material_code", "A003.017.01.004",
          "child_material_name_cn", "弯曲连接件",
          "spec_model", "(83819) 本色",
          "required_qty", 496.0,
          "erp_source_table", "ERP_API_BOM_VIEW_ENTRY"
        )
      ));
    when(erpDataManager.getMaterialSupplyInfo("A003.017.01.004"))
      .thenReturn(Map.of("supply_type", "PURCHASED", "supply_type_name_cn", "Purchased"));

    MvpStoreService service = MvpStoreServiceTestFactory.create(erpDataManager);

    List<Map<String, Object>> rows = service.listOrderPoolMaterials("881MO090955");

    assertEquals(1, rows.size());
    assertEquals("弯曲连接件", rows.get(0).get("child_material_name_cn"));
    assertEquals("(83819) 本色", rows.get(0).get("spec_model"));
  }

  @Test
  void listOrderPoolMaterialsFallsBackToLiveProductionOrdersWhenSnapshotMisses() {
    ErpDataManager erpDataManager = Mockito.mock(ErpDataManager.class);
    when(erpDataManager.getProductionOrders()).thenReturn(List.of());
    when(erpDataManager.loadProductionOrdersLive())
      .thenReturn(List.of(Map.of(
        "production_order_no", "881MO091048",
        "material_list_no", "YXN.009.020.1048_V1.0",
        "product_code", "YXN.009.020.1048"
      )));
    when(erpDataManager.getProductionMaterialIssuesByOrder("881MO091048", null, false)).thenReturn(List.of());
    when(erpDataManager.getProductionMaterialIssuesByOrder("881MO091048", "YXN.009.020.1048_V1.0", false))
      .thenReturn(List.of(
        Map.of(
          "child_material_code", "A002.02.003.232029",
          "child_material_name_cn", "Liner Plate",
          "required_qty", 27.0,
          "erp_source_table", "ERP_API_BOM_VIEW_ENTRY"
        )
      ));
    when(erpDataManager.getMaterialSupplyInfo("A002.02.003.232029"))
      .thenReturn(Map.of("supply_type", "PURCHASED", "supply_type_name_cn", "Purchased"));

    MvpStoreService service = MvpStoreServiceTestFactory.create(erpDataManager);

    List<Map<String, Object>> rows = service.listOrderPoolMaterials("881MO091048");

    assertEquals(1, rows.size());
    assertEquals("A002.02.003.232029", rows.get(0).get("child_material_code"));
    verify(erpDataManager).loadProductionOrdersLive();
    verify(erpDataManager).getProductionMaterialIssuesByOrder("881MO091048", null, false);
    verify(erpDataManager).getProductionMaterialIssuesByOrder("881MO091048", "YXN.009.020.1048_V1.0", false);
  }

  @Test
  void listOrderPoolMaterialsDoesNotCacheEmptyResultWhenOrderResolutionMisses() {
    ErpDataManager erpDataManager = Mockito.mock(ErpDataManager.class);
    when(erpDataManager.getProductionOrders()).thenReturn(List.of());
    when(erpDataManager.loadProductionOrdersLive()).thenReturn(List.of());
    when(erpDataManager.getProductionMaterialIssuesByOrder("NOT-FOUND-001", null, false)).thenReturn(List.of());

    MvpStoreService service = MvpStoreServiceTestFactory.create(erpDataManager);

    List<Map<String, Object>> first = service.listOrderPoolMaterials("NOT-FOUND-001");
    List<Map<String, Object>> second = service.listOrderPoolMaterials("NOT-FOUND-001");

    assertTrue(first.isEmpty());
    assertTrue(second.isEmpty());
    verify(erpDataManager, times(2)).loadProductionOrdersLive();
    verify(erpDataManager, times(2)).getProductionMaterialIssuesByOrder("NOT-FOUND-001", null, false);
  }

  @Test
  void listOrderPoolMaterialsCanReturnRowsEvenWhenProductionOrderRowResolutionMisses() {
    ErpDataManager erpDataManager = Mockito.mock(ErpDataManager.class);
    when(erpDataManager.getProductionOrders()).thenReturn(List.of());
    when(erpDataManager.loadProductionOrdersLive()).thenReturn(List.of());
    when(erpDataManager.getProductionMaterialIssuesByOrder("881MO091048", null, false))
      .thenReturn(List.of(
        Map.of(
          "child_material_code", "A001.02.011.1009",
          "child_material_name_cn", "Heat Shrink Tube",
          "spec_model", "0.79mm*0.59mm*1800mm (FEP)",
          "required_qty", 27.0,
          "erp_source_table", "ERP_API_BOM_VIEW_ENTRY"
        )
      ));
    when(erpDataManager.getMaterialSupplyInfo("A001.02.011.1009"))
      .thenReturn(Map.of("supply_type", "PURCHASED", "supply_type_name_cn", "Purchased"));

    MvpStoreService service = MvpStoreServiceTestFactory.create(erpDataManager);

    List<Map<String, Object>> rows = service.listOrderPoolMaterials("881MO091048");

    assertEquals(1, rows.size());
    assertEquals("A001.02.011.1009", rows.get(0).get("child_material_code"));
    assertEquals("0.79mm*0.59mm*1800mm (FEP)", rows.get(0).get("spec_model"));
    verify(erpDataManager, times(1)).getProductionMaterialIssuesByOrder("881MO091048", null, false);
  }

  @Test
  void listOrderPoolMaterialsReturnsRowsWhenMaterialSupplyLookupFails() {
    ErpDataManager erpDataManager = Mockito.mock(ErpDataManager.class);
    when(erpDataManager.getProductionOrders()).thenReturn(List.of());
    when(erpDataManager.loadProductionOrdersLive()).thenReturn(List.of());
    when(erpDataManager.getProductionMaterialIssuesByOrder("881MO091048", null, false))
      .thenReturn(List.of(
        Map.of(
          "child_material_code", "A001.02.011.1009",
          "child_material_name_cn", "Heat Shrink Tube",
          "spec_model", "0.79mm*0.59mm*1800mm (FEP)",
          "required_qty", 27.0,
          "erp_source_table", "ERP_API_BOM_VIEW_ENTRY"
        )
      ));
    when(erpDataManager.getMaterialSupplyInfo("A001.02.011.1009"))
      .thenThrow(new RuntimeException("ERP query response is not JSON."));

    MvpStoreService service = MvpStoreServiceTestFactory.create(erpDataManager);

    List<Map<String, Object>> rows = service.listOrderPoolMaterials("881MO091048");

    assertEquals(1, rows.size());
    assertEquals("A001.02.011.1009", rows.get(0).get("child_material_code"));
    assertEquals("UNKNOWN", rows.get(0).get("child_material_supply_type"));
    assertEquals("\u672A\u77E5", rows.get(0).get("child_material_supply_type_name_cn"));
  }

  @Test
  void listMaterialChildrenByParentCodeFiltersSelfAndUnknownCodes() {
    ErpDataManager erpDataManager = Mockito.mock(ErpDataManager.class);
    when(erpDataManager.getProductionMaterialIssuesByOrder(null, "A003.017.11.002.1003", false))
      .thenReturn(List.of(
        Map.of(
          "child_material_code", "A003.017.11.002.1003",
          "required_qty", 1.0
        ),
        Map.of(
          "child_material_code", "UNKNOWN",
          "required_qty", 2.0
        ),
        Map.of(
          "child_material_code", "A001.02.012.2005",
          "child_material_name_cn", "Embryo Tube",
          "required_qty", 496.0,
          "source_bill_no", "PPBOM00308548"
        ),
        Map.of(
          "child_material_code", "A001.02.012.2005",
          "child_material_name_cn", "Embryo Tube",
          "required_qty", 999.0,
          "source_bill_no", "PPBOM00308548"
        )
      ));
    when(erpDataManager.getMaterialSupplyInfo("A001.02.012.2005"))
      .thenReturn(Map.of("supply_type", "SELF_MADE", "supply_type_name_cn", "Self-made"));

    MvpStoreService service = MvpStoreServiceTestFactory.create(erpDataManager);

    List<Map<String, Object>> rows = service.listMaterialChildrenByParentCode("A003.017.11.002.1003");

    assertEquals(1, rows.size());
    assertEquals("A001.02.012.2005", rows.get(0).get("child_material_code"));
    assertEquals("A003.017.11.002.1003", rows.get(0).get("parent_material_code"));
    assertEquals("SELF_MADE", rows.get(0).get("child_material_supply_type"));
  }

  @Test
  void listMaterialChildrenByParentCodeUsesCacheByDefaultAndRefreshesWhenRequested() {
    ErpDataManager erpDataManager = Mockito.mock(ErpDataManager.class);
    when(erpDataManager.getProductionMaterialIssuesByOrder(null, "A003.017.11.002.1003", false))
      .thenReturn(List.of(
        Map.of(
          "child_material_code", "A001.02.012.2005",
          "child_material_name_cn", "Embryo Tube",
          "required_qty", 496.0
        )
      ));
    when(erpDataManager.getProductionMaterialIssuesByOrder(null, "A003.017.11.002.1003", true))
      .thenReturn(List.of(
        Map.of(
          "child_material_code", "A003.017.11.003.2002",
          "child_material_name_cn", "Catheter Base",
          "required_qty", 496.0
        )
      ));
    when(erpDataManager.getMaterialSupplyInfo("A001.02.012.2005"))
      .thenReturn(Map.of("supply_type", "SELF_MADE", "supply_type_name_cn", "Self-made"));
    when(erpDataManager.getMaterialSupplyInfo("A003.017.11.003.2002"))
      .thenReturn(Map.of("supply_type", "SELF_MADE", "supply_type_name_cn", "Self-made"));

    MvpStoreService service = MvpStoreServiceTestFactory.create(erpDataManager);

    List<Map<String, Object>> first = service.listMaterialChildrenByParentCode("A003.017.11.002.1003");
    List<Map<String, Object>> second = service.listMaterialChildrenByParentCode("A003.017.11.002.1003");
    List<Map<String, Object>> refreshed = service.listMaterialChildrenByParentCode("A003.017.11.002.1003", true);
    List<Map<String, Object>> third = service.listMaterialChildrenByParentCode("A003.017.11.002.1003");

    assertEquals("A001.02.012.2005", first.get(0).get("child_material_code"));
    assertEquals("A001.02.012.2005", second.get(0).get("child_material_code"));
    assertEquals("A003.017.11.003.2002", refreshed.get(0).get("child_material_code"));
    assertEquals("A003.017.11.003.2002", third.get(0).get("child_material_code"));
    verify(erpDataManager, times(1)).getProductionMaterialIssuesByOrder(null, "A003.017.11.002.1003", false);
    verify(erpDataManager, times(1)).getProductionMaterialIssuesByOrder(null, "A003.017.11.002.1003", true);
  }

  @Test
  void listMaterialChildrenByParentCodePrefersMoreCompleteDuplicateRows() {
    ErpDataManager erpDataManager = Mockito.mock(ErpDataManager.class);
    when(erpDataManager.getProductionMaterialIssuesByOrder(null, "A003.017.11.002.1003", false))
      .thenReturn(List.of(
        Map.of(
          "child_material_code", "A001.02.012.2005",
          "child_material_name_cn", "",
          "spec_model", "",
          "required_qty", 496.0,
          "erp_source_table", "ERP_API_PRODUCTION_MATERIAL_ISSUE"
        ),
        Map.of(
          "child_material_code", "A001.02.012.2005",
          "child_material_name_cn", "Embryo Tube",
          "spec_model", "SPEC-001",
          "required_qty", 496.0,
          "erp_source_table", "ERP_API_BOM_VIEW_ENTRY"
        )
      ));
    when(erpDataManager.getMaterialSupplyInfo("A001.02.012.2005"))
      .thenReturn(Map.of("supply_type", "SELF_MADE", "supply_type_name_cn", "Self-made"));

    MvpStoreService service = MvpStoreServiceTestFactory.create(erpDataManager);

    List<Map<String, Object>> rows = service.listMaterialChildrenByParentCode("A003.017.11.002.1003");

    assertEquals(1, rows.size());
    assertEquals("Embryo Tube", rows.get(0).get("child_material_name_cn"));
    assertEquals("SPEC-001", rows.get(0).get("spec_model"));
  }

  @Test
  void listOrderMaterialAvailabilityBuildsDemandAndStockFromActiveOrderChildren() {
    ErpDataManager erpDataManager = Mockito.mock(ErpDataManager.class);
    when(erpDataManager.getProductionOrders())
      .thenReturn(List.of(Map.of(
        "production_order_no", "MO-ERP-001",
        "material_list_no", "PPBOM001",
        "product_code", "YXN.009.020.1047"
      )));
    when(erpDataManager.getProductionMaterialIssuesByOrder("MO-ERP-001", null, false))
      .thenReturn(List.of(
        Map.of(
          "child_material_code", "A001.02.012.2005",
          "child_material_name_cn", "Embryo Tube",
          "required_qty", 100.0,
          "erp_source_table", "ERP_API_BOM_VIEW_ENTRY"
        )
      ));
    when(erpDataManager.getMaterialSupplyInfo("A001.02.012.2005"))
      .thenReturn(Map.of("supply_type", "PURCHASED", "supply_type_name_cn", "Purchased"));
    when(erpDataManager.getMaterialInventoryByCodes(List.of("A001.02.012.2005"), false))
      .thenReturn(Map.of(
        "A001.02.012.2005",
        Map.of(
          "material_code", "A001.02.012.2005",
          "stock_qty", 50.0,
          "inventory_missing_flag", 0
        )
      ));

    MvpStoreService service = MvpStoreServiceTestFactory.create(erpDataManager);
    service.upsertOrder(
      Map.of(
        "order_no", "MO-ERP-001",
        "items", List.of(Map.of(
          "product_code", "YXN.009.020.1047",
          "qty", 200.0,
          "completed_qty", 0.0
        )),
        "status", "OPEN"
      ),
      "req-material-availability-upsert",
      "tester"
    );

    List<Map<String, Object>> rows = service.listOrderMaterialAvailability(false);
    Map<String, Object> row = rows.stream()
      .filter(item -> "MO-ERP-001".equals(item.get("order_no")))
      .findFirst()
      .orElseThrow();

    assertEquals("A001.02.012.2005", row.get("child_material_code"));
    assertEquals(100.0, ((Number) row.get("demand_qty")).doubleValue());
    assertEquals(50.0, ((Number) row.get("stock_qty")).doubleValue());
    assertEquals(50.0, ((Number) row.get("shortage_qty")).doubleValue());
    assertEquals(100.0, ((Number) row.get("order_max_schedulable_qty")).doubleValue());
    assertEquals(1, ((Number) row.get("schedulable_flag")).intValue());
    assertEquals("OK", row.get("data_status"));
  }
}
