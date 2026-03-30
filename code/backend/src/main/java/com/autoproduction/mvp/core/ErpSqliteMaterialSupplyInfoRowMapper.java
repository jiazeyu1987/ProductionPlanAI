package com.autoproduction.mvp.core;

import static com.autoproduction.mvp.core.erp.loader.ErpLoaderTextUtils.fixPotentialUtf8Mojibake;
import static com.autoproduction.mvp.core.erp.loader.ErpLoaderValueUtils.firstText;
import static com.autoproduction.mvp.core.erp.loader.ErpLoaderValueUtils.toBoolean;

import java.util.LinkedHashMap;
import java.util.Map;

final class ErpSqliteMaterialSupplyInfoRowMapper extends ErpSqliteOrderRowMapperSupport {
  Map<String, Object> normalizeMaterialSupplyInfo(Map<String, Object> row, String materialCode) {
    String normalizedMaterialCode = firstText(row.get("FNumber"), row.get("material_code"), materialCode);
    String materialName = fixPotentialUtf8Mojibake(firstText(row.get("FName"), row.get("material_name_cn")));
    boolean isPurchase = toBoolean(row.get("FIsPurchase"));
    boolean isProduce = toBoolean(row.get("FIsProduce"));
    String categoryName = fixPotentialUtf8Mojibake(firstText(
      row.get("FCategoryID.FName"),
      row.get("FMaterialGroup.FName"),
      row.get("FErpClsID")
    ));
    String typeText = firstText(categoryName, row.get("supply_type_name_cn"), row.get("supply_type"));
    String supplyType = resolveMaterialSupplyType(isPurchase, isProduce, typeText, normalizedMaterialCode);
    String supplyTypeNameCn = switch (supplyType) {
      case "PURCHASED" -> "外购";
      case "SELF_MADE" -> "自制";
      default -> "未知";
    };

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("material_code", normalizedMaterialCode);
    out.put("material_name_cn", materialName == null ? "" : materialName);
    out.put("supply_type", supplyType);
    out.put("supply_type_name_cn", supplyTypeNameCn);
    out.put("supply_type_raw", typeText == null ? "" : typeText);
    return out;
  }
}

