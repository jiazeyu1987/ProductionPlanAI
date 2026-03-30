package com.autoproduction.mvp.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ErpSqliteSnapshotReader {
  private static final Logger log = LoggerFactory.getLogger(ErpSqliteSnapshotReader.class);

  private final boolean useRealOrders;
  private final String sqlitePath;
  private final ErpOrderPayloadMapper payloadMapper;

  ErpSqliteSnapshotReader(boolean useRealOrders, String sqlitePath, ErpOrderPayloadMapper payloadMapper) {
    this.useRealOrders = useRealOrders;
    this.sqlitePath = sqlitePath;
    this.payloadMapper = payloadMapper;
  }

  List<ErpSqliteOrderRecord> loadRecords(String tableName, List<String> lineListFields) {
    if (!useRealOrders || sqlitePath == null || sqlitePath.isBlank()) {
      return List.of();
    }
    Path path = Path.of(sqlitePath);
    if (!Files.exists(path)) {
      return List.of();
    }
    String sql = "SELECT run_id, fid, bill_no, bill_date, modify_date, document_status, org_no, payload_json, fetched_at"
      + " FROM " + tableName + " ORDER BY fid DESC";
    List<ErpSqliteOrderRecord> rows = new ArrayList<>();
    try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath())) {
      if (!tableExists(connection, tableName)) {
        return List.of();
      }
      try (
        PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet resultSet = statement.executeQuery()
      ) {
        while (resultSet.next()) {
          Map<String, Object> base = new LinkedHashMap<>();
          base.put("run_id", resultSet.getLong("run_id"));
          base.put("fid", resultSet.getLong("fid"));
          base.put("bill_no", resultSet.getString("bill_no"));
          base.put("bill_date", resultSet.getString("bill_date"));
          base.put("modify_date", resultSet.getString("modify_date"));
          base.put("document_status", resultSet.getString("document_status"));
          base.put("org_no", resultSet.getString("org_no"));
          base.put("fetched_at", resultSet.getString("fetched_at"));
          String payloadJson = resultSet.getString("payload_json");
          Map<String, Object> header = parseMap(payloadJson);
          List<Map<String, Object>> lines = parseLineListByFields(header, lineListFields);
          if (lines.isEmpty()) {
            lines = List.of(new LinkedHashMap<>());
          }
          rows.add(new ErpSqliteOrderRecord(
            tableName,
            ErpSqliteOrderRecord.buildRecordId(tableName, resultSet.getLong("fid")),
            base,
            header,
            lines,
            payloadJson
          ));
        }
      }
      return rows;
    } catch (Exception ex) {
      log.warn("Failed to load ERP real orders from sqlite file: {}", path, ex);
      return List.of();
    }
  }

  boolean hasLocalSqliteSnapshot() {
    if (!useRealOrders || sqlitePath == null || sqlitePath.isBlank()) {
      return false;
    }
    try {
      Path path = Path.of(sqlitePath);
      if (!Files.exists(path) || Files.isDirectory(path)) {
        return false;
      }
      return hasAnyExpectedTable(path);
    } catch (RuntimeException ex) {
      return false;
    }
  }

  boolean hasLocalSqliteTable(String tableName) {
    if (!useRealOrders || sqlitePath == null || sqlitePath.isBlank()) {
      return false;
    }
    if (tableName == null || tableName.isBlank()) {
      return false;
    }
    try {
      Path path = Path.of(sqlitePath);
      if (!Files.exists(path) || Files.isDirectory(path)) {
        return false;
      }
      String sql = "SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1";
      try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath())) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
          statement.setString(1, tableName);
          try (ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next();
          }
        }
      }
    } catch (Exception ex) {
      return false;
    }
  }

  private boolean hasAnyExpectedTable(Path path) {
    List<String> candidates = List.of(
      "sales_orders_raw",
      "production_orders_raw",
      "purchase_orders_raw"
    );
    String sql = "SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1";
    try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath())) {
      for (String tableName : candidates) {
        if (tableName == null || tableName.isBlank()) {
          continue;
        }
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
          statement.setString(1, tableName);
          try (ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
              return true;
            }
          }
        }
      }
      return false;
    } catch (Exception ex) {
      return false;
    }
  }

  private Map<String, Object> parseMap(String payloadJson) {
    return payloadMapper.parseMap(payloadJson);
  }

  private List<Map<String, Object>> parseLineListByFields(Map<String, Object> header, List<String> lineListFields) {
    return payloadMapper.parseLineListByFields(header, lineListFields);
  }

  private boolean tableExists(Connection connection, String tableName) {
    String sql = "SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1";
    try (
      PreparedStatement statement = connection.prepareStatement(sql)
    ) {
      statement.setString(1, tableName);
      try (ResultSet resultSet = statement.executeQuery()) {
        return resultSet.next();
      }
    } catch (Exception ex) {
      return false;
    }
  }
}
