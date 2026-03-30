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

abstract class MvpStoreMasterdataDomain extends MvpStoreMasterdataRouteSupport {
  protected MvpStoreMasterdataDomain(ErpDataManager erpDataManager) {
    super(erpDataManager);
  }

  public Map<String, Object> getMasterdataConfig(String requestId) {
    synchronized (lock) {
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("request_id", requestId);
      out.put("process_configs", listProcessConfigRowsForEdit());
      out.put("line_topology", listLineTopologyRowsForEdit());
      out.put("initial_carryover_occupancy", listInitialCarryoverRowsForEdit());
      out.put("material_availability", listMaterialAvailabilityRowsForEdit());
      return out;
    }
  }

  public Map<String, Object> getScheduleCalendarRules(String requestId) {
    synchronized (lock) {
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("request_id", requestId);
      out.put("horizon_start_date", state.startDate == null ? null : state.startDate.toString());
      out.put("horizon_days", state.horizonDays);
      out.put("shifts_per_day", state.shiftsPerDay);
      out.put("shift_hours", state.shiftHours);
      out.put("skip_statutory_holidays", state.skipStatutoryHolidays);
      out.put("weekend_rest_mode", normalizeWeekendRestMode(state.weekendRestMode));
      out.put(
        "date_shift_mode_by_date",
        state.dateShiftModeByDate == null ? Map.of() : new LinkedHashMap<>(state.dateShiftModeByDate)
      );
      return out;
    }
  }

  public Map<String, Object> saveMasterdataConfig(
    Map<String, Object> payload,
    String requestId,
    String operator
  ) {
    synchronized (lock) {
      rejectDeprecatedMasterdataConfigFields(payload);
      int updatedRowCount = 0;

      boolean hasProcessConfigs = payload.containsKey("process_configs") || payload.containsKey("processConfigs");
      List<Map<String, Object>> processConfigs = maps(payload.get("process_configs"));
      if (processConfigs.isEmpty()) {
        processConfigs = maps(payload.get("processConfigs"));
      }
      if (hasProcessConfigs) {
        applyProcessConfigPatch(processConfigs);
        updatedRowCount += processConfigs.size();
      }

      boolean hasLineTopology = payload.containsKey("line_topology") || payload.containsKey("lineTopology");
      List<Map<String, Object>> lineTopologyRows = maps(payload.get("line_topology"));
      if (lineTopologyRows.isEmpty()) {
        lineTopologyRows = maps(payload.get("lineTopology"));
      }
      if (hasLineTopology) {
        applyLineTopologyPatch(lineTopologyRows);
        updatedRowCount += lineTopologyRows.size();
      }

      if (hasProcessConfigs) {
        // Resource and material baseline are now system-managed and derived from process/rule window.
        rebuildMasterdataHorizonWindow();
      }

      boolean hasInitialCarryoverRows = payload.containsKey("initial_carryover_occupancy")
        || payload.containsKey("initialCarryoverOccupancy");
      List<Map<String, Object>> initialCarryoverRows = maps(payload.get("initial_carryover_occupancy"));
      if (initialCarryoverRows.isEmpty()) {
        initialCarryoverRows = maps(payload.get("initialCarryoverOccupancy"));
      }
      if (hasInitialCarryoverRows) {
        applyInitialCarryoverPatch(initialCarryoverRows);
        updatedRowCount += initialCarryoverRows.size();
      }

      boolean hasMaterialRows = payload.containsKey("material_availability") || payload.containsKey("materialAvailability");
      List<Map<String, Object>> materialRows = maps(payload.get("material_availability"));
      if (materialRows.isEmpty()) {
        materialRows = maps(payload.get("materialAvailability"));
      }
      if (hasMaterialRows) {
        applyMaterialAvailabilityPatch(materialRows);
        updatedRowCount += materialRows.size();
      }

      appendAudit(
        "MASTERDATA",
        "CONFIG",
        "UPDATE_MASTERDATA_CONFIG",
        operator,
        requestId,
        "updated_rows=" + updatedRowCount
      );

      Map<String, Object> out = getMasterdataConfig(requestId);
      out.put("updated_row_count", updatedRowCount);
      out.put("message", "Masterdata config updated.");
      return out;
    }
  }

  public Map<String, Object> saveScheduleCalendarRules(
    Map<String, Object> payload,
    String requestId,
    String operator
  ) {
    synchronized (lock) {
      boolean planningWindowUpdated = applyPlanningWindowPatch(payload);
      boolean planningCalendarUpdated = applyPlanningCalendarPatch(payload);
      appendAudit(
        "SCHEDULE_CALENDAR_RULES",
        "CONFIG",
        "UPDATE_SCHEDULE_CALENDAR_RULES",
        operator,
        requestId,
        "window_updated=" + planningWindowUpdated + ", calendar_updated=" + planningCalendarUpdated
      );
      Map<String, Object> out = getScheduleCalendarRules(requestId);
      out.put("updated", planningWindowUpdated || planningCalendarUpdated);
      out.put("message", "Schedule calendar rules updated.");
      return out;
    }
  }

  public Map<String, Object> createMasterdataRoute(
    Map<String, Object> payload,
    String requestId,
    String operator
  ) {
    synchronized (lock) {
      String productCode = normalizeCode(string(payload, "product_code", string(payload, "productCode", null)));
      if (productCode.isBlank()) {
        throw badRequest("product_code is required.");
      }
      if (state.processRoutes.containsKey(productCode)) {
        throw badRequest("Route already exists for product_code: " + productCode);
      }

      List<Map<String, Object>> routeStepRows = extractRouteStepRows(payload);
      List<MvpDomain.ProcessStep> steps = parseRouteSteps(routeStepRows);
      upsertProcessRoute(productCode, steps);
      rebuildMasterdataHorizonWindow();

      appendAudit(
        "MASTERDATA",
        "ROUTE-" + productCode,
        "CREATE_PROCESS_ROUTE",
        operator,
        requestId,
        "product_code=" + productCode + ", step_count=" + steps.size()
      );

      return buildRouteMutationResult(requestId, productCode, "Process route created.", false);
    }
  }

  public Map<String, Object> updateMasterdataRoute(
    Map<String, Object> payload,
    String requestId,
    String operator
  ) {
    synchronized (lock) {
      String productCode = normalizeCode(string(payload, "product_code", string(payload, "productCode", null)));
      if (productCode.isBlank()) {
        throw badRequest("product_code is required.");
      }
      if (!state.processRoutes.containsKey(productCode)) {
        throw notFound("Route not found for product_code: " + productCode);
      }

      List<Map<String, Object>> routeStepRows = extractRouteStepRows(payload);
      List<MvpDomain.ProcessStep> steps = parseRouteSteps(routeStepRows);
      upsertProcessRoute(productCode, steps);
      rebuildMasterdataHorizonWindow();

      appendAudit(
        "MASTERDATA",
        "ROUTE-" + productCode,
        "UPDATE_PROCESS_ROUTE",
        operator,
        requestId,
        "product_code=" + productCode + ", step_count=" + steps.size()
      );

      return buildRouteMutationResult(requestId, productCode, "Process route updated.", false);
    }
  }

  public Map<String, Object> copyMasterdataRoute(
    Map<String, Object> payload,
    String requestId,
    String operator
  ) {
    synchronized (lock) {
      String sourceProductCode = normalizeCode(string(
        payload,
        "source_product_code",
        string(payload, "sourceProductCode", string(payload, "product_code", string(payload, "productCode", null)))
      ));
      String targetProductCode = normalizeCode(string(
        payload,
        "target_product_code",
        string(payload, "targetProductCode", string(payload, "new_product_code", string(payload, "newProductCode", null)))
      ));
      if (sourceProductCode.isBlank()) {
        throw badRequest("source_product_code is required.");
      }
      if (targetProductCode.isBlank()) {
        throw badRequest("target_product_code is required.");
      }
      if (sourceProductCode.equals(targetProductCode)) {
        throw badRequest("source_product_code and target_product_code cannot be the same.");
      }

      List<MvpDomain.ProcessStep> sourceRoute = state.processRoutes.get(sourceProductCode);
      if (sourceRoute == null || sourceRoute.isEmpty()) {
        throw notFound("Route not found for source_product_code: " + sourceProductCode);
      }

      boolean overwrite = bool(payload, "overwrite", false);
      if (state.processRoutes.containsKey(targetProductCode) && !overwrite) {
        throw badRequest("Route already exists for target_product_code: " + targetProductCode + ". Set overwrite=true to replace.");
      }

      List<Map<String, Object>> routeStepRows = extractRouteStepRows(payload);
      List<MvpDomain.ProcessStep> steps = routeStepRows.isEmpty()
        ? cloneProcessSteps(sourceRoute)
        : parseRouteSteps(routeStepRows);
      upsertProcessRoute(targetProductCode, steps);
      rebuildMasterdataHorizonWindow();

      appendAudit(
        "MASTERDATA",
        "ROUTE-" + targetProductCode,
        "COPY_PROCESS_ROUTE",
        operator,
        requestId,
        "source_product_code=" + sourceProductCode + ", target_product_code=" + targetProductCode + ", step_count=" + steps.size()
      );

      Map<String, Object> out = buildRouteMutationResult(requestId, targetProductCode, "Process route copied.", false);
      out.put("source_product_code", sourceProductCode);
      return out;
    }
  }

  public Map<String, Object> deleteMasterdataRoute(
    Map<String, Object> payload,
    String requestId,
    String operator
  ) {
    synchronized (lock) {
      String productCode = normalizeCode(string(payload, "product_code", string(payload, "productCode", null)));
      if (productCode.isBlank()) {
        throw badRequest("product_code is required.");
      }
      if (!state.processRoutes.containsKey(productCode)) {
        throw notFound("Route not found for product_code: " + productCode);
      }

      Map<String, List<MvpDomain.ProcessStep>> nextRoutes = mutableProcessRoutesCopy();
      nextRoutes.remove(productCode);
      state.processRoutes = sortProcessRoutes(nextRoutes);
      rebuildMasterdataHorizonWindow();

      appendAudit(
        "MASTERDATA",
        "ROUTE-" + productCode,
        "DELETE_PROCESS_ROUTE",
        operator,
        requestId,
        "product_code=" + productCode
      );

      return buildRouteMutationResult(requestId, productCode, "Process route deleted.", true);
    }
  }

  protected Map<String, Object> buildRouteMutationResult(
    String requestId,
    String productCode,
    String message,
    boolean deleted
  ) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("request_id", requestId);
    out.put("message", message);
    out.put("product_code", productCode);
    out.put("route_no", "ROUTE-" + productCode);
    out.put("deleted", deleted);

    List<MvpDomain.ProcessStep> steps = state.processRoutes.getOrDefault(productCode, List.of());
    List<Map<String, Object>> stepRows = new ArrayList<>();
    for (int i = 0; i < steps.size(); i += 1) {
      MvpDomain.ProcessStep step = steps.get(i);
      stepRows.add(localizeRow(Map.of(
        "route_no", "ROUTE-" + productCode,
        "product_code", productCode,
        "process_code", step.processCode,
        "sequence_no", i + 1,
        "dependency_type", step.dependencyType
      )));
    }
    out.put("steps", stepRows);
    out.put("route_count", state.processRoutes.size());
    return out;
  }

}