package com.autoproduction.mvp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MvpMaterialRowAssemblerTest {

  @Test
  void buildOrderMaterialRowShouldMapCoreFields() {
    Map<String, Object> raw = new HashMap<>();
    raw.put("child_material_name_cn", "导管料");
    raw.put("required_qty", 12.5d);
    raw.put("source_bill_no", "SRC-1");
    Map<String, Object> supply = Map.of("supply_type", "PURCHASE", "supply_type_name_cn", "采购");

    Map<String, Object> row =
        MvpMaterialRowAssembler.buildOrderMaterialRow("PO-1", "ML-1", "MAT-1", raw, supply);

    assertEquals("PO-1", row.get("order_no"));
    assertEquals("ML-1", row.get("material_list_no"));
    assertEquals("MAT-1", row.get("child_material_code"));
    assertEquals("导管料", row.get("child_material_name_cn"));
    assertEquals(12.5d, (double) row.get("required_qty"), 1e-9);
    assertEquals("PURCHASE", row.get("child_material_supply_type"));
    assertEquals("采购", row.get("child_material_supply_type_name_cn"));
  }

  @Test
  void buildParentMaterialChildRowShouldUseDefaults() {
    Map<String, Object> row =
        MvpMaterialRowAssembler.buildParentMaterialChildRow(
            "PPBOM-1", "MAT-2", Map.of(), Map.of());

    assertEquals("PPBOM-1", row.get("parent_material_code"));
    assertEquals("MAT-2", row.get("child_material_code"));
    assertEquals("", row.get("child_material_name_cn"));
    assertEquals(0d, (double) row.get("required_qty"), 1e-9);
    assertEquals("UNKNOWN", row.get("child_material_supply_type"));
    assertEquals("未知", row.get("child_material_supply_type_name_cn"));
  }
}
