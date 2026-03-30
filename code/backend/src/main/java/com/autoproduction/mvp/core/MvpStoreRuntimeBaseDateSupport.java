package com.autoproduction.mvp.core;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

final class MvpStoreRuntimeBaseDateSupport {
  private MvpStoreRuntimeBaseDateSupport() {}

  static String normalizeWeekendRestMode(String modeText, String none, String single, String dbl) {
    if (modeText == null || modeText.isBlank()) {
      return none;
    }
    String normalized = modeText.trim().toUpperCase(Locale.ROOT);
    return switch (normalized) {
      case "NONE", "NO_REST", "NOREST", "0", "\u65e0\u4f11", "\u4e0d\u4f11" -> none;
      case "SINGLE", "SINGLE_REST", "1", "\u5355\u4f11" -> single;
      case "DOUBLE", "DOUBLE_REST", "2", "\u53cc\u4f11" -> dbl;
      default -> none;
    };
  }

  static String toDateTime(String date, String shiftCode, boolean start) {
    LocalDate parsed = LocalDate.parse(date);
    int startHour = "N".equals(shiftCode) || "NIGHT".equals(shiftCode) ? 20 : 8;
    OffsetDateTime time = parsed.atTime(startHour, 0).atOffset(ZoneOffset.UTC);
    if (!start) {
      time = time.plusHours(12);
    }
    return time.toString();
  }
}

