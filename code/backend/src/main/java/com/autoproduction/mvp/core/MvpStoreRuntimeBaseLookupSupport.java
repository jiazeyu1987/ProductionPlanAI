package com.autoproduction.mvp.core;

import java.util.Map;

final class MvpStoreRuntimeBaseLookupSupport {
  private MvpStoreRuntimeBaseLookupSupport() {}

  static String firstString(Map<String, Object> row, String... keys) {
    if (row == null || keys == null) {
      return null;
    }
    for (String key : keys) {
      Object value = row.get(key);
      if (value != null) {
        String text = String.valueOf(value);
        if (!text.isBlank()) {
          return text;
        }
      }
    }
    return null;
  }

  static String nameCn(Map<String, String> dictionary, String code) {
    if (dictionary == null) {
      return code == null ? "" : code;
    }
    return dictionary.getOrDefault(code, code == null ? "" : code);
  }
}

