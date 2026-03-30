package com.autoproduction.mvp.module.dispatchalert;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DispatchAlertFacade {
  private final DispatchCommandService dispatchCommandService;
  private final AlertQueryService alertQueryService;
  private final AlertCommandService alertCommandService;
  private final ReplanJobService replanJobService;
  private final SimulationQueryService simulationQueryService;
  private final SimulationRunCommandService simulationRunCommandService;
  private final SimulationManualCommandService simulationManualCommandService;
  private final AuditQueryService auditQueryService;

  public DispatchAlertFacade(
    DispatchCommandService dispatchCommandService,
    AlertQueryService alertQueryService,
    AlertCommandService alertCommandService,
    ReplanJobService replanJobService,
    SimulationQueryService simulationQueryService,
    SimulationRunCommandService simulationRunCommandService,
    SimulationManualCommandService simulationManualCommandService,
    AuditQueryService auditQueryService
  ) {
    this.dispatchCommandService = dispatchCommandService;
    this.alertQueryService = alertQueryService;
    this.alertCommandService = alertCommandService;
    this.replanJobService = replanJobService;
    this.simulationQueryService = simulationQueryService;
    this.simulationRunCommandService = simulationRunCommandService;
    this.simulationManualCommandService = simulationManualCommandService;
    this.auditQueryService = auditQueryService;
  }

  public List<Map<String, Object>> listDispatchCommands(Map<String, String> filters) {
    return dispatchCommandService.listDispatchCommands(filters);
  }

  public Map<String, Object> createDispatchCommand(Map<String, Object> payload, String requestId, String operator) {
    return dispatchCommandService.createDispatchCommand(payload, requestId, operator);
  }

  public Map<String, Object> approveDispatchCommand(String commandId, Map<String, Object> payload, String requestId, String operator) {
    return dispatchCommandService.approveDispatchCommand(commandId, payload, requestId, operator);
  }

  public List<Map<String, Object>> listAlerts(Map<String, String> filters) {
    return alertQueryService.listAlerts(filters);
  }

  public Map<String, Object> ackAlert(String alertId, Map<String, Object> payload, String requestId, String operator) {
    return alertCommandService.ackAlert(alertId, payload, requestId, operator);
  }

  public Map<String, Object> closeAlert(String alertId, Map<String, Object> payload, String requestId, String operator) {
    return alertCommandService.closeAlert(alertId, payload, requestId, operator);
  }

  public void triggerReplanJob(Map<String, Object> payload, String requestId, String operator) {
    replanJobService.triggerReplanJob(payload, requestId, operator);
  }

  public Map<String, Object> getReplanJob(String jobNo, String requestId) {
    return replanJobService.getReplanJob(jobNo, requestId);
  }

  public List<Map<String, Object>> listAuditLogs(Map<String, String> filters) {
    return auditQueryService.listAuditLogs(filters);
  }

  public Map<String, Object> getSimulationState(String requestId) {
    return simulationQueryService.getSimulationState(requestId);
  }

  public List<Map<String, Object>> listSimulationEvents(Map<String, String> filters) {
    return simulationQueryService.listSimulationEvents(filters);
  }

  public Map<String, Object> resetSimulation(Map<String, Object> payload, String requestId, String operator) {
    return simulationRunCommandService.resetSimulation(payload, requestId, operator);
  }

  public Map<String, Object> runSimulation(Map<String, Object> payload, String requestId, String operator) {
    return simulationRunCommandService.runSimulation(payload, requestId, operator);
  }

  public Map<String, Object> addManualProductionOrder(Map<String, Object> payload, String requestId, String operator) {
    return simulationManualCommandService.addManualProductionOrder(payload, requestId, operator);
  }

  public Map<String, Object> advanceManualOneDay(Map<String, Object> payload, String requestId, String operator) {
    return simulationManualCommandService.advanceManualOneDay(payload, requestId, operator);
  }

  public Map<String, Object> resetManualSimulation(Map<String, Object> payload, String requestId, String operator) {
    return simulationManualCommandService.resetManualSimulation(payload, requestId, operator);
  }
}
