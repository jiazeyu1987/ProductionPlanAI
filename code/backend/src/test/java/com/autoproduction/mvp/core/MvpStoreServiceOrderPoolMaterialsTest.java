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

    when(erpDataManager.getProductionMaterialIssuesByOrder("881MO090955", "YXN.009.020.1047_V1.3", false))
      .thenReturn(List.of(
        Map.of(
          "child_material_code", "YXN.009.020.1047",
          "required_qty", 1.0,
          "source_bill_no", "YXN.009.020.1047_V1.3",
          "source_bill_type", "PRD_PPBOM"
        ),
        Map.of(
          "child_material_code", "UNKNOWN",
          "required_qty", 2.0,
          "source_bill_no", "YXN.009.020.1047_V1.3",
          "source_bill_type", "PRD_PPBOM"
        ),
        Map.of(
          "child_material_code", "A002.02.003.232029",
          "child_material_name_cn", "Liner Plate",
          "required_qty", 496.0,
          "source_bill_no", "YXN.009.020.1047_V1.3",
          "source_bill_type", "PRD_PPBOM"
        ),
        Map.of(
          "child_material_code", "A002.02.003.232029",
          "child_material_name_cn", "Liner Plate",
          "required_qty", 999.0,
          "source_bill_no", "YXN.009.020.1047_V1.3",
          "source_bill_type", "PRD_PPBOM"
        )
      ));
    when(erpDataManager.getMaterialSupplyInfo("A002.02.003.232029"))
      .thenReturn(Map.of("supply_type", "PURCHASED", "supply_type_name_cn", "Purchased"));

    MvpStoreService service = new MvpStoreService(erpDataManager);

    List<Map<String, Object>> rows = service.listOrderPoolMaterials("881MO090955");

    assertEquals(1, rows.size());
    assertEquals("A002.02.003.232029", rows.get(0).get("child_material_code"));
    assertEquals(496.0, ((Number) rows.get(0).get("required_qty")).doubleValue());
    assertEquals("PURCHASED", rows.get(0).get("child_material_supply_type"));
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

    when(erpDataManager.getProductionMaterialIssuesByOrder("881MO090955", "YXN.009.020.1047_V1.3", false))
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
          "child_material_code", "A003.017.11.002.1003",
          "child_material_name_cn", "Handle Sheath",
          "required_qty", 496.0,
          "erp_source_table", "ERP_API_BOM_VIEW_ENTRY"
        ),
        Map.of(
          "child_material_code", "A001.02.012.2005",
          "child_material_name_cn", "Embryo Tube",
          "required_qty", 496.0,
          "erp_source_table", "ERP_API_BOM_VIEW_ENTRY"
        )
      ));
    when(erpDataManager.getMaterialSupplyInfo("A001.02.012.2005"))
      .thenReturn(Map.of("supply_type", "SELF_MADE", "supply_type_name_cn", "Self-made"));
    when(erpDataManager.getMaterialSupplyInfo("A003.017.11.002.1003"))
      .thenReturn(Map.of("supply_type", "PURCHASED", "supply_type_name_cn", "Purchased"));

    MvpStoreService service = new MvpStoreService(erpDataManager);

    List<Map<String, Object>> rows = service.listOrderPoolMaterials("881MO090955");

    assertEquals(2, rows.size());
    assertEquals("A001.02.012.2005", rows.get(0).get("child_material_code"));
    assertEquals("A003.017.11.002.1003", rows.get(1).get("child_material_code"));
    assertTrue(rows.stream().noneMatch(row -> "YXN.009.020.1047".equals(row.get("child_material_code"))));
    verify(erpDataManager).getProductionMaterialIssuesByOrder(null, "PPBOM00308548", false);
    verify(erpDataManager).getMaterialSupplyInfo("A001.02.012.2005");
    verify(erpDataManager).getMaterialSupplyInfo("A003.017.11.002.1003");
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

    when(erpDataManager.getProductionMaterialIssuesByOrder("881MO090955", "YXN.009.020.1047_V1.3", false))
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

    when(erpDataManager.getProductionMaterialIssuesByOrder("881MO090955", "YXN.009.020.1047_V1.3", true))
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

    MvpStoreService service = new MvpStoreService(erpDataManager);

    List<Map<String, Object>> first = service.listOrderPoolMaterials("881MO090955");
    List<Map<String, Object>> second = service.listOrderPoolMaterials("881MO090955");
    List<Map<String, Object>> refreshed = service.listOrderPoolMaterials("881MO090955", true);
    List<Map<String, Object>> third = service.listOrderPoolMaterials("881MO090955");

    assertEquals("A001.02.012.2005", first.get(0).get("child_material_code"));
    assertEquals("A001.02.012.2005", second.get(0).get("child_material_code"));
    assertEquals("A002.02.003.232029", refreshed.get(0).get("child_material_code"));
    assertEquals("A002.02.003.232029", third.get(0).get("child_material_code"));
    verify(erpDataManager, times(1))
      .getProductionMaterialIssuesByOrder("881MO090955", "YXN.009.020.1047_V1.3", false);
    verify(erpDataManager, times(1))
      .getProductionMaterialIssuesByOrder("881MO090955", "YXN.009.020.1047_V1.3", true);
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

    MvpStoreService service = new MvpStoreService(erpDataManager);

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

    MvpStoreService service = new MvpStoreService(erpDataManager);

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
  void listOrderMaterialAvailabilityBuildsDemandAndStockFromActiveOrderChildren() {
    ErpDataManager erpDataManager = Mockito.mock(ErpDataManager.class);
    when(erpDataManager.getProductionOrders())
      .thenReturn(List.of(Map.of(
        "production_order_no", "MO-ERP-001",
        "material_list_no", "PPBOM001",
        "product_code", "YXN.009.020.1047"
      )));
    when(erpDataManager.getProductionMaterialIssuesByOrder("MO-ERP-001", "PPBOM001", false))
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

    MvpStoreService service = new MvpStoreService(erpDataManager);
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
