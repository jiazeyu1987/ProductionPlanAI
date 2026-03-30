package com.autoproduction.mvp.module.dispatchalert;

import com.autoproduction.mvp.core.MvpStoreService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DispatchCommandService {
  private final MvpStoreService store;

  public DispatchCommandService(MvpStoreService store) {
    this.store = store;
  }

  public List<Map<String, Object>> listDispatchCommands(Map<String, String> filters) {
    return store.listDispatchCommands(filters);
  }

  public Map<String, Object> createDispatchCommand(Map<String, Object> payload, String requestId, String operator) {
    return store.createDispatchCommand(payload, requestId, operator);
  }

  public Map<String, Object> approveDispatchCommand(String commandId, Map<String, Object> payload, String requestId, String operator) {
    return store.approveDispatchCommand(commandId, payload, requestId, operator);
  }
}
