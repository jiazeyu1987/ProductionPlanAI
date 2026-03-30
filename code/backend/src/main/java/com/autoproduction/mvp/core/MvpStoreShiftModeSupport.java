package com.autoproduction.mvp.core;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class MvpStoreShiftModeSupport {
  private MvpStoreShiftModeSupport() {}

  static String normalizeShiftCodeLabel(String shiftCode) {
    return switch (MvpStoreLocalizationLookupSupport.normalizeShiftCode(shiftCode)) {
      case "D" -> "DAY";
      case "N" -> "NIGHT";
      default -> shiftCode == null ? "" : shiftCode;
    };
  }

  static int shiftSortIndex(String shiftCode) {
    return switch (MvpStoreLocalizationLookupSupport.normalizeShiftCode(shiftCode)) {
      case "D" -> 0;
      case "N" -> 1;
      default -> 9;
    };
  }

  static String normalizeWeekendRestMode(String modeText) {
    if (modeText == null) {
      return MvpStoreRuntimeBase.WEEKEND_REST_MODE_DOUBLE;
    }
    String normalized = modeText.trim().toUpperCase(Locale.ROOT);
    return switch (normalized) {
      case MvpStoreRuntimeBase.WEEKEND_REST_MODE_NONE -> MvpStoreRuntimeBase.WEEKEND_REST_MODE_NONE;
      case MvpStoreRuntimeBase.WEEKEND_REST_MODE_SINGLE -> MvpStoreRuntimeBase.WEEKEND_REST_MODE_SINGLE;
      default -> MvpStoreRuntimeBase.WEEKEND_REST_MODE_DOUBLE;
    };
  }

  static String normalizeDateShiftMode(String modeText) {
    if (modeText == null) {
      return "";
    }
    String normalized = modeText.trim().toUpperCase(Locale.ROOT);
    return switch (normalized) {
      case MvpStoreRuntimeBase.DATE_SHIFT_MODE_REST -> MvpStoreRuntimeBase.DATE_SHIFT_MODE_REST;
      case MvpStoreRuntimeBase.DATE_SHIFT_MODE_DAY -> MvpStoreRuntimeBase.DATE_SHIFT_MODE_DAY;
      case MvpStoreRuntimeBase.DATE_SHIFT_MODE_NIGHT -> MvpStoreRuntimeBase.DATE_SHIFT_MODE_NIGHT;
      case MvpStoreRuntimeBase.DATE_SHIFT_MODE_BOTH -> MvpStoreRuntimeBase.DATE_SHIFT_MODE_BOTH;
      default -> "";
    };
  }

  static Map<String, String> normalizeDateShiftModeByDate(Object input) {
    Map<String, String> out = new LinkedHashMap<>();
    if (!(input instanceof Map<?, ?> rawMap)) {
      return out;
    }
    for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
      String dateText = String.valueOf(entry.getKey() == null ? "" : entry.getKey()).trim();
      if (dateText.isBlank()) {
        continue;
      }
      try {
        LocalDate.parse(dateText);
      } catch (RuntimeException ignored) {
        continue;
      }
      String mode = normalizeDateShiftMode(String.valueOf(entry.getValue() == null ? "" : entry.getValue()));
      if (mode.isBlank()) {
        continue;
      }
      out.put(dateText, mode);
    }
    return out;
  }

  static boolean isCnStatutoryHoliday(LocalDate date) {
    if (date == null) {
      return false;
    }
    return MvpStoreRuntimeBase.CN_STATUTORY_HOLIDAY_DATE_SET.contains(date.toString());
  }

  static String resolveDateShiftMode(MvpStoreLocalizationAndExportSupport store, LocalDate date) {
    if (date == null) {
      return MvpStoreRuntimeBase.DATE_SHIFT_MODE_DAY;
    }
    String manualMode =
      store.state.dateShiftModeByDate == null
        ? ""
        : normalizeDateShiftMode(store.state.dateShiftModeByDate.get(date.toString()));
    if (!manualMode.isBlank()) {
      return manualMode;
    }
    if (!store.state.skipStatutoryHolidays) {
      return MvpStoreRuntimeBase.DATE_SHIFT_MODE_DAY;
    }
    if (isCnStatutoryHoliday(date)) {
      return MvpStoreRuntimeBase.DATE_SHIFT_MODE_REST;
    }

    String weekendRestMode = normalizeWeekendRestMode(store.state.weekendRestMode);
    int weekday = date.getDayOfWeek().getValue();
    if (MvpStoreRuntimeBase.WEEKEND_REST_MODE_NONE.equals(weekendRestMode)) {
      return MvpStoreRuntimeBase.DATE_SHIFT_MODE_DAY;
    }
    if (MvpStoreRuntimeBase.WEEKEND_REST_MODE_SINGLE.equals(weekendRestMode)) {
      return weekday == 7 ? MvpStoreRuntimeBase.DATE_SHIFT_MODE_REST : MvpStoreRuntimeBase.DATE_SHIFT_MODE_DAY;
    }
    return (weekday == 6 || weekday == 7) ? MvpStoreRuntimeBase.DATE_SHIFT_MODE_REST : MvpStoreRuntimeBase.DATE_SHIFT_MODE_DAY;
  }

  static boolean isShiftOpenInDateMode(String shiftCode, String modeText) {
    String normalizedShiftCode = MvpStoreLocalizationLookupSupport.normalizeShiftCode(shiftCode);
    String mode = normalizeDateShiftMode(modeText);
    if (MvpStoreRuntimeBase.DATE_SHIFT_MODE_REST.equals(mode)) {
      return false;
    }
    if (MvpStoreRuntimeBase.DATE_SHIFT_MODE_NIGHT.equals(mode)) {
      return "N".equals(normalizedShiftCode);
    }
    if (MvpStoreRuntimeBase.DATE_SHIFT_MODE_BOTH.equals(mode)) {
      return "D".equals(normalizedShiftCode) || "N".equals(normalizedShiftCode);
    }
    return "D".equals(normalizedShiftCode);
  }
}

