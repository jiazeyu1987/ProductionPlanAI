package com.autoproduction.mvp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StrategyRuleRegistryTest {

  @Test
  void normalizeCodeSupportsAliasesAndDefaults() {
    assertEquals(StrategyRuleRegistry.KEY_ORDER_FIRST, StrategyRuleRegistry.normalizeCode(null));
    assertEquals(StrategyRuleRegistry.KEY_ORDER_FIRST, StrategyRuleRegistry.normalizeCode("CRITICAL_ORDER_FIRST"));
    assertEquals(StrategyRuleRegistry.MAX_CAPACITY_FIRST, StrategyRuleRegistry.normalizeCode("最大产能优先"));
    assertEquals(StrategyRuleRegistry.MIN_DELAY_FIRST, StrategyRuleRegistry.normalizeCode("MIN_TARDINESS_FIRST"));
  }

  @Test
  void nameCnUsesNormalizedCodeAndDefault() {
    assertEquals("关键订单优先", StrategyRuleRegistry.nameCn("UNKNOWN"));
    assertEquals("最大产能优先", StrategyRuleRegistry.nameCn("MAX_CAPACITY_FIRST"));
    assertEquals("交期最小延期优先", StrategyRuleRegistry.nameCn("交期最小延期优先"));
  }
}
