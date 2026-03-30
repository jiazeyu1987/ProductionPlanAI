package com.autoproduction.mvp.core;

import com.autoproduction.mvp.module.integration.erp.ErpDataManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

abstract class MvpStoreMasterdataPlanningWindowSupport extends MvpStoreMasterdataRouteEditRowsSupport {
  protected MvpStoreMasterdataPlanningWindowSupport(ErpDataManager erpDataManager) {
    super(erpDataManager);
  }

  protected void rejectDeprecatedMasterdataConfigFields(Map<String, Object> payload) {
    if (payload == null || payload.isEmpty()) {
      return;
    }
    for (String key : DEPRECATED_MASTERDATA_CONFIG_FIELDS) {
      if (payload.containsKey(key)) {
        throw badRequest("Field is no longer supported in masterdata/config: " + key + ". Use /schedule-calendar/rules.");
      }
    }
  }

  protected boolean applyPlanningWindowPatch(Map<String, Object> payload) {
    boolean hasStartDate = payload.containsKey("horizon_start_date") || payload.containsKey("horizonStartDate");
    boolean hasHorizonDays = payload.containsKey("horizon_days") || payload.containsKey("horizonDays");
    boolean hasShiftsPerDay = payload.containsKey("shifts_per_day") || payload.containsKey("shiftsPerDay");
    if (!hasStartDate && !hasHorizonDays && !hasShiftsPerDay) {
      return false;
    }

    LocalDate nextStartDate = state.startDate;
    if (hasStartDate) {
      String text = string(payload, "horizon_start_date", string(payload, "horizonStartDate", null));
      if (text == null || text.isBlank()) {
        throw badRequest("horizon_start_date is required.");
      }
      nextStartDate = parseConfigDate(text);
    }
    int nextHorizonDays = hasHorizonDays
      ? clampInt((int) Math.round(number(payload, "horizon_days", number(payload, "horizonDays", state.horizonDays))), 1, 90)
      : state.horizonDays;
    int nextShiftsPerDay = 2;

    if (
      Objects.equals(nextStartDate, state.startDate)
        && nextHorizonDays == state.horizonDays
        && nextShiftsPerDay == state.shiftsPerDay
    ) {
      return false;
    }

    state.startDate = nextStartDate;
    state.horizonDays = nextHorizonDays;
    state.shiftsPerDay = nextShiftsPerDay;
    rebuildMasterdataHorizonWindow();
    return true;
  }

  protected boolean applyPlanningCalendarPatch(Map<String, Object> payload) {
    boolean hasSkipStatutoryHolidays = payload.containsKey("skip_statutory_holidays")
      || payload.containsKey("skipStatutoryHolidays");
    boolean hasWeekendRestMode = payload.containsKey("weekend_rest_mode") || payload.containsKey("weekendRestMode");
    boolean hasDateShiftModeByDate = payload.containsKey("date_shift_mode_by_date")
      || payload.containsKey("dateShiftModeByDate");
    if (!hasSkipStatutoryHolidays && !hasWeekendRestMode && !hasDateShiftModeByDate) {
      return false;
    }

    boolean nextSkipStatutoryHolidays = hasSkipStatutoryHolidays
      ? bool(
        payload,
        "skip_statutory_holidays",
        bool(payload, "skipStatutoryHolidays", state.skipStatutoryHolidays)
      )
      : state.skipStatutoryHolidays;
    String nextWeekendRestMode = hasWeekendRestMode
      ? normalizeWeekendRestMode(string(payload, "weekend_rest_mode", string(payload, "weekendRestMode", state.weekendRestMode)))
      : normalizeWeekendRestMode(state.weekendRestMode);
    Map<String, String> nextDateShiftModeByDate = hasDateShiftModeByDate
      ? normalizeDateShiftModeByDate(
        payload.get("date_shift_mode_by_date") != null ? payload.get("date_shift_mode_by_date") : payload.get("dateShiftModeByDate")
      )
      : state.dateShiftModeByDate == null ? new LinkedHashMap<>() : new LinkedHashMap<>(state.dateShiftModeByDate);

    if (
      nextSkipStatutoryHolidays == state.skipStatutoryHolidays
        && Objects.equals(nextWeekendRestMode, normalizeWeekendRestMode(state.weekendRestMode))
        && Objects.equals(nextDateShiftModeByDate, state.dateShiftModeByDate)
    ) {
      return false;
    }

    state.skipStatutoryHolidays = nextSkipStatutoryHolidays;
    state.weekendRestMode = nextWeekendRestMode;
    state.dateShiftModeByDate = nextDateShiftModeByDate;
    rebuildMasterdataHorizonWindow();
    return true;
  }

  protected void rebuildMasterdataHorizonWindow() {
    Map<String, Integer> workersByKey = new HashMap<>();
    for (MvpDomain.ResourceRow row : state.workerPools) {
      workersByKey.put(row.date + "#" + normalizeShiftCode(row.shiftCode) + "#" + normalizeCode(row.processCode), row.available);
    }
    Map<String, Integer> machinesByKey = new HashMap<>();
    for (MvpDomain.ResourceRow row : state.machinePools) {
      machinesByKey.put(row.date + "#" + normalizeShiftCode(row.shiftCode) + "#" + normalizeCode(row.processCode), row.available);
    }
    Map<String, Integer> occupiedWorkersByKey = new HashMap<>();
    for (MvpDomain.ResourceRow row : state.initialWorkerOccupancy) {
      occupiedWorkersByKey.put(
        row.date + "#" + normalizeShiftCode(row.shiftCode) + "#" + normalizeCode(row.processCode),
        Math.max(0, row.available)
      );
    }
    Map<String, Integer> occupiedMachinesByKey = new HashMap<>();
    for (MvpDomain.ResourceRow row : state.initialMachineOccupancy) {
      occupiedMachinesByKey.put(
        row.date + "#" + normalizeShiftCode(row.shiftCode) + "#" + normalizeCode(row.processCode),
        Math.max(0, row.available)
      );
    }
    Map<String, Double> materialByKey = new HashMap<>();
    for (MvpDomain.MaterialRow row : state.materialAvailability) {
      materialByKey.put(
        row.date + "#" + normalizeShiftCode(row.shiftCode) + "#" + normalizeCode(row.productCode) + "#" + normalizeCode(row.processCode),
        row.availableQty
      );
    }

    String[] shiftCodes = {"D", "N"};
    List<MvpDomain.ShiftRow> shiftRows = new ArrayList<>();
    List<MvpDomain.ResourceRow> workerRows = new ArrayList<>();
    List<MvpDomain.ResourceRow> machineRows = new ArrayList<>();
    List<MvpDomain.ResourceRow> initialWorkerRows = new ArrayList<>();
    List<MvpDomain.ResourceRow> initialMachineRows = new ArrayList<>();
    List<MvpDomain.MaterialRow> materialRows = new ArrayList<>();

    for (int i = 0; i < Math.max(1, state.horizonDays); i += 1) {
      LocalDate date = state.startDate.plusDays(i);
      for (int s = 0; s < Math.max(1, Math.min(2, state.shiftsPerDay)); s += 1) {
        String shiftCode = shiftCodes[s];
        String dateShiftKey = date + "#" + shiftCode;
        boolean open = isShiftOpenInDateMode(shiftCode, resolveDateShiftMode(date));
        shiftRows.add(new MvpDomain.ShiftRow(date, shiftCode, open));

        for (MvpDomain.ProcessConfig process : state.processes) {
          String processCode = normalizeCode(process.processCode);
          String usageKey = dateShiftKey + "#" + processCode;
          int defaultWorkers = Math.max(
            process.requiredWorkers,
            BASE_WORKERS_BY_PROCESS.getOrDefault(processCode, Math.max(2, process.requiredWorkers * 3))
          );
          int defaultMachines = Math.max(
            process.requiredMachines,
            BASE_MACHINES_BY_PROCESS.getOrDefault(processCode, Math.max(1, process.requiredMachines * 2))
          );
          int workers = Math.max(process.requiredWorkers, workersByKey.getOrDefault(usageKey, defaultWorkers));
          int machines = Math.max(process.requiredMachines, machinesByKey.getOrDefault(usageKey, defaultMachines));
          int occupiedWorkers = clampInt(occupiedWorkersByKey.getOrDefault(usageKey, 0), 0, workers);
          int occupiedMachines = clampInt(occupiedMachinesByKey.getOrDefault(usageKey, 0), 0, machines);
          workerRows.add(new MvpDomain.ResourceRow(date, shiftCode, processCode, workers));
          machineRows.add(new MvpDomain.ResourceRow(date, shiftCode, processCode, machines));
          if (occupiedWorkers > 0) {
            initialWorkerRows.add(new MvpDomain.ResourceRow(date, shiftCode, processCode, occupiedWorkers));
          }
          if (occupiedMachines > 0) {
            initialMachineRows.add(new MvpDomain.ResourceRow(date, shiftCode, processCode, occupiedMachines));
          }
        }

        for (Map.Entry<String, List<MvpDomain.ProcessStep>> route : state.processRoutes.entrySet()) {
          String productCode = normalizeCode(route.getKey());
          for (MvpDomain.ProcessStep step : route.getValue()) {
            String processCode = normalizeCode(step.processCode);
            String materialKey = dateShiftKey + "#" + productCode + "#" + processCode;
            double availableQty = materialByKey.getOrDefault(materialKey, 5000d);
            materialRows.add(new MvpDomain.MaterialRow(date, shiftCode, productCode, processCode, round2(Math.max(0d, availableQty))));
          }
        }
      }
    }

    state.shiftCalendar = shiftRows;
    state.workerPools = workerRows;
    state.machinePools = machineRows;
    state.initialWorkerOccupancy = initialWorkerRows;
    state.initialMachineOccupancy = initialMachineRows;
    state.materialAvailability = materialRows;
  }

  protected boolean isDateInCurrentHorizon(LocalDate date) {
    if (date == null || state.startDate == null) {
      return false;
    }
    LocalDate endExclusive = state.startDate.plusDays(Math.max(1, state.horizonDays));
    return !date.isBefore(state.startDate) && date.isBefore(endExclusive);
  }

  protected boolean isShiftEnabledInCurrentSetting(String shiftCode) {
    String normalized = normalizeShiftCode(shiftCode);
    if ("D".equals(normalized)) {
      return true;
    }
    return "N".equals(normalized);
  }
}

