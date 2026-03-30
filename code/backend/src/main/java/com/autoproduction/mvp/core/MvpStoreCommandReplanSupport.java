package com.autoproduction.mvp.core;

import com.autoproduction.mvp.module.integration.erp.ErpDataManager;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

abstract class MvpStoreCommandReplanSupport extends MvpStoreEntityMappingSupport {
  protected MvpStoreCommandReplanSupport(ErpDataManager erpDataManager) {
    super(erpDataManager);
  }

  protected Map<String, Object> doCreateReplanJob(Map<String, Object> payload, String requestId, String operator) {
    String baseVersionNo = string(payload, "base_version_no", null);
    if (baseVersionNo == null) {
      throw badRequest("base_version_no is required.");
    }
    MvpDomain.ScheduleVersion baseVersion = getScheduleEntity(baseVersionNo);
    String strategyCode = normalizeScheduleStrategy(
      string(
        payload,
        "strategy_code",
        firstString(baseVersion.metadata, "schedule_strategy_code", "scheduleStrategyCode", "strategy_code", "strategyCode")
      )
    );
    String jobNo = "RPJ-%05d".formatted(replanSeq.incrementAndGet());
    Map<String, Object> job = new LinkedHashMap<>();
    job.put("request_id", requestId);
    job.put("job_no", jobNo);
    job.put("trigger_type", string(payload, "trigger_type", "PROGRESS_GAP"));
    job.put("scope_type", string(payload, "scope_type", "LOCAL"));
    job.put("base_version_no", baseVersionNo);
    job.put("result_version_no", null);
    job.put("status", "RUNNING");
    job.put("created_at", OffsetDateTime.now(ZoneOffset.UTC).toString());
    job.put("finished_at", null);
    job.put("error_msg", "");
    job.put("strategy_code", strategyCode);
    job.put("strategy_name_cn", scheduleStrategyNameCn(strategyCode));
    state.replanJobs.add(job);
    long startNanos = System.nanoTime();
    String reason = string(payload, "reason", null);
    try {
      Map<String, Object> generatePayload = new LinkedHashMap<>();
      generatePayload.put("request_id", requestId + ":generate");
          generatePayload.put("base_version_no", baseVersionNo);
          generatePayload.put("autoReplan", true);
          generatePayload.put("strategy_code", strategyCode);
          generatePayload.put("compact_response", true);
          Map<String, Object> schedule = generateSchedule(
            generatePayload,
            requestId + ":generate",
            operator
          );
      job.put("result_version_no", schedule.get("versionNo"));
      job.put("status", "DONE");
      job.put("finished_at", OffsetDateTime.now(ZoneOffset.UTC).toString());

      Map<String, Object> perfContext = new LinkedHashMap<>();
      perfContext.put("request_id", requestId);
      perfContext.put("phase", "replan_job");
      perfContext.put("duration_ms", elapsedMillis(startNanos));
      perfContext.put("result_version_no", schedule.get("versionNo"));
      perfContext.put("strategy_code", strategyCode);
      appendAudit("REPLAN_JOB", jobNo, "TRIGGER_REPLAN", operator, requestId, reason, perfContext);
      return new LinkedHashMap<>(job);
    } catch (RuntimeException ex) {
      job.put("status", "FAILED");
      job.put("finished_at", OffsetDateTime.now(ZoneOffset.UTC).toString());
      job.put("error_msg", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());

      Map<String, Object> perfContext = new LinkedHashMap<>();
      perfContext.put("request_id", requestId);
      perfContext.put("phase", "replan_job");
      perfContext.put("duration_ms", elapsedMillis(startNanos));
      perfContext.put("error_msg", job.get("error_msg"));
      appendAudit("REPLAN_JOB", jobNo, "TRIGGER_REPLAN", operator, requestId, reason, perfContext);
      throw ex;
    }
  }

  protected Map<String, Object> maybeTriggerProgressGapReplan(MvpDomain.Reporting reporting, String requestId, String operator) {
    if (state.schedules.isEmpty()) {
      return null;
    }
    MvpDomain.ScheduleVersion latest = state.schedules.get(state.schedules.size() - 1);
    double plannedQty = latest.allocations.stream()
      .filter(row -> row.orderNo.equals(reporting.orderNo)
        && row.productCode.equals(reporting.productCode)
        && row.processCode.equals(reporting.processCode))
      .mapToDouble(row -> row.scheduledQty)
      .sum();
    if (plannedQty <= 0d) {
      return null;
    }
    double reportedQty = state.reportings.stream()
      .filter(row -> row.orderNo.equals(reporting.orderNo)
        && row.productCode.equals(reporting.productCode)
        && row.processCode.equals(reporting.processCode))
      .mapToDouble(row -> row.reportQty)
      .sum();
    double deviation = Math.abs(plannedQty - reportedQty) / plannedQty * 100d;
    if (deviation <= 10d) {
      return null;
    }
    Map<String, Object> job = doCreateReplanJob(
      Map.of(
        "request_id", requestId + ":progress-gap",
        "trigger_type", "PROGRESS_GAP",
        "scope_type", "LOCAL",
        "base_version_no", latest.versionNo,
        "reason", "Auto-trigger by reporting deviation > 10%"
      ),
      requestId + ":progress-gap",
      "system"
    );
    Map<String, Object> alert = createAlert(
      "PROGRESS_GAP",
      "WARN",
      reporting.orderNo,
      reporting.processCode,
      latest.versionNo,
      deviation,
      10d,
      plannedQty,
      reportedQty
    );
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("job_no", job.get("job_no"));
    out.put("alert_id", alert.get("alert_id"));
    return out;
  }

  protected Map<String, Object> createAlert(
    String alertType,
    String severity,
    String orderNo,
    String processCode,
    String versionNo,
    double triggerValue,
    double thresholdValue,
    Double targetValue,
    Double actualValue
  ) {
    String alertId = "ALT-%05d".formatted(alertSeq.incrementAndGet());
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("alert_id", alertId);
    row.put("alert_type", alertType);
    row.put("severity", severity);
    row.put("order_no", orderNo);
    row.put("order_type", "production");
    row.put("process_code", processCode);
    row.put("version_no", versionNo);
    row.put("trigger_value", round2(triggerValue));
    row.put("threshold_value", round2(thresholdValue));
    row.put("deviation_percent", round2(triggerValue));
    row.put("alert_threshold_percent", round2(thresholdValue));
    row.put("target_value", targetValue == null ? null : round2(targetValue));
    row.put("actual_value", actualValue == null ? null : round2(actualValue));
    row.put("status", "OPEN");
    row.put("created_at", OffsetDateTime.now(ZoneOffset.UTC).toString());
    row.put("ack_by", null);
    row.put("ack_time", null);
    row.put("close_by", null);
    row.put("close_time", null);
    state.alerts.add(row);
    return row;
  }

  protected Map<String, Object> enrichAlertMetrics(Map<String, Object> source) {
    Map<String, Object> row = new LinkedHashMap<>(source);
    String alertType = string(row, "alert_type", "");
    if (!"PROGRESS_GAP".equals(alertType)) {
      return row;
    }
    if (row.get("target_value") != null
      && row.get("actual_value") != null
      && row.get("deviation_percent") != null
      && row.get("alert_threshold_percent") != null) {
      return row;
    }

    String orderNo = string(row, "order_no", null);
    String processCode = string(row, "process_code", null);
    if (orderNo == null || orderNo.isBlank() || processCode == null || processCode.isBlank()) {
      return row;
    }

    String versionNo = string(row, "version_no", null);
    MvpDomain.ScheduleVersion schedule = null;
    if (versionNo != null && !versionNo.isBlank()) {
      schedule = state.schedules.stream().filter(item -> versionNo.equals(item.versionNo)).findFirst().orElse(null);
    }
    if (schedule == null && !state.schedules.isEmpty()) {
      schedule = state.schedules.get(state.schedules.size() - 1);
    }
    if (schedule == null) {
      return row;
    }

    double plannedQty = schedule.allocations.stream()
      .filter(item -> orderNo.equals(item.orderNo) && processCode.equals(item.processCode))
      .mapToDouble(item -> item.scheduledQty)
      .sum();
    if (plannedQty <= 0d) {
      return row;
    }
    double reportedQty = state.reportings.stream()
      .filter(item -> orderNo.equals(item.orderNo) && processCode.equals(item.processCode))
      .mapToDouble(item -> item.reportQty)
      .sum();
    double deviation = Math.abs(plannedQty - reportedQty) / plannedQty * 100d;
    double threshold = number(row, "threshold_value", 10d);

    row.put("target_value", round2(plannedQty));
    row.put("actual_value", round2(reportedQty));
    row.put("deviation_percent", round2(deviation));
    row.put("alert_threshold_percent", round2(threshold));
    row.putIfAbsent("trigger_value", round2(deviation));
    row.putIfAbsent("threshold_value", round2(threshold));
    return row;
  }

  protected Map<String, Object> updateAlert(String alertId, Map<String, Object> payload, String requestId, String operator, String nextStatus) {
    return runIdempotent(requestId, nextStatus + "_ALERT#" + alertId, () -> {
      Map<String, Object> alert = state.alerts.stream()
        .filter(row -> Objects.equals(row.get("alert_id"), alertId))
        .findFirst()
        .orElseThrow(() -> notFound("Alert %s not found.".formatted(alertId)));
      alert.put("status", nextStatus);
      if ("ACKED".equals(nextStatus)) {
        alert.put("ack_by", string(payload, "operator", operator));
        alert.put("ack_time", OffsetDateTime.now(ZoneOffset.UTC).toString());
      }
      if ("CLOSED".equals(nextStatus)) {
        alert.put("close_by", string(payload, "operator", operator));
        alert.put("close_time", OffsetDateTime.now(ZoneOffset.UTC).toString());
      }
      appendAudit("ALERT", alertId, "ACKED".equals(nextStatus) ? "ACK_ALERT" : "CLOSE_ALERT", operator, requestId, string(payload, "reason", null));
      return Map.of("request_id", requestId, "success", true, "message", "Alert %s set to %s.".formatted(alertId, nextStatus));
    });
  }
}

