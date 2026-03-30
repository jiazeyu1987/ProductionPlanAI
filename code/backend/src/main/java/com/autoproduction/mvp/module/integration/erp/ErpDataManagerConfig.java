package com.autoproduction.mvp.module.integration.erp;

import java.util.LinkedHashMap;
import java.util.Map;

record ErpDataManagerConfig(
  boolean refreshEnabled,
  long triggerMinIntervalMs,
  boolean readThroughOnEmpty,
  String baseUrl,
  String acctId,
  String username,
  String password,
  int lcid,
  int timeoutSeconds,
  boolean verifySsl,
  long materialIssueCacheTtlMs
) {

  Map<String, Object> connectionSummary() {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("base_url", baseUrl);
    out.put("acct_id", acctId);
    out.put("username", username);
    out.put("password_configured", password != null && !password.isBlank());
    out.put("lcid", lcid);
    out.put("timeout", timeoutSeconds);
    out.put("verify_ssl", verifySsl);
    return out;
  }
}

