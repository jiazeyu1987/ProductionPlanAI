package com.autoproduction.mvp.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MvpStoreScheduleAlgorithmViewSupport {
  private MvpStoreScheduleAlgorithmViewSupport() {}

  static Map<String, Object> getScheduleAlgorithm(
    MvpStoreScheduleAlgorithmDomain store,
    String versionNo,
    String requestId
  ) {
    MvpDomain.ScheduleVersion schedule = store.getScheduleEntity(versionNo);
    Map<String, Object> observabilityMetrics = store.buildScheduleObservabilityMetrics(schedule, null, null);
    String strategyCode = MvpStoreScheduleAlgorithmHeaderSupport.resolveStrategyCode(store, schedule);
    String strategyNameCn = store.scheduleStrategyNameCn(strategyCode);

    Map<String, Object> summary = MvpStoreScheduleAlgorithmHeaderSupport.buildSummary(
      store,
      schedule,
      observabilityMetrics
    );
    List<String> logic = MvpStoreScheduleAlgorithmHeaderSupport.buildLogic(schedule, strategyCode, strategyNameCn);
    List<Map<String, Object>> priorityPreview = MvpStoreScheduleAlgorithmHeaderSupport.buildPriorityPreview(
      store,
      strategyCode
    );

    MvpStoreScheduleAlgorithmProcessAnalyticsSupport.Analytics analytics =
      MvpStoreScheduleAlgorithmProcessAnalyticsSupport.buildAnalytics(store, schedule);

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("request_id", requestId);
    out.put("version_no", schedule.versionNo);
    out.put("status", schedule.status);
    out.put("status_name_cn", store.statusNameCn(schedule.status));
    out.put("rule_version_no", schedule.ruleVersionNo);
    out.put("created_at", schedule.createdAt == null ? null : schedule.createdAt.toString());
    out.put("publish_time", schedule.publishTime == null ? null : schedule.publishTime.toString());
    out.put("strategy_code", strategyCode);
    out.put("strategy_name_cn", strategyNameCn);
    out.put("summary", summary);
    out.put("logic", logic);
    out.put("priority_preview", priorityPreview);
    out.put("process_summary", analytics.processSummary());
    out.put("unscheduled_reason_summary", analytics.unscheduledReasonSummary());
    out.put("unscheduled_samples", analytics.unscheduledSamples());
    out.put("metadata", store.deepCopyMap(schedule.metadata));
    out.put("metrics", store.deepCopyMap(schedule.metrics));
    return out;
  }
}

