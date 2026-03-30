package com.autoproduction.mvp.core;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class MvpDomainScheduleSupport {
  private MvpDomainScheduleSupport() {}
}

class MvpDomainScheduleTaskBase {
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

class MvpDomainAllocationBase {
  String taskKey;
  String orderNo;
  String productCode;
  String processCode;
  String companyCode;
  String workshopCode;
  String lineCode;
  String lineName;
  String dependencyType;
  String shiftId;
  String date;
  String shiftCode;
  double scheduledQty;
  int workersUsed;
  int machinesUsed;
  int groupsUsed;
}

class MvpDomainScheduleVersionBase {
  String requestId;
  String versionNo;
  OffsetDateTime generatedAt;
  int shiftHours;
  int shiftsPerDay;
  List<Map<String, Object>> shifts = new ArrayList<>();
  List<MvpDomain.ScheduleTask> tasks = new ArrayList<>();
  List<MvpDomain.Allocation> allocations = new ArrayList<>();
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

class MvpDomainReportingBase {
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

