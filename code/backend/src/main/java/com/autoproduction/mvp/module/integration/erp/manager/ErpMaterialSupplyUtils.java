package com.autoproduction.mvp.module.integration.erp.manager;

public final class ErpMaterialSupplyUtils {
  private ErpMaterialSupplyUtils() {}

  public static boolean isKnownSupplyType(String supplyType) {
    if (supplyType == null) {
      return false;
    }
    return "PURCHASED".equalsIgnoreCase(supplyType) || "SELF_MADE".equalsIgnoreCase(supplyType);
  }

  public static String inferMaterialSupplyTypeByCode(String materialCode) {
    String code = ErpMaterialCodeUtils.normalizeText(materialCode);
    if (code == null) {
      return null;
    }
    String normalized = code.toUpperCase();
    // ERP material master occasionally misses purchase/produce flags.
    // For current coded materials, A001/A002 are external-procurement items.
    if (normalized.startsWith("A001.") || normalized.startsWith("A002.")) {
      return "PURCHASED";
    }
    return null;
  }

  public static String toSupplyTypeNameCn(String supplyType) {
    if ("PURCHASED".equalsIgnoreCase(supplyType)) {
      return "\u5916\u8D2D";
    }
    if ("SELF_MADE".equalsIgnoreCase(supplyType)) {
      return "\u81EA\u5236";
    }
    return "\u672A\u77E5";
  }
}
