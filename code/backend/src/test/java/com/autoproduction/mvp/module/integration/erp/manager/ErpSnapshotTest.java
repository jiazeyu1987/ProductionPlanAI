package com.autoproduction.mvp.module.integration.erp.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ErpSnapshotTest {

  @Test
  void emptySnapshotHasZeroCounts() {
    ErpSnapshot snapshot = ErpSnapshot.empty();
    Map<String, Object> counts = snapshot.toCountMap();

    assertEquals(0, counts.get("sales_order_lines"));
    assertEquals(0, counts.get("production_orders"));
    assertEquals(0, counts.get("purchase_orders"));
    assertEquals(0, counts.get("sales_order_headers_raw"));
    assertEquals(0, counts.get("sales_order_lines_raw"));
    assertEquals(0, counts.get("production_order_headers_raw"));
    assertEquals(0, counts.get("production_order_lines_raw"));
  }

  @Test
  void snapshotCountMapReflectsEachCollectionSize() {
    ErpSnapshot snapshot = new ErpSnapshot(
      List.of(Map.of("id", 1), Map.of("id", 2)),
      List.of(Map.of("id", 1)),
      List.of(Map.of("id", 1), Map.of("id", 2), Map.of("id", 3)),
      List.of(Map.of("id", 1)),
      List.of(Map.of("id", 1), Map.of("id", 2)),
      List.of(),
      List.of(Map.of("id", 1))
    );
    Map<String, Object> counts = snapshot.toCountMap();

    assertEquals(2, counts.get("sales_order_lines"));
    assertEquals(1, counts.get("production_orders"));
    assertEquals(3, counts.get("purchase_orders"));
    assertEquals(1, counts.get("sales_order_headers_raw"));
    assertEquals(2, counts.get("sales_order_lines_raw"));
    assertEquals(0, counts.get("production_order_headers_raw"));
    assertEquals(1, counts.get("production_order_lines_raw"));
  }
}

