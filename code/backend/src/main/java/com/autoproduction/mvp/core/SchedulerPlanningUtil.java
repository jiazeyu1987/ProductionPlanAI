package com.autoproduction.mvp.core;

import static com.autoproduction.mvp.core.SchedulerPlanningConstants.NIGHT_SHIFT_CAPACITY_FACTOR;

import java.time.LocalDate;

final class SchedulerPlanningUtil {
  private SchedulerPlanningUtil() {}

  static double round3(double value) {
    return Math.round(value * 1000d) / 1000d;
  }

  static LocalDate parseDate(String value) {
    if (value == null || value.isBlank()) {
      return LocalDate.of(1970, 1, 1);
    }
    try {
      return LocalDate.parse(value);
    } catch (Exception ignored) {
      return LocalDate.of(1970, 1, 1);
    }
  }

  static LocalDate expectedStartDateForOrder(MvpDomain.Order order) {
    if (order == null) {
      return null;
    }
    if (order.expectedStartDate != null) {
      return order.expectedStartDate;
    }
    if (order.businessData != null && order.businessData.orderDate != null) {
      return order.businessData.orderDate;
    }
    return null;
  }

  static LocalDate parseFlexibleDate(String raw, LocalDate referenceDate) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String text = raw.trim();
    try {
      return LocalDate.parse(text);
    } catch (Exception ignore) {
      // fallback below
    }
    String normalized = text.replace('/', '.').replace('-', '.');
    String[] parts = normalized.split("\\.");
    if (parts.length == 2) {
      try {
        int month = Integer.parseInt(parts[0]);
        int day = Integer.parseInt(parts[1]);
        int year = referenceDate == null ? LocalDate.now().getYear() : referenceDate.getYear();
        return LocalDate.of(year, month, day);
      } catch (Exception ignore) {
        return null;
      }
    }
    return null;
  }

  static String normalizeLineToken(String value, String fallback) {
    String normalized = value == null ? "" : value.trim().toUpperCase();
    if (normalized.isBlank()) {
      return fallback;
    }
    return normalized;
  }

  static String normalizeProcessCode(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim().toUpperCase();
    return normalized.isBlank() ? null : normalized;
  }

  static String normalizeKeyToken(String value) {
    if (value == null) {
      return "";
    }
    String normalized = value.trim().toUpperCase();
    return normalized.isBlank() ? "" : normalized;
  }

  static String reportingKey(String orderNo, String productCode, String processCode) {
    return normalizeKeyToken(orderNo) + "#" + normalizeKeyToken(productCode) + "#" + normalizeKeyToken(processCode);
  }

  static String normalizeShiftCode(String shiftCode) {
    if (shiftCode == null) {
      return "D";
    }
    String normalized = shiftCode.trim().toUpperCase();
    if (normalized.isBlank()) {
      return "D";
    }
    if ("DAY".equals(normalized)) {
      return "D";
    }
    if ("N".equals(normalized) || "D".equals(normalized)) {
      return normalized;
    }
    return "D";
  }

  static double shiftCapacityFactor(String shiftCode) {
    return "N".equalsIgnoreCase(normalizeShiftCode(shiftCode)) ? NIGHT_SHIFT_CAPACITY_FACTOR : 1d;
  }

  static double processCapacityFactor(String processCode) {
    if (processCode == null) {
      return 1d;
    }
    if (processCode.toUpperCase().contains("STERILE")) {
      return 0.86d;
    }
    if (processCode.toUpperCase().contains("ASSEMBLY")) {
      return 0.94d;
    }
    return 1d;
  }
}

