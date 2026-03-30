package com.autoproduction.mvp.core;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

final class MvpStoreEntityMappingDateSupport {
  private MvpStoreEntityMappingDateSupport() {}

  static String formatShortDate(LocalDate date) {
    return date.getMonthValue() + "." + date.getDayOfMonth();
  }

  static OffsetDateTime parseOffsetDateTimeFilter(Map<String, String> filters, String... keys) {
    if (filters == null || keys == null) {
      return null;
    }
    for (String key : keys) {
      if (key == null || key.isBlank()) {
        continue;
      }
      String raw = filters.get(key);
      if (raw == null || raw.isBlank()) {
        continue;
      }
      try {
        return OffsetDateTime.parse(raw);
      } catch (Exception ignore) {
        try {
          return LocalDateTime.parse(raw).atOffset(ZoneOffset.UTC);
        } catch (Exception ignoreAgain) {
          // ignore invalid filter value
        }
      }
    }
    return null;
  }

  static LocalDate parseLocalDateFlexible(String value, LocalDate fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    String normalized = value.trim();
    if (normalized.matches("\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}")) {
      String[] parts = normalized.replace('/', '-').split("-");
      try {
        int year = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);
        int day = Integer.parseInt(parts[2]);
        return LocalDate.of(year, month, day);
      } catch (Exception ignore) {
        // continue
      }
    }
    if (normalized.length() >= 10) {
      String dateOnly = normalized.substring(0, 10).replace("/", "-");
      try {
        return LocalDate.parse(dateOnly);
      } catch (Exception ignore) {
        // continue
      }
    }
    if (normalized.matches("\\d{8}")) {
      String dateOnly = normalized.substring(0, 4)
        + "-"
        + normalized.substring(4, 6)
        + "-"
        + normalized.substring(6, 8);
      try {
        return LocalDate.parse(dateOnly);
      } catch (Exception ignore) {
        return fallback;
      }
    }
    return fallback;
  }
}

