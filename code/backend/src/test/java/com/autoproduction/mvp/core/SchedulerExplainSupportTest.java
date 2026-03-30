package com.autoproduction.mvp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SchedulerExplainSupportTest {

  @Test
  void reasonCompatibilityShouldContainLegacyCapacityCode() {
    List<String> reasons = SchedulerExplainSupport.reasonCodesForCompatibility("CAPACITY_MACHINE");
    assertTrue(reasons.contains("CAPACITY_MACHINE"));
    assertTrue(reasons.contains("CAPACITY_LIMIT"));
  }

  @Test
  void resolveTaskStatusShouldRespectFrozenAndLockedFlags() {
    assertEquals(
        "SKIPPED_FROZEN",
        SchedulerExplainSupport.resolveTaskStatus(true, false, 0d, 10d, 1e-6));
    assertEquals(
        "PRESERVED_LOCKED",
        SchedulerExplainSupport.resolveTaskStatus(false, true, 0d, 10d, 1e-6));
    assertEquals(
        "PARTIALLY_ALLOCATED",
        SchedulerExplainSupport.resolveTaskStatus(false, false, 1d, 10d, 1e-6));
    assertEquals(
        "READY",
        SchedulerExplainSupport.resolveTaskStatus(false, false, 0d, 0d, 1e-6));
  }

  @Test
  void buildReasonDistributionShouldIgnoreBlankReasonCode() {
    Map<String, Integer> dist =
        SchedulerExplainSupport.buildReasonDistribution(
            List.of(
                Map.of("reason_code", "CAPACITY_MACHINE"),
                Map.of("reason_code", "CAPACITY_MACHINE"),
                Map.of("reason_code", "MATERIAL_SHORTAGE"),
                Map.of("reason_code", "")));
    assertEquals(2, dist.get("CAPACITY_MACHINE"));
    assertEquals(1, dist.get("MATERIAL_SHORTAGE"));
  }
}
