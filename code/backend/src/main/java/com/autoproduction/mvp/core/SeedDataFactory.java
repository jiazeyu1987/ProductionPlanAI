package com.autoproduction.mvp.core;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SeedDataFactory {

  private static final String PROD_ANGIO_CATH = "PROD_ANGIO_CATH";
  private static final String PROD_YXN_067_005_1006 = "YXN.067.005.1006";
  private static final String PROD_A006_034_10191 = "A006.034.10191";
  private static final String PROD_YXN_009_020_1047 = "YXN.009.020.1047";
  private static final String PROD_YXN_044_02_1028 = "YXN.044.02.1028";

  private static final List<String> ANGIO_CATH_PROCESS_CODES = List.of(
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
    "Z450",
    "Z360",
    "Z270",
    "Z4830",
    "Z500",
    "W130",
    "W140",
    "W160",
    "W150",
    "W030"
  );
  private static final List<String> YXN_067_005_1006_PROCESS_CODES = List.of(
    "W150",
    "W160",
    "W130",
    "W140",
    "W030"
  );
  private static final List<String> A006_034_10191_PROCESS_CODES = List.of(
    "W160",
    "W030"
  );
  private static final List<String> YXN_009_020_1047_PROCESS_CODES = List.of(
    "W030",
    "W130",
    "W140",
    "W150",
    "W160"
  );
  private static final List<String> YXN_044_02_1028_PROCESS_CODES = List.of(
    "W130",
    "W140",
    "W160",
    "W150",
    "W030"
  );

  private SeedDataFactory() {}

  static MvpDomain.State build() {
    MvpDomain.State state = new MvpDomain.State();
    state.startDate = LocalDate.of(2026, 3, 22);
    state.horizonDays = 4;
    state.shiftsPerDay = 2;
    state.shiftHours = 12;
    state.skipStatutoryHolidays = false;
    state.weekendRestMode = "DOUBLE";
    state.dateShiftModeByDate = new HashMap<>();
    state.strictRoute = true;

    List<MvpDomain.ProcessConfig> processConfigs = new ArrayList<>();
    processConfigs.add(new MvpDomain.ProcessConfig("PROC_TUBE", 1000d, 2, 1));
    processConfigs.add(new MvpDomain.ProcessConfig("PROC_ASSEMBLY", 900d, 2, 1));
    processConfigs.add(new MvpDomain.ProcessConfig("PROC_BALLOON", 850d, 2, 1));
    processConfigs.add(new MvpDomain.ProcessConfig("PROC_STENT", 700d, 3, 1));
    processConfigs.add(new MvpDomain.ProcessConfig("PROC_STERILE", 1200d, 1, 1));
    processConfigs.addAll(buildAngioCathProcessConfigs());
    state.processes = processConfigs;

    state.processRoutes = buildDefaultProcessRoutes();

    state.lineProcessBindings = List.of(
      new MvpDomain.LineProcessBinding("COMPANY-MAIN", "WS-PRODUCTION", "LINE-CATH-1", "导管一线", "PROC_TUBE", true, 1000d, 2, 1),
      new MvpDomain.LineProcessBinding("COMPANY-MAIN", "WS-PRODUCTION", "LINE-CATH-1", "导管一线", "PROC_ASSEMBLY", true, 900d, 2, 1),
      new MvpDomain.LineProcessBinding("COMPANY-MAIN", "WS-PRODUCTION", "LINE-BALLOON-1", "球囊一线", "PROC_BALLOON", true, 850d, 2, 1),
      new MvpDomain.LineProcessBinding("COMPANY-MAIN", "WS-PRODUCTION", "LINE-BALLOON-1", "球囊一线", "PROC_ASSEMBLY", true, 900d, 2, 1),
      new MvpDomain.LineProcessBinding("COMPANY-MAIN", "WS-PRODUCTION", "LINE-STENT-1", "支架一线", "PROC_STENT", true, 700d, 3, 1),
      new MvpDomain.LineProcessBinding("COMPANY-MAIN", "WS-STERILE", "LINE-STERILE-1", "灭菌一线", "PROC_STERILE", true, 1200d, 1, 1)
    );
    state.shiftCalendar = buildShiftCalendar(state.startDate, state.horizonDays, state.shiftsPerDay);
    state.workerPools = buildResource(
      state.startDate,
      state.horizonDays,
      state.shiftsPerDay,
      buildDefaultResourceValues(
        state.processes,
        Map.of(
          "PROC_TUBE", 8,
          "PROC_ASSEMBLY", 10,
          "PROC_BALLOON", 8,
          "PROC_STENT", 9,
          "PROC_STERILE", 6
        ),
        6
      )
    );
    state.machinePools = buildResource(
      state.startDate,
      state.horizonDays,
      state.shiftsPerDay,
      buildDefaultResourceValues(
        state.processes,
        Map.of(
          "PROC_TUBE", 3,
          "PROC_ASSEMBLY", 3,
          "PROC_BALLOON", 2,
          "PROC_STENT", 2,
          "PROC_STERILE", 2
        ),
        2
      )
    );
    state.materialAvailability = buildMaterials(state.startDate, state.horizonDays, state.shiftsPerDay);

    state.orders = new ArrayList<>();
    state.orders.add(new MvpDomain.Order(
      "MO-CATH-001",
      "production",
      LocalDate.of(2026, 3, 24),
      state.startDate,
      true,
      false,
      false,
      "OPEN",
      List.of(new MvpDomain.OrderItem("PROD_CATH", 300d, 0d)),
      createBusinessData(
        LocalDate.of(2026, 3, 22),
        LocalDate.of(2026, 3, 24),
        "鍐呰锤澶囪揣",
        "瀵肩",
        "5F COBRA2 100cm",
        "K2026032201",
        "纸塑袋",
        "YXN.039.002.1002",
        "3.24",
        "周计划（3.16-3.22）",
        "MVP鍩哄噯鏍蜂緥",
        300d,
        "SF-CATH-001"
      )
    ));
    state.orders.add(new MvpDomain.Order(
      "MO-BALLOON-001",
      "production",
      LocalDate.of(2026, 3, 25),
      state.startDate,
      false,
      false,
      false,
      "OPEN",
      List.of(new MvpDomain.OrderItem("PROD_BALLOON", 1000d, 0d)),
      createBusinessData(
        LocalDate.of(2026, 3, 22),
        LocalDate.of(2026, 3, 25),
        "鍐呰锤澶囪揣",
        "鐞冨泭",
        "S020015-4 2.0mmx15mm",
        "K2026032202",
        "纸塑袋",
        "YXN.037.011.1007",
        "3.25",
        "周计划（3.16-3.22）",
        "MVP鍩哄噯鏍蜂緥",
        1000d,
        "SF-BALLOON-001"
      )
    ));
    state.orders.add(new MvpDomain.Order(
      "MO-STENT-001",
      "production",
      LocalDate.of(2026, 3, 26),
      state.startDate,
      false,
      false,
      false,
      "OPEN",
      List.of(new MvpDomain.OrderItem("PROD_STENT", 2000d, 0d)),
      createBusinessData(
        LocalDate.of(2026, 3, 22),
        LocalDate.of(2026, 3, 26),
        "研发型检样品单",
        "鏀灦",
        "SC50040140 5.0mmx40mm",
        "K2026032203",
        "硬吸塑",
        "YXN.074.002.5001",
        "3.26",
        "周计划（3.16-3.22）",
        "浼樺厛鎺掍骇",
        2000d,
        "SF-STENT-001"
      )
    ));
    return state;
  }

  private static List<MvpDomain.ProcessConfig> buildAngioCathProcessConfigs() {
    List<MvpDomain.ProcessConfig> rows = new ArrayList<>();
    for (String processCode : ANGIO_CATH_PROCESS_CODES) {
      double capacityPerShift = processCode.startsWith("W") ? 1200d : 900d;
      rows.add(new MvpDomain.ProcessConfig(processCode, capacityPerShift, 1, 1));
    }
    return rows;
  }

  private static Map<String, List<MvpDomain.ProcessStep>> buildDefaultProcessRoutes() {
    Map<String, List<MvpDomain.ProcessStep>> routes = new LinkedHashMap<>();
    routes.put("PROD_CATH", List.of(
      new MvpDomain.ProcessStep("PROC_TUBE", "FS"),
      new MvpDomain.ProcessStep("PROC_ASSEMBLY", "FS"),
      new MvpDomain.ProcessStep("PROC_STERILE", "FS")
    ));
    routes.put("PROD_BALLOON", List.of(
      new MvpDomain.ProcessStep("PROC_BALLOON", "FS"),
      new MvpDomain.ProcessStep("PROC_ASSEMBLY", "FS"),
      new MvpDomain.ProcessStep("PROC_STERILE", "FS")
    ));
    routes.put("PROD_STENT", List.of(
      new MvpDomain.ProcessStep("PROC_STENT", "FS"),
      new MvpDomain.ProcessStep("PROC_STERILE", "FS")
    ));
    routes.put(PROD_ANGIO_CATH, buildFsRoute(ANGIO_CATH_PROCESS_CODES));
    routes.put(PROD_YXN_067_005_1006, buildFsRoute(YXN_067_005_1006_PROCESS_CODES));
    routes.put(PROD_A006_034_10191, buildFsRoute(A006_034_10191_PROCESS_CODES));
    routes.put(PROD_YXN_009_020_1047, buildFsRoute(YXN_009_020_1047_PROCESS_CODES));
    routes.put(PROD_YXN_044_02_1028, buildFsRoute(YXN_044_02_1028_PROCESS_CODES));
    return routes;
  }

  private static List<MvpDomain.ProcessStep> buildFsRoute(List<String> processCodes) {
    List<MvpDomain.ProcessStep> steps = new ArrayList<>();
    for (String processCode : processCodes) {
      steps.add(new MvpDomain.ProcessStep(processCode, "FS"));
    }
    return steps;
  }

  private static Map<String, Integer> buildDefaultResourceValues(
    List<MvpDomain.ProcessConfig> processConfigs,
    Map<String, Integer> preferred,
    int fallback
  ) {
    Map<String, Integer> out = new LinkedHashMap<>(preferred);
    for (MvpDomain.ProcessConfig config : processConfigs) {
      out.putIfAbsent(config.processCode, fallback);
    }
    return out;
  }

  private static MvpDomain.OrderBusinessData createBusinessData(
    LocalDate orderDate,
    LocalDate dueDate,
    String customerRemark,
    String productName,
    String specModel,
    String productionBatchNo,
    String packagingForm,
    String salesOrderNo,
    String workshopOuterPackagingDate,
    String weeklyMonthlyPlanRemark,
    String note,
    double marketDemand,
    String semiFinishedCode
  ) {
    MvpDomain.OrderBusinessData data = new MvpDomain.OrderBusinessData();
    data.orderDate = orderDate;
    data.customerRemark = customerRemark;
    data.productName = productName;
    data.specModel = specModel;
    data.productionBatchNo = productionBatchNo;
    data.packagingForm = packagingForm;
    data.salesOrderNo = salesOrderNo;
    data.productionDateForeignTrade = "";
    data.purchaseDueDate = "";
    data.injectionDueDate = "";
    data.marketRemarkInfo = weeklyMonthlyPlanRemark;
    data.marketDemand = marketDemand;
    data.plannedFinishDate1 = dueDate.toString();
    data.plannedFinishDate2 = dueDate.toString();
    data.semiFinishedCode = semiFinishedCode;
    data.semiFinishedInventory = Math.round(marketDemand * 0.15d);
    data.semiFinishedDemand = marketDemand;
    data.semiFinishedWip = Math.round(marketDemand * 0.2d);
    data.needOrderQty = Math.max(0d, data.semiFinishedDemand - data.semiFinishedInventory - data.semiFinishedWip);
    data.pendingInboundQty = Math.round(data.needOrderQty * 0.5d);
    data.weeklyMonthlyPlanRemark = weeklyMonthlyPlanRemark;
    data.workshopOuterPackagingDate = workshopOuterPackagingDate;
    data.note = note;
    data.workshopCompletedQty = 0d;
    data.workshopCompletedTime = "";
    data.outerCompletedQty = 0d;
    data.outerCompletedTime = "";
    data.matchStatus = "待匹配";
    return data;
  }

  private static List<MvpDomain.ShiftRow> buildShiftCalendar(LocalDate startDate, int horizonDays, int shiftsPerDay) {
    List<MvpDomain.ShiftRow> rows = new ArrayList<>();
    String[] shifts = {"D", "N"};
    for (int i = 0; i < horizonDays; i += 1) {
      LocalDate date = startDate.plusDays(i);
      for (int j = 0; j < shiftsPerDay; j += 1) {
        rows.add(new MvpDomain.ShiftRow(date, shifts[j], true));
      }
    }
    return rows;
  }

  private static List<MvpDomain.ResourceRow> buildResource(
    LocalDate startDate,
    int horizonDays,
    int shiftsPerDay,
    Map<String, Integer> values
  ) {
    List<MvpDomain.ResourceRow> rows = new ArrayList<>();
    String[] shifts = {"D", "N"};
    for (int i = 0; i < horizonDays; i += 1) {
      LocalDate date = startDate.plusDays(i);
      for (int j = 0; j < shiftsPerDay; j += 1) {
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
          rows.add(new MvpDomain.ResourceRow(date, shifts[j], entry.getKey(), entry.getValue()));
        }
      }
    }
    return rows;
  }

  private static List<MvpDomain.MaterialRow> buildMaterials(LocalDate startDate, int horizonDays, int shiftsPerDay) {
    List<MvpDomain.MaterialRow> rows = new ArrayList<>();
    String[] shifts = {"D", "N"};
    Map<String, List<String>> routes = Map.of(
      "PROD_CATH", List.of("PROC_TUBE", "PROC_ASSEMBLY", "PROC_STERILE"),
      "PROD_BALLOON", List.of("PROC_BALLOON", "PROC_ASSEMBLY", "PROC_STERILE"),
      "PROD_STENT", List.of("PROC_STENT", "PROC_STERILE"),
      PROD_ANGIO_CATH, ANGIO_CATH_PROCESS_CODES
    );
    for (int i = 0; i < horizonDays; i += 1) {
      LocalDate date = startDate.plusDays(i);
      for (int j = 0; j < shiftsPerDay; j += 1) {
        for (Map.Entry<String, List<String>> route : routes.entrySet()) {
          for (String processCode : route.getValue()) {
            rows.add(new MvpDomain.MaterialRow(date, shifts[j], route.getKey(), processCode, 5000d));
          }
        }
      }
    }
    return rows;
  }
}


