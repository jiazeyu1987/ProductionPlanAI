package com.autoproduction.mvp.core;

import com.autoproduction.mvp.module.integration.erp.ErpDataManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
abstract class MvpStoreSimulationDomain extends MvpStoreDispatchDomain {
  protected MvpStoreSimulationDomain(ErpDataManager erpDataManager) {
    super(erpDataManager);
  }

  public Map<String, Object> getSimulationState(String requestId) {
    synchronized (lock) {
      alignSimulationDateToToday();
      return buildSimulationStateResponse(requestId);
    }
  }

  protected void alignSimulationDateToToday() {
    LocalDate today = LocalDate.now(SIMULATION_ZONE);
    if (simulationState.currentDate == null || simulationState.currentDate.isBefore(today)) {
      simulationState.currentDate = today;
      refreshOrderStatuses(today);
    }
  }

  public List<Map<String, Object>> listSimulationEvents(Map<String, String> filters) {
    synchronized (lock) {
      return simulationState.events.stream()
        .filter(row -> {
          if (filters == null) {
            return true;
          }
          if (filters.containsKey("event_type") && !Objects.equals(filters.get("event_type"), row.get("event_type"))) {
            return false;
          }
          if (filters.containsKey("event_date") && !Objects.equals(filters.get("event_date"), row.get("event_date"))) {
            return false;
          }
          return true;
        })
        .map(this::localizeRow)
        .toList();
    }
  }

  public Map<String, Object> resetSimulation(Map<String, Object> payload, String requestId, String operator) {
    synchronized (lock) {
      return runIdempotent(
        requestId,
        "SIM_RESET",
        () -> MvpStoreSimulationRunSupport.resetSimulation(this, payload, requestId, operator)
      );
    }
  }

  public Map<String, Object> runSimulation(Map<String, Object> payload, String requestId, String operator) {
    synchronized (lock) {
      return runIdempotent(
        requestId,
        "SIM_RUN",
        () -> MvpStoreSimulationRunSupport.runSimulation(this, payload, requestId, operator)
      );
    }
  }

  public Map<String, Object> addManualProductionOrder(Map<String, Object> payload, String requestId, String operator) {
    synchronized (lock) {
      return runIdempotent(
        requestId,
        "MANUAL_SIM_ADD_ORDER",
        () -> MvpStoreSimulationManualSupport.addManualProductionOrder(this, payload, requestId, operator)
      );
    }
  }

  public Map<String, Object> advanceManualOneDay(Map<String, Object> payload, String requestId, String operator) {
    synchronized (lock) {
      return runIdempotent(
        requestId,
        "MANUAL_SIM_ADVANCE_DAY",
        () -> MvpStoreSimulationManualSupport.advanceManualOneDay(this, payload, requestId, operator)
      );
    }
  }

  public Map<String, Object> resetManualSimulation(Map<String, Object> payload, String requestId, String operator) {
    synchronized (lock) {
      return runIdempotent(
        requestId,
        "MANUAL_SIM_RESET",
        () -> MvpStoreSimulationManualSupport.resetManualSimulation(this, payload, requestId, operator)
      );
    }
  }
}

