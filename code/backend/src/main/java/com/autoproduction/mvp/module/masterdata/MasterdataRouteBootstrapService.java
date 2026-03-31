package com.autoproduction.mvp.module.masterdata;

import com.autoproduction.mvp.core.MvpStoreService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class MasterdataRouteBootstrapService {
  static final String TARGET_PRODUCT_CODE = "YXN.044.02.1020";
  static final int TARGET_WORKSHOP_COUNT = 4;
  static final int TARGET_LINES_PER_WORKSHOP = 4;

  private static final Logger log = LoggerFactory.getLogger(MasterdataRouteBootstrapService.class);
  private static final String REQUEST_ID = "bootstrap-masterdata-route-yxn-044-02-1020";
  private static final String OPERATOR = "system-bootstrap";
  private static final String TOPOLOGY_REQUEST_SUFFIX = "-topology";
  private static final List<Integer> CAPACITY_PER_SHIFT_PATTERN = List.of(300, 350, 400, 450, 500);
  private static final List<String> TARGET_PROCESS_CODES = List.of(
    "Z470",
    "Z3910",
    "Z3920",
    "Z390",
    "Z340",
    "Z410",
    "Z460",
    "Z420",
    "Z320",
    "Z310",
    "Z350",
    "Z480",
    "Z370",
    "Z380",
    "Z290",
    "Z2824",
    "Z4810",
    "Z440",
    "Z300",
    "Z450",
    "Z360",
    "Z270",
    "Z490",
    "Z500",
    "Z4740",
    "W130",
    "W140",
    "W160",
    "W150",
    "W030"
  );

  private final MvpStoreService store;
  private final MasterdataConfigCommandService masterdataConfigCommandService;
  private final MasterdataRouteCommandService masterdataRouteCommandService;

  public MasterdataRouteBootstrapService(
    MvpStoreService store,
    MasterdataConfigCommandService masterdataConfigCommandService,
    MasterdataRouteCommandService masterdataRouteCommandService
  ) {
    this.store = store;
    this.masterdataConfigCommandService = masterdataConfigCommandService;
    this.masterdataRouteCommandService = masterdataRouteCommandService;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void ensureTargetRoutePresent() {
    Map<String, Object> config = store.getMasterdataConfig(REQUEST_ID);
    List<Map<String, Object>> routeRows = rows(config.get("process_routes"));
    List<Map<String, Object>> currentRoute = routeRows.stream()
      .filter(row -> TARGET_PRODUCT_CODE.equals(normalize(row.get("product_code"))))
      .sorted(Comparator.comparingDouble(row -> number(row.get("sequence_no"))))
      .toList();

    if (!matchesTargetRoute(currentRoute)) {
      List<Map<String, Object>> processConfigs = new ArrayList<>(rows(config.get("process_configs")));
      Set<String> availableProcessCodes = new LinkedHashSet<>();
      for (Map<String, Object> row : processConfigs) {
        String processCode = normalize(row.get("process_code"));
        if (!processCode.isBlank()) {
          availableProcessCodes.add(processCode);
        }
      }

      List<String> missingProcessCodes = TARGET_PROCESS_CODES.stream()
        .filter(code -> !availableProcessCodes.contains(code))
        .toList();
      if (!missingProcessCodes.isEmpty()) {
        ensureRequiredProcessConfigs(processConfigs, availableProcessCodes, missingProcessCodes);
      }

      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("product_code", TARGET_PRODUCT_CODE);
      payload.put("steps", targetStepsPayload());

      if (currentRoute.isEmpty()) {
        masterdataRouteCommandService.createMasterdataRoute(payload, REQUEST_ID, OPERATOR);
        log.info("Bootstrapped missing process route for {}.", TARGET_PRODUCT_CODE);
      } else {
        masterdataRouteCommandService.updateMasterdataRoute(payload, REQUEST_ID, OPERATOR);
        log.info("Updated process route for {} to match bootstrap definition.", TARGET_PRODUCT_CODE);
      }
    }

    ensureTargetLineTopology(config);
  }

  private void ensureRequiredProcessConfigs(
    List<Map<String, Object>> processConfigs,
    Set<String> availableProcessCodes,
    List<String> missingProcessCodes
  ) {
    for (String processCode : missingProcessCodes) {
      if (!availableProcessCodes.add(processCode)) {
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("process_code", processCode);
      row.put("capacity_per_shift", 900);
      row.put("required_workers", 1);
      row.put("required_machines", 1);
      processConfigs.add(row);
    }

    masterdataConfigCommandService.saveMasterdataConfig(
      Map.of("process_configs", processConfigs),
      REQUEST_ID + "-config",
      OPERATOR
    );
    log.info("Bootstrapped process configs for {}: {}", TARGET_PRODUCT_CODE, missingProcessCodes);
  }

  private static List<Map<String, Object>> targetStepsPayload() {
    List<Map<String, Object>> steps = new ArrayList<>();
    for (int index = 0; index < TARGET_PROCESS_CODES.size(); index += 1) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("process_code", TARGET_PROCESS_CODES.get(index));
      row.put("dependency_type", "FS");
      row.put("sequence_no", index + 1);
      steps.add(row);
    }
    return steps;
  }

  private void ensureTargetLineTopology(Map<String, Object> config) {
    List<Map<String, Object>> currentLineTopology = rows(config.get("line_topology"));
    List<Map<String, Object>> targetLineTopology = targetLineTopologyRows();
    if (matchesTargetLineTopology(currentLineTopology, targetLineTopology)) {
      return;
    }
    masterdataConfigCommandService.saveMasterdataConfig(
      Map.of("line_topology", targetLineTopology),
      REQUEST_ID + TOPOLOGY_REQUEST_SUFFIX,
      OPERATOR
    );
    log.info(
      "Rebuilt line topology for {} with {} workshops x {} lines.",
      TARGET_PRODUCT_CODE,
      TARGET_WORKSHOP_COUNT,
      TARGET_LINES_PER_WORKSHOP
    );
  }

  private static List<Map<String, Object>> targetLineTopologyRows() {
    List<Map<String, Object>> rows = new ArrayList<>();
    int channelCount = TARGET_WORKSHOP_COUNT * TARGET_LINES_PER_WORKSHOP;
    for (int index = 0; index < TARGET_PROCESS_CODES.size(); index += 1) {
      int channelIndex = index % channelCount;
      int workshopSeq = (channelIndex / TARGET_LINES_PER_WORKSHOP) + 1;
      int lineSeq = (channelIndex % TARGET_LINES_PER_WORKSHOP) + 1;
      String workshopCode = String.format(Locale.ROOT, "WS-%02d", workshopSeq);
      String lineCode = String.format(Locale.ROOT, "%s-LINE-%02d", workshopCode, lineSeq);
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("company_code", "COMPANY-MAIN");
      row.put("workshop_code", workshopCode);
      row.put("workshop_name", "\u8f66\u95f4" + workshopSeq);
      row.put("line_code", lineCode);
      row.put("line_name", "\u4ea7\u7ebf" + lineSeq);
      row.put("process_code", TARGET_PROCESS_CODES.get(index));
      row.put("capacity_per_shift", CAPACITY_PER_SHIFT_PATTERN.get(index % CAPACITY_PER_SHIFT_PATTERN.size()));
      row.put("required_workers", 1);
      row.put("required_machines", 1);
      row.put("enabled_flag", 1);
      rows.add(row);
    }
    return rows;
  }

  private static boolean matchesTargetLineTopology(
    List<Map<String, Object>> currentRows,
    List<Map<String, Object>> targetRows
  ) {
    List<String> currentSignatures = normalizedLineTopologySignatures(currentRows);
    List<String> targetSignatures = normalizedLineTopologySignatures(targetRows);
    return currentSignatures.equals(targetSignatures);
  }

  private static List<String> normalizedLineTopologySignatures(List<Map<String, Object>> rows) {
    List<String> out = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      String companyCode = normalize(value(row, "company_code", "companyCode"));
      String workshopCode = normalize(value(row, "workshop_code", "workshopCode"));
      String workshopName = text(value(row, "workshop_name", "workshopName"));
      String lineCode = normalize(value(row, "line_code", "lineCode"));
      String lineName = text(value(row, "line_name", "lineName"));
      String processCode = normalize(value(row, "process_code", "processCode"));
      String capacity = String.format(Locale.ROOT, "%.2f", number(value(row, "capacity_per_shift", "capacityPerShift")));
      int requiredWorkers = (int) Math.round(number(value(row, "required_workers", "requiredWorkers")));
      int requiredMachines = (int) Math.round(number(value(row, "required_machines", "requiredMachines")));
      int enabledFlag = number(value(row, "enabled_flag", "enabledFlag")) > 0d ? 1 : 0;
      out.add(String.join(
        "#",
        companyCode,
        workshopCode,
        workshopName,
        lineCode,
        lineName,
        processCode,
        capacity,
        String.valueOf(requiredWorkers),
        String.valueOf(requiredMachines),
        String.valueOf(enabledFlag)
      ));
    }
    out.sort(String::compareTo);
    return out;
  }

  private static Object value(Map<String, Object> row, String... keys) {
    if (row == null || keys == null) {
      return null;
    }
    for (String key : keys) {
      if (key == null || key.isBlank()) {
        continue;
      }
      if (row.containsKey(key)) {
        return row.get(key);
      }
    }
    return null;
  }

  private static boolean matchesTargetRoute(List<Map<String, Object>> currentRoute) {
    if (currentRoute.size() != TARGET_PROCESS_CODES.size()) {
      return false;
    }
    for (int index = 0; index < TARGET_PROCESS_CODES.size(); index += 1) {
      Map<String, Object> row = currentRoute.get(index);
      if (!TARGET_PROCESS_CODES.get(index).equals(normalize(row.get("process_code")))) {
        return false;
      }
      if (!"FS".equals(normalize(row.get("dependency_type")))) {
        return false;
      }
      if (Math.abs(number(row.get("sequence_no")) - (index + 1)) > 1e-9) {
        return false;
      }
    }
    return true;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> rows(Object raw) {
    if (raw instanceof List<?> list) {
      List<Map<String, Object>> result = new ArrayList<>();
      for (Object item : list) {
        if (item instanceof Map<?, ?> map) {
          result.add((Map<String, Object>) map);
        }
      }
      return result;
    }
    return List.of();
  }

  private static String normalize(Object value) {
    return value == null ? "" : String.valueOf(value).trim().toUpperCase(Locale.ROOT);
  }

  private static String text(Object value) {
    return value == null ? "" : String.valueOf(value).trim();
  }

  private static double number(Object value) {
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    if (value == null) {
      return 0d;
    }
    return Double.parseDouble(String.valueOf(value));
  }
}
