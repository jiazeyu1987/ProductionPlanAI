package com.autoproduction.mvp.core;

final class MvpStoreMaterialCodeSupport {
  private MvpStoreMaterialCodeSupport() {}

  static String normalizeCode(String value) {
    return value == null ? "" : value.trim().toUpperCase();
  }

  static String normalizeMaterialCode(String value) {
    return normalizeCode(value);
  }

  static String materialListBaseCode(String materialListNo) {
    String normalized = normalizeMaterialCode(materialListNo);
    if (normalized.isBlank()) {
      return "";
    }
    int splitIndex = normalized.indexOf('_');
    if (splitIndex <= 0) {
      splitIndex = normalized.indexOf("-V");
    }
    if (splitIndex <= 0) {
      return normalized;
    }
    return normalized.substring(0, splitIndex);
  }

  static boolean isSameMaterialCode(String left, String right) {
    if (left == null || right == null) {
      return false;
    }
    return normalizeMaterialCode(left).equals(normalizeMaterialCode(right));
  }
}

