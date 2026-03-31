package com.autoproduction.mvp.core;

import static com.autoproduction.mvp.core.erp.loader.ErpLoaderValueUtils.firstText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ErpKingdeeMaterialQuery {
  private static final Logger log = LoggerFactory.getLogger(ErpKingdeeMaterialQuery.class);

  private final ErpKingdeeBillQueryClient billQueryClient;
  private final ErpOrderNormalizer normalizer;
  private final ErpSqliteOrderValidator validator;
  private final ErpSqliteOrderAssembler assembler;
  private final ErpSqliteOrderRowMapper rowMapper;

  ErpKingdeeMaterialQuery(
    ErpKingdeeBillQueryClient billQueryClient,
    ErpOrderNormalizer normalizer,
    ErpSqliteOrderValidator validator,
    ErpSqliteOrderAssembler assembler,
    ErpSqliteOrderRowMapper rowMapper
  ) {
    this.billQueryClient = billQueryClient;
    this.normalizer = normalizer;
    this.validator = validator;
    this.assembler = assembler;
    this.rowMapper = rowMapper;
  }

  Map<String, Object> queryMaterialSupplyInfoFromApi(String materialCode) {
    String normalizedMaterialCode = firstText(materialCode);
    if (normalizedMaterialCode == null) {
      return Map.of();
    }

    List<String> fieldVariants = List.of(
      "FID,FNumber,FName,FIsPurchase,FIsProduce,FCategoryID.FName,FCategoryID.FNumber,FUseOrgId.FNumber",
      "FID,FNumber,FName,FIsPurchase,FIsProduce,FMaterialGroup.FName,FUseOrgId.FNumber",
      "FID,FNumber,FName,FErpClsID,FUseOrgId.FNumber",
      "FID,FNumber,FName,FCategoryID.FName,FCategoryID.FNumber",
      "FID,FNumber,FName,FIsPurchase,FIsProduce",
      "FID,FNumber,FName"
    );

    List<String> filters = List.of(
      "FNumber = '" + validator.escapeFilterValue(normalizedMaterialCode) + "' and " + validator.buildOrgFilter("FUseOrgId.FNumber"),
      "FNumber = '" + validator.escapeFilterValue(normalizedMaterialCode) + "' and " + validator.buildOrgFilter("FCreateOrgId.FNumber"),
      "FNumber = '" + validator.escapeFilterValue(normalizedMaterialCode) + "'"
    );

    RuntimeException lastEx = null;
    for (String fieldKeys : fieldVariants) {
      for (String filter : filters) {
        try {
          List<Map<String, Object>> rows = billQueryClient.queryBillRowsFromApi(
            "BD_MATERIAL",
            fieldKeys,
            filter,
            "FID DESC",
            0,
            5
          );
          if (rows.isEmpty()) {
            continue;
          }
          Map<String, Object> row = rows.get(0);
          return rowMapper.normalizeMaterialSupplyInfo(row, normalizedMaterialCode);
        } catch (RuntimeException ex) {
          lastEx = ex;
          log.debug("ERP material supply query variant failed. materialCode={}, fields={}, filter={}", normalizedMaterialCode, fieldKeys, filter, ex);
        }
      }
    }
    if (lastEx != null) {
      throw lastEx;
    }
    return Map.of();
  }

  Map<String, Double> queryMaterialInventoryStock(Set<String> normalizedCodes) {
    if (normalizedCodes == null || normalizedCodes.isEmpty() || !validator.hasApiConfig()) {
      return Map.of();
    }
    List<String> fieldVariants = List.of(
      "FID,FMaterialId.FNumber,FMaterialId.FName,FStockQty,FBaseQty,FAVBQty,FCanUseQty,FQty,FStockOrgId.FNumber",
      "FID,FMaterialId.FNumber,FMaterialId.FName,FStockQty,FBaseQty,FQty,FStockOrgId.FNumber",
      "FID,FMaterialId.FNumber,FMaterialId.FName,FBaseQty,FQty,FStockOrgId.FNumber",
      "FID,FMaterialId.FNumber,FMaterialId.FName,FQty",
      "FID,FMaterialId.FNumber,FQty"
    );
    int chunkSize = 20;
    List<String> codeList = new ArrayList<>(normalizedCodes);
    Map<String, Double> stockByCode = new HashMap<>();

    for (int start = 0; start < codeList.size(); start += chunkSize) {
      int end = Math.min(codeList.size(), start + chunkSize);
      List<String> chunk = codeList.subList(start, end);
      String inClause = normalizer.buildInClause(chunk, validator::escapeFilterValue);
      if (inClause.isBlank()) {
        continue;
      }
      List<String> filters = List.of(
        "FMaterialId.FNumber in (" + inClause + ") and " + validator.buildOrgFilter("FStockOrgId.FNumber"),
        "FMaterialId.FNumber in (" + inClause + ") and " + validator.buildOrgFilter("FUseOrgId.FNumber"),
        "FMaterialId.FNumber in (" + inClause + ")"
      );

      List<Map<String, Object>> rows = List.of();
      RuntimeException lastEx = null;
      for (String fieldKeys : fieldVariants) {
        for (String filter : filters) {
          try {
            rows = billQueryClient.queryBillRowsFromApi("STK_Inventory", fieldKeys, filter, "FID DESC", 0, 2000);
            if (!rows.isEmpty()) {
              break;
            }
          } catch (RuntimeException ex) {
            lastEx = ex;
          }
        }
        if (!rows.isEmpty()) {
          break;
        }
      }
      if (rows.isEmpty()) {
        if (lastEx != null) {
          log.debug("ERP inventory query chunk failed. materialCodes={}, message={}", chunk, lastEx.getMessage());
          throw lastEx;
        }
        continue;
      }
      assembler.mergeInventoryRows(stockByCode, rows);
    }
    return stockByCode;
  }
}
