package com.autoproduction.mvp.core;

import java.util.Map;

final class StrategyRuleRegistry {
  static final String KEY_ORDER_FIRST = "KEY_ORDER_FIRST";
  static final String MAX_CAPACITY_FIRST = "MAX_CAPACITY_FIRST";
  static final String MIN_DELAY_FIRST = "MIN_DELAY_FIRST";

  private static final Map<String, String> STRATEGY_NAME_CN = Map.ofEntries(
    Map.entry(KEY_ORDER_FIRST, "\u5173\u952e\u8ba2\u5355\u4f18\u5148"),
    Map.entry(MAX_CAPACITY_FIRST, "\u6700\u5927\u4ea7\u80fd\u4f18\u5148"),
    Map.entry(MIN_DELAY_FIRST, "\u4ea4\u671f\u6700\u5c0f\u5ef6\u671f\u4f18\u5148")
  );

  private StrategyRuleRegistry() {}

  static String normalizeCode(String strategyCode) {
    String normalized = strategyCode == null ? "" : strategyCode.trim().toUpperCase();
    return switch (normalized) {
      case "MAX_CAPACITY_FIRST", "\u6700\u5927\u4ea7\u80fd\u4f18\u5148" -> MAX_CAPACITY_FIRST;
      case "MIN_DELAY_FIRST", "MIN_TARDINESS_FIRST", "\u4ea4\u671f\u6700\u5c0f\u5ef6\u671f\u4f18\u5148" -> MIN_DELAY_FIRST;
      case "KEY_ORDER_FIRST", "CRITICAL_ORDER_FIRST", "\u5173\u952e\u8ba2\u5355\u4f18\u5148" -> KEY_ORDER_FIRST;
      default -> KEY_ORDER_FIRST;
    };
  }

  static String nameCn(String strategyCode) {
    String normalized = normalizeCode(strategyCode);
    return STRATEGY_NAME_CN.getOrDefault(normalized, STRATEGY_NAME_CN.get(KEY_ORDER_FIRST));
  }
}
