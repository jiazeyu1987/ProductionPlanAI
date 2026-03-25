package com.autoproduction.mvp.core;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class SeedDataFactory {

  private SeedDataFactory() {}

  static MvpDomain.State build() {
    MvpDomain.State state = new MvpDomain.State();
    state.startDate = LocalDate.of(2026, 3, 22);
    state.horizonDays = 4;
    state.shiftsPerDay = 2;
    state.shiftHours = 12;
    state.strictRoute = true;

    state.processes = List.of(
      new MvpDomain.ProcessConfig("PROC_TUBE", 1000d, 2, 1),
      new MvpDomain.ProcessConfig("PROC_ASSEMBLY", 900d, 2, 1),
      new MvpDomain.ProcessConfig("PROC_BALLOON", 850d, 2, 1),
      new MvpDomain.ProcessConfig("PROC_STENT", 700d, 3, 1),
      new MvpDomain.ProcessConfig("PROC_STERILE", 1200d, 1, 1)
    );

    state.processRoutes = Map.of(
      "PROD_CATH", List.of(
        new MvpDomain.ProcessStep("PROC_TUBE", "FS"),
        new MvpDomain.ProcessStep("PROC_ASSEMBLY", "FS"),
        new MvpDomain.ProcessStep("PROC_STERILE", "FS")
      ),
      "PROD_BALLOON", List.of(
        new MvpDomain.ProcessStep("PROC_BALLOON", "FS"),
        new MvpDomain.ProcessStep("PROC_ASSEMBLY", "FS"),
        new MvpDomain.ProcessStep("PROC_STERILE", "FS")
      ),
      "PROD_STENT", List.of(
        new MvpDomain.ProcessStep("PROC_STENT", "FS"),
        new MvpDomain.ProcessStep("PROC_STERILE", "FS")
      )
    );

    state.shiftCalendar = buildShiftCalendar(state.startDate, state.horizonDays, state.shiftsPerDay);
    state.workerPools = buildResource(state.startDate, state.horizonDays, state.shiftsPerDay, Map.of(
      "PROC_TUBE", 8, "PROC_ASSEMBLY", 10, "PROC_BALLOON", 8, "PROC_STENT", 9, "PROC_STERILE", 6
    ));
    state.machinePools = buildResource(state.startDate, state.horizonDays, state.shiftsPerDay, Map.of(
      "PROC_TUBE", 3, "PROC_ASSEMBLY", 3, "PROC_BALLOON", 2, "PROC_STENT", 2, "PROC_STERILE", 2
    ));
    state.materialAvailability = buildMaterials(state.startDate, state.horizonDays, state.shiftsPerDay);

    state.orders = new ArrayList<>();
    state.orders.add(new MvpDomain.Order(
      "MO-CATH-001", "production", LocalDate.of(2026, 3, 24),
      state.startDate,
      true, false, false, "OPEN",
      List.of(new MvpDomain.OrderItem("PROD_CATH", 300d, 0d)),
      createBusinessData(
        LocalDate.of(2026, 3, 22),
        LocalDate.of(2026, 3, 24),
        "内贸备货",
        "导管",
        "5F COBRA2 100cm",
        "K2026032201",
        "纸塑袋",
        "YXN.039.002.1002",
        "3.24",
        "周计划（3.16-3.22）",
        "MVP基准样例",
        300d,
        "SF-CATH-001"
      )
    ));
    state.orders.add(new MvpDomain.Order(
      "MO-BALLOON-001", "production", LocalDate.of(2026, 3, 25),
      state.startDate,
      false, false, false, "OPEN",
      List.of(new MvpDomain.OrderItem("PROD_BALLOON", 1000d, 0d)),
      createBusinessData(
        LocalDate.of(2026, 3, 22),
        LocalDate.of(2026, 3, 25),
        "内贸备货",
        "球囊",
        "S020015-4 2.0mmx15mm",
        "K2026032202",
        "纸塑袋",
        "YXN.037.011.1007",
        "3.25",
        "周计划（3.16-3.22）",
        "MVP基准样例",
        1000d,
        "SF-BALLOON-001"
      )
    ));
    state.orders.add(new MvpDomain.Order(
      "MO-STENT-001", "production", LocalDate.of(2026, 3, 26),
      state.startDate,
      false, false, false, "OPEN",
      List.of(new MvpDomain.OrderItem("PROD_STENT", 2000d, 0d)),
      createBusinessData(
        LocalDate.of(2026, 3, 22),
        LocalDate.of(2026, 3, 26),
        "研发型检样品单",
        "支架",
        "SC50040140 5.0mmx40mm",
        "K2026032203",
        "硬吸塑",
        "YXN.074.002.5001",
        "3.26",
        "周计划（3.16-3.22）",
        "优先排产",
        2000d,
        "SF-STENT-001"
      )
    ));
    return state;
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
      "PROD_STENT", List.of("PROC_STENT", "PROC_STERILE")
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
