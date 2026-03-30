package com.autoproduction.mvp.core;

import java.time.LocalDate;
import java.time.ZoneOffset;

final class ErpSqliteOrderValidator {
  private final String baseUrl;
  private final String acctId;
  private final String username;
  private final String password;
  private final String defaultErpOrgNo;

  ErpSqliteOrderValidator(String baseUrl, String acctId, String username, String password, String defaultErpOrgNo) {
    this.baseUrl = baseUrl;
    this.acctId = acctId;
    this.username = username;
    this.password = password;
    this.defaultErpOrgNo = defaultErpOrgNo;
  }

  boolean hasApiConfig() {
    return !baseUrl.isBlank() && !acctId.isBlank() && !username.isBlank() && !password.isBlank();
  }

  String buildOrgDateFilter(String orgField, String dateField, int lookbackDays) {
    LocalDate startDate = LocalDate.now(ZoneOffset.UTC).minusDays(Math.max(30, lookbackDays));
    return buildOrgFilter(orgField) + " and " + dateField + " >= '" + startDate + "'";
  }

  String buildOrgFilter(String orgField) {
    return orgField + " = '" + escapeFilterValue(defaultErpOrgNo) + "'";
  }

  String escapeFilterValue(String text) {
    return text == null ? "" : text.replace("'", "''");
  }

  boolean equalsIgnoreCaseSafe(String left, String right) {
    if (left == null || right == null) {
      return false;
    }
    return left.equalsIgnoreCase(right);
  }

  boolean containsAuthError(String message) {
    String text = message == null ? "" : message.toLowerCase();
    return text.contains("鐧诲綍")
      || text.contains("璇峰厛鐧诲綍")
      || text.contains("session")
      || text.contains("context")
      || text.contains("401")
      || text.contains("403");
  }
}
