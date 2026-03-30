package com.autoproduction.mvp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MvpAuditLogSupportTest {

  @Test
  void createAuditRowAppliesDefaultsAndIncludesPerfContext() {
    Map<String, Object> row = MvpAuditLogSupport.createAuditRow(
      "ORDER",
      "MO-001",
      "CREATE_ORDER",
      "创建订单",
      null,
      "req-1",
      "because",
      Map.of("elapsed_ms", 12)
    );

    assertEquals("ORDER", row.get("entity_type"));
    assertEquals("MO-001", row.get("entity_id"));
    assertEquals("CREATE_ORDER", row.get("action"));
    assertEquals("创建订单", row.get("action_name_cn"));
    assertEquals("system", row.get("operator"));
    assertEquals("req-1", row.get("request_id"));
    assertEquals("because", row.get("reason"));
    assertNotNull(row.get("operate_time"));
    assertEquals(12, ((Number) ((Map<?, ?>) row.get("perf_context")).get("elapsed_ms")).intValue());
  }

  @Test
  void matchesFiltersHonorsEntityTypeAndRequestId() {
    Map<String, Object> row = Map.of(
      "entity_type", "ORDER",
      "request_id", "req-2"
    );

    assertTrue(MvpAuditLogSupport.matchesFilters(row, null));
    assertTrue(MvpAuditLogSupport.matchesFilters(row, Map.of("entity_type", "ORDER")));
    assertTrue(MvpAuditLogSupport.matchesFilters(row, Map.of("request_id", "req-2")));
    assertTrue(MvpAuditLogSupport.matchesFilters(row, Map.of("entity_type", "ORDER", "request_id", "req-2")));
    assertFalse(MvpAuditLogSupport.matchesFilters(row, Map.of("entity_type", "REPORTING")));
    assertFalse(MvpAuditLogSupport.matchesFilters(row, Map.of("request_id", "req-x")));
  }
}
