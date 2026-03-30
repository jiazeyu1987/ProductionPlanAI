package com.autoproduction.mvp.core;

import static com.autoproduction.mvp.core.SchedulerPlanningComponents.componentArrivalDate;
import static com.autoproduction.mvp.core.SchedulerPlanningComponents.componentKeyFromOrder;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.EPS;
import static com.autoproduction.mvp.core.SchedulerPlanningUtil.parseDate;
import static com.autoproduction.mvp.core.SchedulerPlanningUtil.reportingKey;
import static com.autoproduction.mvp.core.SchedulerPlanningUtil.round3;
import static com.autoproduction.mvp.core.SchedulerPlanningUtil.normalizeProcessCode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SchedulerPlanningIndexes {
  private SchedulerPlanningIndexes() {}

  static List<Map<String, Object>> buildShifts(MvpDomain.State state) {
    List<Map<String, Object>> shifts = new ArrayList<>();
    Map<String, Boolean> openByKey = new HashMap<>();
    for (MvpDomain.ShiftRow row : state.shiftCalendar) {
      openByKey.put(row.date + "#" + row.shiftCode, row.open);
    }
    String[] shiftCodes = {"D", "N"};
    for (int i = 0; i < state.horizonDays; i += 1) {
      LocalDate date = state.startDate.plusDays(i);
      for (int s = 0; s < state.shiftsPerDay; s += 1) {
        String shiftCode = shiftCodes[s];
        String shiftId = date + "#" + shiftCode;
        if (!openByKey.getOrDefault(shiftId, true)) {
          continue;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("index", shifts.size());
        row.put("date", date.toString());
        row.put("shiftCode", shiftCode);
        row.put("shiftId", shiftId);
        shifts.add(row);
      }
    }
    if (shifts.isEmpty()) {
      throw new IllegalArgumentException("No open shifts found in planning horizon.");
    }
    return shifts;
  }

  static Map<String, Integer> shiftIndexById(List<Map<String, Object>> shifts) {
    Map<String, Integer> out = new HashMap<>();
    for (int i = 0; i < shifts.size(); i += 1) {
      String shiftId = String.valueOf(shifts.get(i).get("shiftId"));
      if (shiftId != null && !shiftId.isBlank()) {
        out.put(shiftId, i);
      }
    }
    return out;
  }

  static Map<String, Double> cumulativeMaterialIndex(
    List<MvpDomain.MaterialRow> rows,
    List<Map<String, Object>> shifts
  ) {
    Map<String, List<MvpDomain.MaterialRow>> rowsByShift = new HashMap<>();
    for (MvpDomain.MaterialRow row : rows) {
      String shiftId = row.date + "#" + row.shiftCode;
      rowsByShift.computeIfAbsent(shiftId, ignored -> new ArrayList<>()).add(row);
    }
    Map<String, Double> cumulativeByProductProcess = new HashMap<>();
    Map<String, Double> out = new HashMap<>();
    for (Map<String, Object> shift : shifts) {
      String shiftId = String.valueOf(shift.get("shiftId"));
      List<MvpDomain.MaterialRow> rowsInShift = rowsByShift.getOrDefault(shiftId, List.of());
      for (MvpDomain.MaterialRow row : rowsInShift) {
        String materialKey = row.productCode + "#" + row.processCode;
        cumulativeByProductProcess.merge(materialKey, row.availableQty, Double::sum);
      }
      for (Map.Entry<String, Double> entry : cumulativeByProductProcess.entrySet()) {
        out.put(shiftId + "#" + entry.getKey(), round3(entry.getValue()));
      }
    }
    return out;
  }

  static Map<String, Double> cumulativeComponentIndex(
    MvpDomain.State state,
    List<Map<String, Object>> shifts
  ) {
    Map<String, Double> initialByComponent = new HashMap<>();
    Map<String, Map<LocalDate, Double>> arrivalsByComponentDate = new HashMap<>();
    for (MvpDomain.Order order : state.orders) {
      if (order == null) {
        continue;
      }
      String componentKey = componentKeyFromOrder(order);
      if (componentKey == null) {
        continue;
      }
      MvpDomain.OrderBusinessData businessData = order.businessData;
      double initial = 0d;
      double inbound = 0d;
      if (businessData != null) {
        initial = Math.max(0d, businessData.semiFinishedInventory + businessData.semiFinishedWip);
        inbound = Math.max(0d, businessData.pendingInboundQty);
      }
      initialByComponent.merge(componentKey, initial, Math::max);
      if (inbound <= EPS) {
        continue;
      }
      LocalDate arrivalDate = componentArrivalDate(order, state.startDate);
      if (arrivalDate == null) {
        initialByComponent.merge(componentKey, initial + inbound, Math::max);
        continue;
      }
      arrivalsByComponentDate
        .computeIfAbsent(componentKey, ignored -> new HashMap<>())
        .merge(arrivalDate, inbound, Math::max);
    }

    Set<String> components = new HashSet<>();
    components.addAll(initialByComponent.keySet());
    components.addAll(arrivalsByComponentDate.keySet());
    Map<String, Double> out = new HashMap<>();
    for (String component : components) {
      Map<LocalDate, Double> arrivalsByDate = arrivalsByComponentDate.getOrDefault(component, Map.of());
      double initial = initialByComponent.getOrDefault(component, 0d);
      for (Map<String, Object> shift : shifts) {
        String shiftId = String.valueOf(shift.get("shiftId"));
        LocalDate shiftDate = parseDate(String.valueOf(shift.get("date")));
        double arrived = 0d;
        for (Map.Entry<LocalDate, Double> arrival : arrivalsByDate.entrySet()) {
          if (!arrival.getKey().isAfter(shiftDate)) {
            arrived += arrival.getValue();
          }
        }
        double cumulative = round3(initial + arrived);
        out.put(shiftId + "#" + component, round3(cumulative));
      }
    }
    return out;
  }

  static Map<String, Double> cumulativeReportedByOrderProductProcess(List<MvpDomain.Reporting> reportings) {
    if (reportings == null || reportings.isEmpty()) {
      return Map.of();
    }
    Map<String, Double> out = new HashMap<>();
    for (MvpDomain.Reporting reporting : reportings) {
      if (reporting == null) {
        continue;
      }
      String processCode = normalizeProcessCode(reporting.processCode);
      if (processCode == null) {
        continue;
      }
      String key = reportingKey(reporting.orderNo, reporting.productCode, processCode);
      out.merge(key, Math.max(0d, reporting.reportQty), Double::sum);
    }
    return out;
  }

  static Map<String, Integer> resourceIndex(List<MvpDomain.ResourceRow> rows) {
    Map<String, Integer> out = new HashMap<>();
    for (MvpDomain.ResourceRow row : rows) {
      out.put(row.date + "#" + row.shiftCode + "#" + row.processCode, Math.max(0, row.available));
    }
    return out;
  }

  static Map<String, Integer> effectiveResourceIndex(
    List<MvpDomain.ResourceRow> capacityRows,
    List<MvpDomain.ResourceRow> occupiedRows
  ) {
    Map<String, Integer> capacity = resourceIndex(capacityRows);
    Map<String, Integer> occupied = resourceIndex(occupiedRows);
    Map<String, Integer> out = new HashMap<>();
    for (Map.Entry<String, Integer> entry : capacity.entrySet()) {
      int gross = Math.max(0, entry.getValue());
      int used = Math.max(0, occupied.getOrDefault(entry.getKey(), 0));
      out.put(entry.getKey(), Math.max(0, gross - used));
    }
    return out;
  }

  static Map<String, Integer> maxResourceByProcess(Map<String, Integer> byShiftProcess) {
    Map<String, Integer> out = new HashMap<>();
    for (Map.Entry<String, Integer> entry : byShiftProcess.entrySet()) {
      String key = entry.getKey();
      if (key == null) {
        continue;
      }
      int idx = key.lastIndexOf('#');
      if (idx < 0 || idx + 1 >= key.length()) {
        continue;
      }
      String processCode = key.substring(idx + 1);
      out.merge(processCode, Math.max(0, entry.getValue()), Math::max);
    }
    return out;
  }

  static Map<String, Double> materialIndex(List<MvpDomain.MaterialRow> rows) {
    Map<String, Double> out = new HashMap<>();
    for (MvpDomain.MaterialRow row : rows) {
      out.put(row.date + "#" + row.shiftCode + "#" + row.productCode + "#" + row.processCode, row.availableQty);
    }
    return out;
  }

  static Map<String, Double> totalMaterialByProductProcess(List<MvpDomain.MaterialRow> rows) {
    Map<String, Double> out = new HashMap<>();
    for (MvpDomain.MaterialRow row : rows) {
      out.merge(row.productCode + "#" + row.processCode, row.availableQty, Double::sum);
    }
    return out;
  }

  static Map<String, Double> snapshotProduced(Map<String, MvpDomain.ScheduleTask> tasks) {
    Map<String, Double> out = new HashMap<>();
    for (MvpDomain.ScheduleTask task : tasks.values()) {
      out.put(task.taskKey, task.producedQty);
    }
    return out;
  }
}

