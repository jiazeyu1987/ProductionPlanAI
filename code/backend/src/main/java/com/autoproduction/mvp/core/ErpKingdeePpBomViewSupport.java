package com.autoproduction.mvp.core;

import static com.autoproduction.mvp.core.erp.loader.ErpLoaderTextUtils.fixPotentialUtf8Mojibake;
import static com.autoproduction.mvp.core.erp.loader.ErpLoaderValueUtils.firstNonNullNumber;
import static com.autoproduction.mvp.core.erp.loader.ErpLoaderValueUtils.firstText;
import static com.autoproduction.mvp.core.erp.loader.ErpLoaderValueUtils.toNumber;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class ErpKingdeePpBomViewSupport {
  private final ErpKingdeeBillQueryClient billQueryClient;
  private final ErpOrderPayloadMapper payloadMapper;
  private final ErpSqliteOrderRowMapper rowMapper;
  private final Map<String, List<Map<String, Object>>> ppBomViewEntryCache = new ConcurrentHashMap<>();

  ErpKingdeePpBomViewSupport(
    ErpKingdeeBillQueryClient billQueryClient,
    ErpOrderPayloadMapper payloadMapper,
    ErpSqliteOrderRowMapper rowMapper
  ) {
    this.billQueryClient = billQueryClient;
    this.payloadMapper = payloadMapper;
    this.rowMapper = rowMapper;
  }

  void clearCache() {
    ppBomViewEntryCache.clear();
  }

  List<Map<String, Object>> expandPpBomRowsByView(
    List<Map<String, Object>> headerRows,
    String fallbackSourceBillNo
  ) {
    if (headerRows == null || headerRows.isEmpty()) {
      return List.of();
    }
    for (Map<String, Object> headerRow : headerRows) {
      List<Map<String, Object>> expanded = new ArrayList<>();
      String billNo = firstText(headerRow.get("FBillNo"), headerRow.get("BillNo"));
      if (billNo == null) {
        continue;
      }
      List<Map<String, Object>> cached = ppBomViewEntryCache.get(billNo);
      if (cached != null && !cached.isEmpty()) {
        List<Map<String, Object>> clone = new ArrayList<>();
        for (Map<String, Object> row : cached) {
          clone.add(new LinkedHashMap<>(row));
        }
        return clone;
      }
      String sourceBillNo = firstText(
        headerRow.get("FNumber"),
        headerRow.get("source_bill_no"),
        fallbackSourceBillNo,
        billNo
      );
      String fid = firstText(headerRow.get("FID"));
      Object viewed = billQueryClient.viewBillFromApi("PRD_PPBOM", billNo, fid);
      if (!(viewed instanceof Map<?, ?> viewedMap)) {
        continue;
      }
      Map<String, Object> model = extractViewBillModel(viewedMap);
      if (model == null || model.isEmpty()) {
        continue;
      }
      String documentStatus = firstText(model.get("DocumentStatus"), headerRow.get("FDocumentStatus"));
      String issueDate = firstText(
        model.get("Date"),
        model.get("CreateDate"),
        model.get("ApproveDate"),
        model.get("ModifyDate"),
        headerRow.get("FDate")
      );

      List<Map<String, Object>> treeRows = parseLineListByFields(model, List.of("TreeEntity", "FTreeEntity"));
      if (treeRows.isEmpty()) {
        treeRows = List.of(model);
      }
      for (Map<String, Object> treeRow : treeRows) {
        List<Map<String, Object>> entryRows = parseLineListByFields(
          treeRow,
          List.of("PPBomEntry", "FPPBomEntry", "Entity", "FEntity")
        );
        if (entryRows.isEmpty() && treeRow == model) {
          entryRows = parseLineListByFields(model, List.of("PPBomEntry", "FPPBomEntry", "Entity", "FEntity"));
        }
        for (Map<String, Object> entry : entryRows) {
          String childCode = firstText(
            rowMapper.mapValue(entry.get("MaterialID"), "Number"),
            rowMapper.mapValue(entry.get("MATERIALID"), "Number"),
            rowMapper.mapValue(entry.get("MaterialId"), "Number"),
            entry.get("MaterialID_Number"),
            entry.get("MaterialID.FNumber"),
            entry.get("FMaterialId.FNumber")
          );
          if (childCode == null) {
            continue;
          }
          String childName = firstText(
            rowMapper.masterDataNameCn(entry.get("MaterialID")),
            rowMapper.masterDataNameCn(entry.get("MATERIALID")),
            rowMapper.masterDataNameCn(entry.get("MaterialId")),
            rowMapper.mapValue(entry.get("MaterialID"), "Name"),
            rowMapper.mapValue(entry.get("MATERIALID"), "Name"),
            rowMapper.mapValue(entry.get("MaterialId"), "Name"),
            entry.get("MaterialID_Name"),
            entry.get("MaterialID.FName"),
            entry.get("FMaterialId.FName")
          );
          childName = fixPotentialUtf8Mojibake(childName);
          Number requiredQty = firstNonNullNumber(
            toNumber(entry.get("MustQty")),
            toNumber(entry.get("NeedQty")),
            toNumber(entry.get("StdQty")),
            toNumber(entry.get("BaseMustQty")),
            toNumber(entry.get("BaseNeedQty")),
            toNumber(entry.get("BaseStdQty")),
            toNumber(entry.get("Numerator")),
            toNumber(entry.get("BaseNumerator"))
          );

          Map<String, Object> row = new LinkedHashMap<>();
          row.put("FBillNo", billNo);
          row.put("FSeq", firstText(entry.get("Seq"), entry.get("BOMEntryID"), entry.get("Id")));
          row.put("FDate", issueDate);
          row.put("FDocumentStatus", documentStatus);
          row.put("FSrcBillNo", sourceBillNo);
          row.put("FSrcBillType", "PRD_PPBOM");
          row.put("FMaterialId.FNumber", childCode);
          row.put("FMaterialId.FName", childName);
          row.put("FActualQty", requiredQty == null ? 0d : requiredQty);
          row.put("FQty", requiredQty == null ? 0d : requiredQty);
          row.put("source_bill_no", sourceBillNo);
          row.put("source_bill_type", "PRD_PPBOM");
          row.put("erp_source_table", "ERP_API_BOM_VIEW_ENTRY");
          row.put("erp_form_id", "PRD_PPBOM");
          expanded.add(row);
        }
      }
      if (!expanded.isEmpty()) {
        List<Map<String, Object>> cachedRows = new ArrayList<>();
        for (Map<String, Object> row : expanded) {
          cachedRows.add(new LinkedHashMap<>(row));
        }
        ppBomViewEntryCache.put(billNo, cachedRows);
        return expanded;
      }
    }
    return List.of();
  }

  private Map<String, Object> extractViewBillModel(Map<?, ?> viewResponse) {
    Map<String, Object> root = toStringObjectMap(viewResponse);
    if (root == null) {
      return null;
    }
    Object resultObj = root.get("Result");
    Map<String, Object> resultMap = toStringObjectMap(resultObj);
    if (resultMap == null) {
      return null;
    }
    Object modelObj = resultMap.get("Result");
    Map<String, Object> modelMap = toStringObjectMap(modelObj);
    if (modelMap != null && !modelMap.isEmpty()) {
      return modelMap;
    }
    Object dataObj = resultMap.get("NeedReturnData");
    Map<String, Object> dataMap = toStringObjectMap(dataObj);
    if (dataMap != null && !dataMap.isEmpty()) {
      return dataMap;
    }
    if (dataObj instanceof List<?> list && !list.isEmpty()) {
      Map<String, Object> first = toStringObjectMap(list.get(0));
      if (first != null && !first.isEmpty()) {
        return first;
      }
    }
    return null;
  }

  private Map<String, Object> toStringObjectMap(Object source) {
    if (!(source instanceof Map<?, ?> sourceMap)) {
      return null;
    }
    Map<String, Object> out = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : sourceMap.entrySet()) {
      out.put(String.valueOf(entry.getKey()), entry.getValue());
    }
    return out;
  }

  private List<Map<String, Object>> parseLineListByFields(Map<String, Object> header, List<String> lineListFields) {
    return payloadMapper.parseLineListByFields(header, lineListFields);
  }
}

