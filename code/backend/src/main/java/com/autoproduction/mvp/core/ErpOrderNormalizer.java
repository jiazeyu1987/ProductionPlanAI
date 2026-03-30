package com.autoproduction.mvp.core;

import static com.autoproduction.mvp.core.erp.loader.ErpLoaderValueUtils.firstText;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

final class ErpOrderNormalizer {
  Set<String> normalizeMaterialCodeSet(List<String> materialCodes) {
    Set<String> normalized = new HashSet<>();
    if (materialCodes == null || materialCodes.isEmpty()) {
      return normalized;
    }
    for (String code : materialCodes) {
      String text = firstText(code);
      if (text == null) {
        continue;
      }
      normalized.add(text.trim().toUpperCase());
    }
    return normalized;
  }

  String buildInClause(List<String> materialCodes, Function<String, String> escapeFilterValue) {
    if (materialCodes == null || materialCodes.isEmpty()) {
      return "";
    }
    List<String> quoted = new java.util.ArrayList<>();
    for (String code : materialCodes) {
      String normalized = firstText(code);
      if (normalized == null) {
        continue;
      }
      quoted.add("'" + escapeFilterValue.apply(normalized.toUpperCase()) + "'");
    }
    return String.join(",", quoted);
  }

  String normalizeMaterialListBaseNo(String materialListNo) {
    String normalized = firstText(materialListNo);
    if (normalized == null) {
      return null;
    }
    int splitIndex = normalized.indexOf('_');
    if (splitIndex <= 0) {
      splitIndex = normalized.toUpperCase().indexOf("-V");
    }
    if (splitIndex <= 0) {
      return normalized;
    }
    return normalized.substring(0, splitIndex);
  }
}
