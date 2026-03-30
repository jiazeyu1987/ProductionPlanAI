package com.autoproduction.mvp.core;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class MvpDomainStateSupport {
  private MvpDomainStateSupport() {}
}

class MvpDomainStateBase {
  LocalDate startDate;
  int horizonDays;
  int shiftsPerDay;
  int shiftHours;
  boolean skipStatutoryHolidays;
  String weekendRestMode = "DOUBLE";
  Map<String, String> dateShiftModeByDate = new HashMap<>();
  boolean strictRoute;
  List<MvpDomain.ProcessConfig> processes = new ArrayList<>();
  Map<String, List<MvpDomain.ProcessStep>> processRoutes = new HashMap<>();
  List<MvpDomain.ShiftRow> shiftCalendar = new ArrayList<>();
  List<MvpDomain.ResourceRow> workerPools = new ArrayList<>();
  List<MvpDomain.ResourceRow> machinePools = new ArrayList<>();
  List<MvpDomain.ResourceRow> initialWorkerOccupancy = new ArrayList<>();
  List<MvpDomain.ResourceRow> initialMachineOccupancy = new ArrayList<>();
  List<MvpDomain.MaterialRow> materialAvailability = new ArrayList<>();
  List<MvpDomain.Order> orders = new ArrayList<>();
  List<MvpDomain.LineProcessBinding> lineProcessBindings = new ArrayList<>();

  List<MvpDomain.ScheduleVersion> schedules = new ArrayList<>();
  String publishedVersionNo;
  List<MvpDomain.Reporting> reportings = new ArrayList<>();
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

