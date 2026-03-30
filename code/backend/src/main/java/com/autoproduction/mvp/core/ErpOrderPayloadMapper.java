package com.autoproduction.mvp.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ErpOrderPayloadMapper {
  private final ObjectMapper objectMapper;

  ErpOrderPayloadMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  Map<String, Object> parseMap(String payloadJson) {
    if (payloadJson == null || payloadJson.isBlank()) {
      return new LinkedHashMap<>();
    }
    try {
      return objectMapper.readValue(payloadJson, new TypeReference<LinkedHashMap<String, Object>>() {});
    } catch (Exception ex) {
      return new LinkedHashMap<>();
    }
  }

  List<Map<String, Object>> parseLineListByFields(Map<String, Object> header, List<String> lineListFields) {
    if (header == null || lineListFields == null || lineListFields.isEmpty()) {
      return List.of();
    }
    for (String field : lineListFields) {
      if (field == null || field.isBlank()) {
        continue;
      }
      List<Map<String, Object>> lines = parseLineList(header.get(field));
      if (!lines.isEmpty()) {
        return lines;
      }
    }
    return List.of();
  }

  private List<Map<String, Object>> parseLineList(Object value) {
    if (!(value instanceof List<?> listValue)) {
      return List.of();
    }
    List<Map<String, Object>> lines = new ArrayList<>();
    for (Object item : listValue) {
      if (item instanceof Map<?, ?> source) {
        Map<String, Object> line = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
          line.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        lines.add(line);
      }
    }
    return lines;
  }
}
