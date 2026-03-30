package com.autoproduction.mvp.core;

import static com.autoproduction.mvp.core.erp.loader.ErpLoaderBillQueryUtils.mapQueryRows;
import static com.autoproduction.mvp.core.erp.loader.ErpLoaderBillQueryUtils.normalizeBillQueryRows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ErpKingdeeBillQueryClient {
  private final ErpKingdeeApiSession session;
  private final ObjectMapper objectMapper;
  private final ErpSqliteOrderValidator validator;

  ErpKingdeeBillQueryClient(ErpKingdeeApiSession session, ObjectMapper objectMapper, ErpSqliteOrderValidator validator) {
    this.session = session;
    this.objectMapper = objectMapper;
    this.validator = validator;
  }

  List<Map<String, Object>> queryBillRowsFromApi(
    String formId,
    String fieldKeys,
    String filterString,
    String orderString,
    int startRow,
    int limit
  ) {
    session.ensureApiLogin(false);
    String service = "Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.ExecuteBillQuery.common.kdsvc";
    Map<String, Object> queryObj = new LinkedHashMap<>();
    queryObj.put("FormId", formId);
    queryObj.put("FieldKeys", fieldKeys);
    queryObj.put("FilterString", filterString == null ? "" : filterString);
    queryObj.put("OrderString", orderString == null ? "FID DESC" : orderString);
    queryObj.put("StartRow", Math.max(0, startRow));
    queryObj.put("Limit", Math.max(1, limit));

    String payloadJson;
    try {
      payloadJson = objectMapper.writeValueAsString(queryObj);
    } catch (Exception ex) {
      throw new RuntimeException("Failed to serialize ERP query payload.", ex);
    }

    RuntimeException lastEx = null;
    for (String url : session.serviceUrls(service)) {
      try {
        Object parsed = session.postFormForJson(url, Map.of("data", payloadJson));
        if (parsed instanceof List<?> rows) {
          return normalizeBillQueryRows(rows, fieldKeys);
        }
        if (parsed instanceof Map<?, ?> parsedMap) {
          String message = "ERP query unexpected response.";
          Object statusObj = mapValue(mapValue(parsedMap, "Result"), "ResponseStatus");
          if (statusObj instanceof Map<?, ?> statusMap) {
            if (Boolean.TRUE.equals(statusMap.get("IsSuccess"))) {
              return List.of();
            }
            Object errors = statusMap.get("Errors");
            message = errors == null ? String.valueOf(statusMap) : String.valueOf(errors);
          } else {
            message = String.valueOf(parsedMap);
          }
          if (validator.containsAuthError(message)) {
            session.invalidateLogin();
            session.ensureApiLogin(true);
            parsed = session.postFormForJson(url, Map.of("data", payloadJson));
            if (parsed instanceof List<?> retryRows) {
              return mapQueryRows(retryRows, fieldKeys);
            }
          }
          throw new RuntimeException(message);
        }
        throw new RuntimeException("ERP query response is not JSON.");
      } catch (RuntimeException ex) {
        lastEx = ex;
      }
    }
    if (lastEx != null) {
      throw lastEx;
    }
    return List.of();
  }

  Object viewBillFromApi(String formId, String number, String id) {
    String normalizedFormId = com.autoproduction.mvp.core.erp.loader.ErpLoaderValueUtils.firstText(formId);
    if (normalizedFormId == null) {
      return Map.of();
    }
    String service = "Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.View.common.kdsvc";

    List<Map<String, Object>> payloads = new ArrayList<>();
    if (number != null && id != null) {
      payloads.add(Map.of("FormId", normalizedFormId, "Number", number, "Id", id));
    }
    if (number != null) {
      payloads.add(Map.of("FormId", normalizedFormId, "Number", number));
    }
    if (id != null) {
      payloads.add(Map.of("FormId", normalizedFormId, "Id", id));
    }
    payloads.add(Map.of("FormId", normalizedFormId));

    RuntimeException lastEx = null;
    for (Map<String, Object> payload : payloads) {
      Map<String, Object> payloadWithoutFormId = new LinkedHashMap<>(payload);
      payloadWithoutFormId.remove("FormId");
      String payloadJson;
      String payloadNoFormIdJson;
      try {
        payloadJson = objectMapper.writeValueAsString(payload);
        payloadNoFormIdJson = objectMapper.writeValueAsString(payloadWithoutFormId);
      } catch (Exception ex) {
        continue;
      }
      List<Map<String, String>> wrappers = List.of(
        Map.of("data", payloadJson),
        Map.of("formid", normalizedFormId, "data", payloadNoFormIdJson),
        Map.of("FormId", normalizedFormId, "data", payloadNoFormIdJson),
        Map.of("formId", normalizedFormId, "data", payloadNoFormIdJson)
      );
      for (String url : session.serviceUrls(service)) {
        for (Map<String, String> wrapper : wrappers) {
          try {
            Object parsed = session.postFormForJson(url, wrapper);
            if (!(parsed instanceof Map<?, ?> map)) {
              return parsed;
            }
            Object statusObj = mapValue(mapValue(map, "Result"), "ResponseStatus");
            if (!(statusObj instanceof Map<?, ?> statusMap)) {
              return parsed;
            }
            if (Boolean.TRUE.equals(statusMap.get("IsSuccess"))) {
              return parsed;
            }
            lastEx = new RuntimeException(String.valueOf(statusMap.get("Errors")));
          } catch (RuntimeException ex) {
            lastEx = ex;
          }
        }
      }
    }
    if (lastEx != null) {
      throw lastEx;
    }
    return Map.of();
  }

  private Object mapValue(Object source, String key) {
    if (!(source instanceof Map<?, ?> map)) {
      return null;
    }
    return map.get(key);
  }
}
