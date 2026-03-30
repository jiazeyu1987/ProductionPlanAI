package com.autoproduction.mvp.module.dispatchalert;

import com.autoproduction.mvp.core.MvpStoreService;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ReplanJobService {
  private final MvpStoreService store;

  public ReplanJobService(MvpStoreService store) {
    this.store = store;
  }

  public void triggerReplanJob(Map<String, Object> payload, String requestId, String operator) {
    store.triggerReplanJob(payload, requestId, operator);
  }

  public Map<String, Object> getReplanJob(String jobNo, String requestId) {
    return store.getReplanJob(jobNo, requestId);
  }
}
