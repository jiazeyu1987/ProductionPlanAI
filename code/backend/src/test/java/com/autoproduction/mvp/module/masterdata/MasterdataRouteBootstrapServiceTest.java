package com.autoproduction.mvp.module.masterdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autoproduction.mvp.core.MvpStoreService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MasterdataRouteBootstrapServiceTest {
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

  @Test
  @SuppressWarnings("unchecked")
  void shouldSeedConfigsAndCreateRouteWhenMasterdataIsEmpty() {
    MvpStoreService store = mock(MvpStoreService.class);
    MasterdataConfigCommandService configCommandService = mock(MasterdataConfigCommandService.class);
    MasterdataRouteCommandService routeCommandService = mock(MasterdataRouteCommandService.class);
    MasterdataRouteBootstrapService service = new MasterdataRouteBootstrapService(
      store,
      configCommandService,
      routeCommandService
    );

    when(store.getMasterdataConfig("bootstrap-masterdata-route-yxn-044-02-1020")).thenReturn(Map.of(
      "process_configs", List.of(),
      "process_routes", List.of(),
      "line_topology", List.of()
    ));

    service.ensureTargetRoutePresent();

    ArgumentCaptor<Map<String, Object>> configPayloadCaptor = ArgumentCaptor.forClass(Map.class);
    verify(configCommandService).saveMasterdataConfig(
      configPayloadCaptor.capture(),
      eq("bootstrap-masterdata-route-yxn-044-02-1020-config"),
      eq("system-bootstrap")
    );
    List<Map<String, Object>> processConfigs =
      (List<Map<String, Object>>) configPayloadCaptor.getValue().get("process_configs");
    assertEquals(30, processConfigs.size());
    assertEquals("Z470", processConfigs.get(0).get("process_code"));
    assertEquals("W030", processConfigs.get(29).get("process_code"));

    ArgumentCaptor<Map<String, Object>> routePayloadCaptor = ArgumentCaptor.forClass(Map.class);
    verify(routeCommandService).createMasterdataRoute(
      routePayloadCaptor.capture(),
      eq("bootstrap-masterdata-route-yxn-044-02-1020"),
      eq("system-bootstrap")
    );
    List<Map<String, Object>> steps = (List<Map<String, Object>>) routePayloadCaptor.getValue().get("steps");
    assertEquals(30, steps.size());
    assertEquals("YXN.044.02.1020", routePayloadCaptor.getValue().get("product_code"));
    assertEquals("W030", steps.get(29).get("process_code"));

    ArgumentCaptor<Map<String, Object>> topologyPayloadCaptor = ArgumentCaptor.forClass(Map.class);
    verify(configCommandService).saveMasterdataConfig(
      topologyPayloadCaptor.capture(),
      eq("bootstrap-masterdata-route-yxn-044-02-1020-topology"),
      eq("system-bootstrap")
    );
    List<Map<String, Object>> lineTopology =
      (List<Map<String, Object>>) topologyPayloadCaptor.getValue().get("line_topology");
    assertEquals(30, lineTopology.size());
    Set<String> workshopCodes = new LinkedHashSet<>();
    Set<String> processCodes = new LinkedHashSet<>();
    for (Map<String, Object> row : lineTopology) {
      workshopCodes.add(String.valueOf(row.get("workshop_code")));
      processCodes.add(String.valueOf(row.get("process_code")));
      assertEquals(1, row.get("required_workers"));
      assertEquals(1, row.get("required_machines"));
      double capacityPerShift = Double.parseDouble(String.valueOf(row.get("capacity_per_shift")));
      assertTrue(capacityPerShift >= 300d && capacityPerShift <= 500d);
    }
    assertEquals(4, workshopCodes.size());
    assertEquals(new LinkedHashSet<>(TARGET_PROCESS_CODES), processCodes);
  }

  @Test
  void shouldDoNothingWhenTargetRouteAlreadyMatches() {
    MvpStoreService store = mock(MvpStoreService.class);
    MasterdataConfigCommandService configCommandService = mock(MasterdataConfigCommandService.class);
    MasterdataRouteCommandService routeCommandService = mock(MasterdataRouteCommandService.class);
    MasterdataRouteBootstrapService service = new MasterdataRouteBootstrapService(
      store,
      configCommandService,
      routeCommandService
    );

    when(store.getMasterdataConfig("bootstrap-masterdata-route-yxn-044-02-1020")).thenReturn(Map.of(
      "process_configs", List.of(Map.of("process_code", "Z470")),
      "process_routes", targetRouteRows(),
      "line_topology", targetLineTopologyRows()
    ));

    service.ensureTargetRoutePresent();

    verify(configCommandService, never()).saveMasterdataConfig(anyMap(), anyString(), anyString());
    verify(routeCommandService, never()).createMasterdataRoute(anyMap(), anyString(), anyString());
    verify(routeCommandService, never()).updateMasterdataRoute(anyMap(), anyString(), anyString());
  }

  @Test
  void shouldUpdateRouteWhenTargetProductExistsButStepsDiffer() {
    MvpStoreService store = mock(MvpStoreService.class);
    MasterdataConfigCommandService configCommandService = mock(MasterdataConfigCommandService.class);
    MasterdataRouteCommandService routeCommandService = mock(MasterdataRouteCommandService.class);
    MasterdataRouteBootstrapService service = new MasterdataRouteBootstrapService(
      store,
      configCommandService,
      routeCommandService
    );

    when(store.getMasterdataConfig("bootstrap-masterdata-route-yxn-044-02-1020")).thenReturn(Map.of(
      "process_configs",
      targetProcessConfigRows(),
      "process_routes",
      List.of(
        Map.of("product_code", "YXN.044.02.1020", "process_code", "Z470", "sequence_no", 1, "dependency_type", "FS"),
        Map.of("product_code", "YXN.044.02.1020", "process_code", "Z3910", "sequence_no", 2, "dependency_type", "FS")
      ),
      "line_topology", targetLineTopologyRows()
    ));

    service.ensureTargetRoutePresent();

    verify(configCommandService, never()).saveMasterdataConfig(anyMap(), anyString(), anyString());
    verify(routeCommandService).updateMasterdataRoute(
      anyMap(),
      eq("bootstrap-masterdata-route-yxn-044-02-1020"),
      eq("system-bootstrap")
    );
  }

  @Test
  void shouldAddMissingProcessConfigsBeforeCreatingRoute() {
    MvpStoreService store = mock(MvpStoreService.class);
    MasterdataConfigCommandService configCommandService = mock(MasterdataConfigCommandService.class);
    MasterdataRouteCommandService routeCommandService = mock(MasterdataRouteCommandService.class);
    MasterdataRouteBootstrapService service = new MasterdataRouteBootstrapService(
      store,
      configCommandService,
      routeCommandService
    );

    when(store.getMasterdataConfig("bootstrap-masterdata-route-yxn-044-02-1020")).thenReturn(Map.of(
      "process_configs",
      List.of(Map.of("process_code", "Z470")),
      "process_routes",
      List.of(Map.of("product_code", "A006.034.10191", "process_code", "W160", "sequence_no", 1)),
      "line_topology", List.of()
    ));

    service.ensureTargetRoutePresent();

    verify(configCommandService).saveMasterdataConfig(
      anyMap(),
      eq("bootstrap-masterdata-route-yxn-044-02-1020-config"),
      eq("system-bootstrap")
    );
    verify(routeCommandService).createMasterdataRoute(
      anyMap(),
      eq("bootstrap-masterdata-route-yxn-044-02-1020"),
      eq("system-bootstrap")
    );
    verify(configCommandService).saveMasterdataConfig(
      anyMap(),
      eq("bootstrap-masterdata-route-yxn-044-02-1020-topology"),
      eq("system-bootstrap")
    );
  }

  @Test
  void shouldRebuildTopologyWhenRouteMatchesButTopologyDiffers() {
    MvpStoreService store = mock(MvpStoreService.class);
    MasterdataConfigCommandService configCommandService = mock(MasterdataConfigCommandService.class);
    MasterdataRouteCommandService routeCommandService = mock(MasterdataRouteCommandService.class);
    MasterdataRouteBootstrapService service = new MasterdataRouteBootstrapService(
      store,
      configCommandService,
      routeCommandService
    );

    when(store.getMasterdataConfig("bootstrap-masterdata-route-yxn-044-02-1020")).thenReturn(Map.of(
      "process_configs", targetProcessConfigRows(),
      "process_routes", targetRouteRows(),
      "line_topology",
      List.of(
        Map.of(
          "company_code", "COMPANY-MAIN",
          "workshop_code", "WS-LEGACY",
          "workshop_name", "旧车间",
          "line_code", "WS-LEGACY-LINE-01",
          "line_name", "旧产线",
          "process_code", "Z470",
          "capacity_per_shift", 900,
          "required_workers", 2,
          "required_machines", 2,
          "enabled_flag", 1
        )
      )
    ));

    service.ensureTargetRoutePresent();

    verify(routeCommandService, never()).createMasterdataRoute(anyMap(), anyString(), anyString());
    verify(routeCommandService, never()).updateMasterdataRoute(anyMap(), anyString(), anyString());
    verify(configCommandService).saveMasterdataConfig(
      anyMap(),
      eq("bootstrap-masterdata-route-yxn-044-02-1020-topology"),
      eq("system-bootstrap")
    );
  }

  private static List<Map<String, Object>> targetRouteRows() {
    List<Map<String, Object>> rows = new ArrayList<>();
    for (int index = 0; index < TARGET_PROCESS_CODES.size(); index += 1) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("product_code", "YXN.044.02.1020");
      row.put("process_code", TARGET_PROCESS_CODES.get(index));
      row.put("sequence_no", index + 1);
      row.put("dependency_type", "FS");
      rows.add(row);
    }
    return rows;
  }

  private static List<Map<String, Object>> targetProcessConfigRows() {
    List<Map<String, Object>> rows = new ArrayList<>();
    for (String processCode : TARGET_PROCESS_CODES) {
      rows.add(Map.of(
        "process_code", processCode,
        "capacity_per_shift", 900,
        "required_workers", 1,
        "required_machines", 1
      ));
    }
    return rows;
  }

  private static List<Map<String, Object>> targetLineTopologyRows() {
    List<Map<String, Object>> rows = new ArrayList<>();
    List<Integer> capacityPattern = List.of(300, 350, 400, 450, 500);
    int workshopCount = 4;
    int linesPerWorkshop = 4;
    int channelCount = workshopCount * linesPerWorkshop;
    for (int index = 0; index < TARGET_PROCESS_CODES.size(); index += 1) {
      int channelIndex = index % channelCount;
      int workshopSeq = (channelIndex / linesPerWorkshop) + 1;
      int lineSeq = (channelIndex % linesPerWorkshop) + 1;
      String workshopCode = String.format(Locale.ROOT, "WS-%02d", workshopSeq);
      String lineCode = String.format(Locale.ROOT, "%s-LINE-%02d", workshopCode, lineSeq);
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("company_code", "COMPANY-MAIN");
      row.put("workshop_code", workshopCode);
      row.put("workshop_name", "\u8f66\u95f4" + workshopSeq);
      row.put("line_code", lineCode);
      row.put("line_name", "\u4ea7\u7ebf" + lineSeq);
      row.put("process_code", TARGET_PROCESS_CODES.get(index));
      row.put("capacity_per_shift", capacityPattern.get(index % capacityPattern.size()));
      row.put("required_workers", 1);
      row.put("required_machines", 1);
      row.put("enabled_flag", 1);
      rows.add(row);
    }
    return rows;
  }
}
