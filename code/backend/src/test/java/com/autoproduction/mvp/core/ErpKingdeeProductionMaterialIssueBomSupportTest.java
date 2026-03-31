package com.autoproduction.mvp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ErpKingdeeProductionMaterialIssueBomSupportTest {

  @Test
  void queryBomChildMaterialsFromEngBomViewComputesIssueQtyFromOrderQtyAndRatio() {
    ErpKingdeeBillQueryClient billQueryClient = mock(ErpKingdeeBillQueryClient.class);
    ErpKingdeePpBomViewSupport ppBomViewSupport = mock(ErpKingdeePpBomViewSupport.class);
    ErpKingdeeProductionMaterialIssueBomSupport support = new ErpKingdeeProductionMaterialIssueBomSupport(
      billQueryClient,
      new ErpOrderNormalizer(),
      new ErpSqliteOrderValidator("", "", "", "", "881"),
      ppBomViewSupport
    );

    Map<String, Object> child = Map.of(
      "Number", "A001.02.014.10071",
      "Name", List.of(Map.of("Value", "造影导管软端（加强显影）")),
      "Specification", List.of(Map.of("Value", "5F（1.17*1.68*1170mm）硬度55D 蓝 45%显影剂"))
    );
    Map<String, Object> entry = Map.of(
      "Seq", 1,
      "RowId", "row-1",
      "NUMERATOR", 80.0,
      "DENOMINATOR", 1000.0,
      "BaseNumerator", 80.0,
      "BaseDenominator", 1000.0,
      "ActualQty", 0.0,
      "Qty", 0.0,
      "MATERIALIDCHILD", child
    );
    Map<String, Object> viewResponse = Map.of(
      "Result", Map.of(
        "ResponseStatus", Map.of("IsSuccess", true),
        "Result", Map.of(
          "Id", 5489117,
          "TreeEntity", List.of(entry)
        )
      )
    );
    when(billQueryClient.viewBillFromApi("ENG_BOM", "YXN.067.005.1006_V1.0", null)).thenReturn(viewResponse);

    List<Map<String, Object>> rows = support.queryBomChildMaterialsFromEngBomView(
      "881MO091041",
      "YXN.067.005.1006_V1.0",
      154.0
    );

    assertEquals(1, rows.size());
    assertEquals("A001.02.014.10071", rows.get(0).get("child_material_code"));
    assertEquals(13.0, ((Number) rows.get(0).get("issue_qty")).doubleValue());
    assertEquals(13.0, ((Number) rows.get(0).get("required_qty")).doubleValue());
  }
}
