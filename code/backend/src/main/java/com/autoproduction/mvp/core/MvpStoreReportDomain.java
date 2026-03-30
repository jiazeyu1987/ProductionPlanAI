package com.autoproduction.mvp.core;

import com.autoproduction.mvp.module.integration.erp.ErpDataManager;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
abstract class MvpStoreReportDomain extends MvpStoreSimulationDomain {
  protected MvpStoreReportDomain(ErpDataManager erpDataManager) {
    super(erpDataManager);
  }

  public Map<String, Object> recordReporting(Map<String, Object> payload, String requestId, String operator) {
    synchronized (lock) {
      return MvpStoreReportReportingSupport.recordReporting(this, payload, requestId, operator);
    }
  }

  public Map<String, Object> deleteReporting(String reportingId, String requestId, String operator) {
    synchronized (lock) {
      return MvpStoreReportReportingSupport.deleteReporting(this, reportingId, requestId, operator);
    }
  }

  public List<Map<String, Object>> listReportings() {
    synchronized (lock) {
      return MvpStoreReportReportingSupport.listReportings(this);
    }
  }

  public List<Map<String, Object>> listReportingsForMes(Map<String, String> filters) {
    synchronized (lock) {
      return MvpStoreReportReportingSupport.listReportingsForMes(this, filters);
    }
  }

  public Map<String, Object> writeScheduleResults(Map<String, Object> payload, String requestId, String operator) {
    synchronized (lock) {
      return runIdempotent(
        requestId,
        "WRITE_SCHEDULE_RESULTS",
        () -> MvpStoreReportScheduleWritebackSupport.writeScheduleResults(this, payload, requestId, operator)
      );
    }
  }

  public Map<String, Object> writeScheduleStatus(Map<String, Object> payload, String requestId, String operator) {
    synchronized (lock) {
      return runIdempotent(
        requestId,
        "WRITE_SCHEDULE_STATUS",
        () -> MvpStoreReportScheduleWritebackSupport.writeScheduleStatus(this, payload, requestId, operator)
      );
    }
  }

  public Map<String, Object> ingestWipLotEvent(Map<String, Object> payload, String requestId, String operator) {
    synchronized (lock) {
      return runIdempotent(
        requestId,
        "INGEST_WIP_EVENT",
        () -> MvpStoreReportWipLotSupport.ingestWipLotEvent(this, payload, requestId, operator)
      );
    }
  }

  public Map<String, Object> triggerReplanJob(Map<String, Object> payload, String requestId, String operator) {
    synchronized (lock) {
      return runIdempotent(
        requestId,
        "TRIGGER_REPLAN",
        () -> MvpStoreReportOpsSupport.triggerReplanJob(this, payload, requestId, operator)
      );
    }
  }

  public Map<String, Object> getReplanJob(String jobNo, String requestId) {
    synchronized (lock) {
      return MvpStoreReportOpsSupport.getReplanJob(this, jobNo, requestId);
    }
  }

  public List<Map<String, Object>> listAlerts(Map<String, String> filters) {
    synchronized (lock) {
      return MvpStoreReportOpsSupport.listAlerts(this, filters);
    }
  }

  public Map<String, Object> ackAlert(String alertId, Map<String, Object> payload, String requestId, String operator) {
    synchronized (lock) {
      return MvpStoreReportOpsSupport.ackAlert(this, alertId, payload, requestId, operator);
    }
  }

  public Map<String, Object> closeAlert(String alertId, Map<String, Object> payload, String requestId, String operator) {
    synchronized (lock) {
      return MvpStoreReportOpsSupport.closeAlert(this, alertId, payload, requestId, operator);
    }
  }

  public List<Map<String, Object>> listAuditLogs(Map<String, String> filters) {
    synchronized (lock) {
      return MvpStoreReportOpsSupport.listAuditLogs(this, filters);
    }
  }

  public List<Map<String, Object>> listIntegrationInbox(Map<String, String> filters) {
    synchronized (lock) {
      return MvpStoreReportOpsSupport.listIntegrationInbox(this, filters);
    }
  }

  public List<Map<String, Object>> listIntegrationOutbox(Map<String, String> filters) {
    synchronized (lock) {
      return MvpStoreReportOpsSupport.listIntegrationOutbox(this, filters);
    }
  }
}

