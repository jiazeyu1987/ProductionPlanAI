package com.autoproduction.mvp.core;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class MvpDomain {

  private MvpDomain() {}

  static final class State {
    LocalDate startDate;
    int horizonDays;
    int shiftsPerDay;
    int shiftHours;
    boolean strictRoute;
    List<ProcessConfig> processes = new ArrayList<>();
    Map<String, List<ProcessStep>> processRoutes = new HashMap<>();
    List<ShiftRow> shiftCalendar = new ArrayList<>();
    List<ResourceRow> workerPools = new ArrayList<>();
    List<ResourceRow> machinePools = new ArrayList<>();
    List<MaterialRow> materialAvailability = new ArrayList<>();
    List<Order> orders = new ArrayList<>();

    List<ScheduleVersion> schedules = new ArrayList<>();
    String publishedVersionNo;
    List<Reporting> reportings = new ArrayList<>();
    List<Map<String, Object>> scheduleResultWrites = new ArrayList<>();
    List<Map<String, Object>> scheduleStatusWrites = new ArrayList<>();
    List<Map<String, Object>> wipLots = new ArrayList<>();
    List<Map<String, Object>> wipLotEvents = new ArrayList<>();
    List<Map<String, Object>> replanJobs = new ArrayList<>();
    List<Map<String, Object>> alerts = new ArrayList<>();
    List<Map<String, Object>> auditLogs = new ArrayList<>();
    List<Map<String, Object>> dispatchCommands = new ArrayList<>();
    List<Map<String, Object>> dispatchApprovals = new ArrayList<>();
    List<Map<String, Object>> integrationInbox = new ArrayList<>();
    List<Map<String, Object>> integrationOutbox = new ArrayList<>();
    Map<String, Map<String, Object>> idempotencyLedger = new HashMap<>();
  }

  static final class OrderItem {
    String productCode;
    double qty;
    double completedQty;

    OrderItem(String productCode, double qty, double completedQty) {
      this.productCode = productCode;
      this.qty = qty;
      this.completedQty = completedQty;
    }
  }

  static final class Order {
    String orderNo;
    String orderType;
    LocalDate dueDate;
    boolean urgent;
    boolean frozen;
    boolean lockFlag;
    String status;
    List<OrderItem> items;
    OrderBusinessData businessData;

    Order(
      String orderNo,
      String orderType,
      LocalDate dueDate,
      boolean urgent,
      boolean frozen,
      boolean lockFlag,
      String status,
      List<OrderItem> items,
      OrderBusinessData businessData
    ) {
      this.orderNo = orderNo;
      this.orderType = orderType;
      this.dueDate = dueDate;
      this.urgent = urgent;
      this.frozen = frozen;
      this.lockFlag = lockFlag;
      this.status = status;
      this.items = new ArrayList<>(items);
      this.businessData = businessData == null ? new OrderBusinessData() : new OrderBusinessData(businessData);
    }
  }

  static final class OrderBusinessData {
    LocalDate orderDate;
    String customerRemark;
    String productName;
    String specModel;
    String productionBatchNo;
    String packagingForm;
    String salesOrderNo;
    String productionDateForeignTrade;
    String purchaseDueDate;
    String injectionDueDate;
    String marketRemarkInfo;
    double marketDemand;
    String plannedFinishDate1;
    String plannedFinishDate2;
    String semiFinishedCode;
    double semiFinishedInventory;
    double semiFinishedDemand;
    double semiFinishedWip;
    double needOrderQty;
    double pendingInboundQty;
    String weeklyMonthlyPlanRemark;
    String workshopOuterPackagingDate;
    String note;
    double workshopCompletedQty;
    String workshopCompletedTime;
    double outerCompletedQty;
    String outerCompletedTime;
    String matchStatus;

    OrderBusinessData() {
      this.orderDate = null;
      this.customerRemark = "";
      this.productName = "";
      this.specModel = "";
      this.productionBatchNo = "";
      this.packagingForm = "";
      this.salesOrderNo = "";
      this.productionDateForeignTrade = "";
      this.purchaseDueDate = "";
      this.injectionDueDate = "";
      this.marketRemarkInfo = "";
      this.marketDemand = 0d;
      this.plannedFinishDate1 = "";
      this.plannedFinishDate2 = "";
      this.semiFinishedCode = "";
      this.semiFinishedInventory = 0d;
      this.semiFinishedDemand = 0d;
      this.semiFinishedWip = 0d;
      this.needOrderQty = 0d;
      this.pendingInboundQty = 0d;
      this.weeklyMonthlyPlanRemark = "";
      this.workshopOuterPackagingDate = "";
      this.note = "";
      this.workshopCompletedQty = 0d;
      this.workshopCompletedTime = "";
      this.outerCompletedQty = 0d;
      this.outerCompletedTime = "";
      this.matchStatus = "";
    }

    OrderBusinessData(OrderBusinessData source) {
      this.orderDate = source.orderDate;
      this.customerRemark = source.customerRemark;
      this.productName = source.productName;
      this.specModel = source.specModel;
      this.productionBatchNo = source.productionBatchNo;
      this.packagingForm = source.packagingForm;
      this.salesOrderNo = source.salesOrderNo;
      this.productionDateForeignTrade = source.productionDateForeignTrade;
      this.purchaseDueDate = source.purchaseDueDate;
      this.injectionDueDate = source.injectionDueDate;
      this.marketRemarkInfo = source.marketRemarkInfo;
      this.marketDemand = source.marketDemand;
      this.plannedFinishDate1 = source.plannedFinishDate1;
      this.plannedFinishDate2 = source.plannedFinishDate2;
      this.semiFinishedCode = source.semiFinishedCode;
      this.semiFinishedInventory = source.semiFinishedInventory;
      this.semiFinishedDemand = source.semiFinishedDemand;
      this.semiFinishedWip = source.semiFinishedWip;
      this.needOrderQty = source.needOrderQty;
      this.pendingInboundQty = source.pendingInboundQty;
      this.weeklyMonthlyPlanRemark = source.weeklyMonthlyPlanRemark;
      this.workshopOuterPackagingDate = source.workshopOuterPackagingDate;
      this.note = source.note;
      this.workshopCompletedQty = source.workshopCompletedQty;
      this.workshopCompletedTime = source.workshopCompletedTime;
      this.outerCompletedQty = source.outerCompletedQty;
      this.outerCompletedTime = source.outerCompletedTime;
      this.matchStatus = source.matchStatus;
    }
  }

  static final class ProcessConfig {
    String processCode;
    double capacityPerShift;
    int requiredWorkers;
    int requiredMachines;

    ProcessConfig(String processCode, double capacityPerShift, int requiredWorkers, int requiredMachines) {
      this.processCode = processCode;
      this.capacityPerShift = capacityPerShift;
      this.requiredWorkers = requiredWorkers;
      this.requiredMachines = requiredMachines;
    }
  }

  static final class ProcessStep {
    String processCode;
    String dependencyType;

    ProcessStep(String processCode, String dependencyType) {
      this.processCode = processCode;
      this.dependencyType = dependencyType;
    }
  }

  static final class ShiftRow {
    LocalDate date;
    String shiftCode;
    boolean open;

    ShiftRow(LocalDate date, String shiftCode, boolean open) {
      this.date = date;
      this.shiftCode = shiftCode;
      this.open = open;
    }
  }

  static final class ResourceRow {
    LocalDate date;
    String shiftCode;
    String processCode;
    int available;

    ResourceRow(LocalDate date, String shiftCode, String processCode, int available) {
      this.date = date;
      this.shiftCode = shiftCode;
      this.processCode = processCode;
      this.available = available;
    }
  }

  static final class MaterialRow {
    LocalDate date;
    String shiftCode;
    String productCode;
    String processCode;
    double availableQty;

    MaterialRow(LocalDate date, String shiftCode, String productCode, String processCode, double availableQty) {
      this.date = date;
      this.shiftCode = shiftCode;
      this.productCode = productCode;
      this.processCode = processCode;
      this.availableQty = availableQty;
    }
  }

  static final class ScheduleTask {
    String taskKey;
    String orderNo;
    int itemIndex;
    int stepIndex;
    String productCode;
    String processCode;
    String dependencyType;
    String predecessorTaskKey;
    double targetQty;
    double producedQty;
    String dependencyStatus;
    String taskStatus;
    String lastBlockReason;
    String lastBlockReasonDetail;
    String lastBlockingDimension;
    Map<String, Object> lastBlockEvidence = new HashMap<>();
  }

  static final class Allocation {
    String taskKey;
    String orderNo;
    String productCode;
    String processCode;
    String dependencyType;
    String shiftId;
    String date;
    String shiftCode;
    double scheduledQty;
    int workersUsed;
    int machinesUsed;
    int groupsUsed;
  }

  static final class ScheduleVersion {
    String requestId;
    String versionNo;
    OffsetDateTime generatedAt;
    int shiftHours;
    int shiftsPerDay;
    List<Map<String, Object>> shifts = new ArrayList<>();
    List<ScheduleTask> tasks = new ArrayList<>();
    List<Allocation> allocations = new ArrayList<>();
    List<Map<String, Object>> unscheduled = new ArrayList<>();
    Map<String, Object> metrics = new HashMap<>();
    Map<String, Object> metadata = new HashMap<>();

    String status;
    String basedOnVersion;
    String ruleVersionNo;
    OffsetDateTime publishTime;
    String createdBy;
    OffsetDateTime createdAt;
    String rollbackFrom;
  }

  static final class Reporting {
    String reportingId;
    String requestId;
    String orderNo;
    String productCode;
    String processCode;
    double reportQty;
    OffsetDateTime reportTime;
    String triggeredReplanJobNo;
    String triggeredAlertId;
  }
}
