package com.autoproduction.mvp.core;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class MvpStoreScheduleVersionListSupport {
  private MvpStoreScheduleVersionListSupport() {}

  static List<Map<String, Object>> listScheduleVersions(MvpStoreScheduleLoadDomain store, Map<String, String> filters) {
    return store.state.schedules.stream()
      .sorted(Comparator.comparing(v -> v.versionNo))
      .map(version -> {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("version_no", version.versionNo);
        row.put("status", version.status);
        row.put("based_on_version", version.basedOnVersion);
        row.put("rule_version_no", version.ruleVersionNo);
        String strategyCode = store.normalizeScheduleStrategy(
          store.firstString(version.metadata, "schedule_strategy_code", "scheduleStrategyCode", "strategy_code", "strategyCode")
        );
        row.put("strategy_code", strategyCode);
        row.put("strategy_name_cn", store.scheduleStrategyNameCn(strategyCode));
        row.put("publish_time", version.publishTime == null ? null : version.publishTime.toString());
        row.put("created_by", version.createdBy);
        row.put("created_at", version.createdAt == null ? null : version.createdAt.toString());
        return store.localizeRow(row);
      })
      .filter(row -> filters == null || !filters.containsKey("status") || Objects.equals(filters.get("status"), row.get("status")))
      .toList();
  }
}

