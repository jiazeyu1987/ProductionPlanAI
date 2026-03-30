package com.autoproduction.mvp.core;

import java.util.List;
import java.util.Map;

record ErpSqliteOrderRecord(
  String tableName,
  String recordId,
  Map<String, Object> base,
  Map<String, Object> header,
  List<Map<String, Object>> lines,
  String payloadJson
) {
  static String buildRecordId(String tableName, long fid) {
    return tableName + ":" + fid;
  }

  static String buildLineId(String recordId, int lineNo) {
    return recordId + ":" + lineNo;
  }
}
