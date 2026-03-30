package com.autoproduction.mvp.module.integration;

import com.autoproduction.mvp.core.MvpStoreService;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class IntegrationCommandService {
  private final MvpStoreService store;

  public IntegrationCommandService(MvpStoreService store) {
    this.store = store;
  }

  public Map<String, Object> writeScheduleResults(Map<String, Object> payload, String requestId, String operator) {
    return store.writeScheduleResults(payload, requestId, operator);
  }

  public Map<String, Object> writeScheduleStatus(Map<String, Object> payload, String requestId, String operator) {
    return store.writeScheduleStatus(payload, requestId, operator);
  }

  public Map<String, Object> ingestWipLotEvent(Map<String, Object> payload, String requestId, String operator) {
    return store.ingestWipLotEvent(payload, requestId, operator);
  }

  public void triggerReplanJob(Map<String, Object> payload, String requestId, String operator) {
    store.triggerReplanJob(payload, requestId, operator);
  }

  public Map<String, Object> retryOutboxMessage(String messageId, String requestId, String operator) {
    return store.retryOutboxMessage(messageId, requestId, operator);
  }
}
