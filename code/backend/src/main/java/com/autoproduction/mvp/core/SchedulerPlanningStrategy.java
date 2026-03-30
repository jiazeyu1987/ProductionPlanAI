package com.autoproduction.mvp.core;

import static com.autoproduction.mvp.core.SchedulerPlanningConstants.STRATEGY_MAX_CAPACITY_FIRST;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.STRATEGY_MIN_DELAY_FIRST;

final class SchedulerPlanningStrategy {
  private SchedulerPlanningStrategy() {}

  static String normalizeStrategyCode(String strategyCode) {
    return StrategyRuleRegistry.normalizeCode(strategyCode);
  }

  static String strategyNameCn(String strategyCode) {
    return StrategyRuleRegistry.nameCn(strategyCode);
  }

  static StrategyProfile resolveStrategyProfile(String strategyCode) {
    return switch (normalizeStrategyCode(strategyCode)) {
      case STRATEGY_MAX_CAPACITY_FIRST -> new StrategyProfile(
        0.65d,
        0.55d,
        0.75d,
        0.80d,
        0.70d,
        0.55d,
        0.70d,
        0.20d,
        0.04d
      );
      case STRATEGY_MIN_DELAY_FIRST -> new StrategyProfile(
        0.95d,
        1.65d,
        0.85d,
        1.00d,
        1.10d,
        0.90d,
        0.90d,
        0.06d,
        0.00d
      );
      default -> new StrategyProfile(
        1.20d,
        1.00d,
        1.00d,
        1.00d,
        1.00d,
        1.20d,
        1.00d,
        0.08d,
        0.00d
      );
    };
  }
}

