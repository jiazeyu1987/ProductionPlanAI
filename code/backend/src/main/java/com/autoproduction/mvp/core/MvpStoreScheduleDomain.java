package com.autoproduction.mvp.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.autoproduction.mvp.module.integration.erp.ErpDataManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service

abstract class MvpStoreScheduleDomain extends MvpStoreReportDomain {
  protected MvpStoreScheduleDomain(ErpDataManager erpDataManager) {
    super(erpDataManager);
  }

  public Map<String, Object> generateSchedule(Map<String, Object> options, String requestId, String operator) {
    synchronized (lock) {
      long totalStart = System.nanoTime();
      Map<String, Long> phaseDurationMs = new LinkedHashMap<>();

      long phaseStart = System.nanoTime();
      syncCompletedQtyFromFinalProcessReports();
      phaseDurationMs.put("sync_completed_qty", elapsedMillis(phaseStart));

      phaseStart = System.nanoTime();
      boolean autoReplan = bool(options, "autoReplan", false);
      String strategyCode = normalizeScheduleStrategy(
        string(
          options,
          "strategy_code",
          string(options, "schedule_strategy", string(options, "strategy", null))
        )
      );
      String baseVersionNo = string(options, "base_version_no", null);
      MvpDomain.ScheduleVersion baseVersion = null;
      if (baseVersionNo != null && !baseVersionNo.isBlank()) {
        baseVersion = state.schedules.stream().filter(item -> item.versionNo.equals(baseVersionNo)).findFirst().orElse(null);
      }

      OrderMaterialConstraintSnapshot materialConstraintSnapshot = buildOrderMaterialConstraintSnapshot(false);
      MvpDomain.State scheduleState = deepCopyState(state);
      applyOrderMaterialConstraintsForSchedule(scheduleState, materialConstraintSnapshot.constraintByOrderNo);

      List<String> lockedOrders = new ArrayList<>();
      List<MvpDomain.Order> orders = new ArrayList<>();
      for (MvpDomain.Order order : scheduleState.orders) {
        if (!hasRemainingQty(order)) {
          continue;
        }
        if (order.lockFlag) {
          lockedOrders.add(order.orderNo);
        }
        orders.add(order);
      }
      phaseDurationMs.put("prepare_input", elapsedMillis(phaseStart));

      phaseStart = System.nanoTime();
      String versionNo = "V%03d".formatted(state.schedules.size() + 1);
      MvpDomain.ScheduleVersion schedule = SchedulerEngine.generate(
        scheduleState,
        orders,
        requestId,
        versionNo,
        baseVersion,
        new HashSet<>(lockedOrders),
        strategyCode
      );
      phaseDurationMs.put("engine_generate", elapsedMillis(phaseStart));

      phaseStart = System.nanoTime();
      schedule.status = "DRAFT";
      schedule.basedOnVersion = baseVersionNo;
      schedule.ruleVersionNo = "RULE-P0-BASE";
      schedule.publishTime = null;
      schedule.createdBy = operator;
      schedule.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
      schedule.metadata.put("autoReplan", autoReplan);
      schedule.metadata.put("excludedLockedOrders", List.of());
      schedule.metadata.put("preservedLockedOrders", lockedOrders);
      schedule.metadata.put("baseVersionResolved", baseVersion != null);
      schedule.metadata.put("schedule_strategy_code", strategyCode);
      schedule.metadata.put("schedule_strategy_name_cn", scheduleStrategyNameCn(strategyCode));
      schedule.metadata.put("order_material_constraint_count", materialConstraintSnapshot.constraintByOrderNo.size());
      schedule.metadata.put("schedule_generate_phase_duration_ms", new LinkedHashMap<>(phaseDurationMs));

      long candidateOrderCount = orders.stream().map(order -> order.orderNo).filter(Objects::nonNull).distinct().count();
      long candidateTaskCount = schedule.tasks.size();
      schedule.metrics.putAll(buildScheduleObservabilityMetrics(schedule, candidateOrderCount, candidateTaskCount));

      state.schedules.add(schedule);
      phaseDurationMs.put("persist_and_metrics", elapsedMillis(phaseStart));

      long totalDurationMs = elapsedMillis(totalStart);
      schedule.metrics.put("schedule_generate_duration_ms", totalDurationMs);
      schedule.metrics.put("schedule_generate_phase_duration_ms", new LinkedHashMap<>(phaseDurationMs));
      schedule.metadata.put("schedule_generate_duration_ms", totalDurationMs);

      Map<String, Object> perfContext = new LinkedHashMap<>();
      perfContext.put("request_id", requestId);
      perfContext.put("version_no", schedule.versionNo);
      perfContext.put("phase", "schedule_generate");
      perfContext.put("duration_ms", totalDurationMs);
      perfContext.put("phase_duration_ms", new LinkedHashMap<>(phaseDurationMs));
      perfContext.put("strategy_code", strategyCode);
      perfContext.put("strategy_name_cn", scheduleStrategyNameCn(strategyCode));
      appendAudit(
        "SCHEDULE_VERSION",
        schedule.versionNo,
        autoReplan ? "AUTO_REPLAN_SCHEDULE" : "GENERATE_SCHEDULE",
        operator,
        requestId,
        string(options, "reason", null),
        perfContext
      );

      boolean compactResponse = bool(
        options,
        "compact_response",
        bool(options, "compactResponse", false)
      );
      if (compactResponse) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("request_id", requestId);
        out.put("version_no", schedule.versionNo);
        out.put("versionNo", schedule.versionNo);
        out.put("status", schedule.status);
        out.put("generated_at", schedule.generatedAt == null ? null : schedule.generatedAt.toString());
        out.put("generatedAt", schedule.generatedAt == null ? null : schedule.generatedAt.toString());
        out.put(
          "schedule_completion_rate",
          number(schedule.metrics, "schedule_completion_rate", number(schedule.metrics, "scheduleCompletionRate", 0d))
        );
        out.put("unscheduled_task_count", schedule.unscheduled == null ? 0 : schedule.unscheduled.size());
        return out;
      }
      return toScheduleMap(schedule);
    }
  }

  public List<Map<String, Object>> listSchedules() {
    synchronized (lock) {
      return state.schedules.stream().map(this::toScheduleMap).toList();
    }
  }

  public Map<String, Object> getLatestSchedule() {
    synchronized (lock) {
      if (state.schedules.isEmpty()) {
        throw badRequest("No schedule generated.");
      }
      return toScheduleMap(state.schedules.get(state.schedules.size() - 1));
    }
  }

  public Map<String, Object> validateSchedule(String versionNo) {
    synchronized (lock) {
      MvpDomain.ScheduleVersion schedule = getScheduleEntity(versionNo);
      Map<String, Object> validation = SchedulerEngine.validate(state, schedule);
      Map<String, Object> response = new LinkedHashMap<>();
      response.put("versionNo", schedule.versionNo);
      response.putAll(validation);
      return response;
    }
  }

  public Map<String, Object> publishSchedule(String versionNo, Map<String, Object> payload, String requestId, String operator) {
    synchronized (lock) {
      return runIdempotent(requestId, "PUBLISH#" + versionNo, () -> {
        MvpDomain.ScheduleVersion target = getScheduleEntity(versionNo);
        if (state.publishedVersionNo != null && !state.publishedVersionNo.equals(versionNo)) {
          MvpDomain.ScheduleVersion previous = getScheduleEntity(state.publishedVersionNo);
          previous.status = "SUPERSEDED";
        }
        state.publishedVersionNo = versionNo;
        target.status = "PUBLISHED";
        target.publishTime = OffsetDateTime.now(ZoneOffset.UTC);
        appendAudit("SCHEDULE_VERSION", versionNo, "PUBLISH_VERSION", operator, requestId, string(payload, "reason", null));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("request_id", requestId);
        out.put("success", true);
        out.put("message", "Version %s published.".formatted(versionNo));
        out.put("version_no", versionNo);
        out.put("versionNo", versionNo);
        out.put("publishedAt", target.publishTime.toString());
        out.put("status", target.status);
        return out;
      });
    }
  }

  public Map<String, Object> rollbackSchedule(String versionNo, Map<String, Object> payload, String requestId, String operator) {
    synchronized (lock) {
      return runIdempotent(requestId, "ROLLBACK#" + versionNo, () -> {
        MvpDomain.ScheduleVersion target = getScheduleEntity(versionNo);
        if (state.publishedVersionNo != null && !state.publishedVersionNo.equals(versionNo)) {
          MvpDomain.ScheduleVersion current = getScheduleEntity(state.publishedVersionNo);
          current.status = "ROLLED_BACK";
          target.rollbackFrom = current.versionNo;
        }
        target.status = "PUBLISHED";
        target.publishTime = OffsetDateTime.now(ZoneOffset.UTC);
        state.publishedVersionNo = versionNo;
        appendAudit("SCHEDULE_VERSION", versionNo, "ROLLBACK_VERSION", operator, requestId, string(payload, "reason", null));
        return Map.of(
          "request_id", requestId,
          "success", true,
          "message", "Rollback to %s completed.".formatted(versionNo),
          "version_no", versionNo
        );
      });
    }
  }

}