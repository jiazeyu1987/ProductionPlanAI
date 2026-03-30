package com.autoproduction.mvp.core;

import static com.autoproduction.mvp.core.erp.loader.ErpLoaderCsvUtils.parseCsvLine;
import static com.autoproduction.mvp.core.erp.loader.ErpLoaderValueUtils.firstText;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ErpSqliteCsvFallbackReader {
  private static final Logger log = LoggerFactory.getLogger(ErpSqliteCsvFallbackReader.class);

  private final String productionOrderCsvPath;
  private final String erpDemoOutDir;
  private final ErpSqliteOrderValidator validator;
  private final ErpSqliteOrderRowMapper rowMapper;

  ErpSqliteCsvFallbackReader(
    String productionOrderCsvPath,
    String erpDemoOutDir,
    ErpSqliteOrderValidator validator,
    ErpSqliteOrderRowMapper rowMapper
  ) {
    this.productionOrderCsvPath = productionOrderCsvPath == null ? "" : productionOrderCsvPath.trim();
    this.erpDemoOutDir = erpDemoOutDir == null ? "" : erpDemoOutDir.trim();
    this.validator = validator;
    this.rowMapper = rowMapper;
  }

  List<Map<String, Object>> loadProductionOrdersFromCsv() {
    Path csvPath = resolveProductionOrderCsvPath();
    if (csvPath == null) {
      return List.of();
    }
    List<Map<String, Object>> csvRows = readCsvRows(csvPath);
    List<Map<String, Object>> rows = new ArrayList<>();
    for (int i = 0; i < csvRows.size(); i += 1) {
      rows.add(rowMapper.toProductionOrderRowFromApi(csvRows.get(i), i + 1));
    }
    return rows;
  }

  List<Map<String, Object>> loadProductionMaterialIssuesFromCsvByOrder(String productionOrderNo, String materialListNo) {
    Path csvPath = resolvePickMaterialCsvPath();
    if (csvPath == null) {
      return List.of();
    }
    List<Map<String, Object>> csvRows = readCsvRows(csvPath);
    List<Map<String, Object>> rows = new ArrayList<>();

    if (materialListNo != null) {
      for (int i = 0; i < csvRows.size(); i += 1) {
        Map<String, Object> csvRow = csvRows.get(i);
        String sourceBillNo = firstText(csvRow.get("FSrcBillNo"), csvRow.get("source_bill_no"));
        if (!validator.equalsIgnoreCaseSafe(sourceBillNo, materialListNo)) {
          continue;
        }
        csvRow.putIfAbsent("erp_source_table", "ERP_CSV_PRODUCTION_MATERIAL_ISSUE");
        csvRow.putIfAbsent("erp_form_id", "PRD_PickMtrl");
        rows.add(rowMapper.toProductionMaterialIssueRowFromApi(csvRow, i + 1));
      }
      if (!rows.isEmpty()) {
        return rows;
      }
    }

    if (productionOrderNo != null) {
      for (int i = 0; i < csvRows.size(); i += 1) {
        Map<String, Object> csvRow = csvRows.get(i);
        String sourceProductionOrderNo = firstText(
          csvRow.get("FMoBillNo"),
          csvRow.get("FMOBillNO"),
          csvRow.get("FPrdMoNo"),
          csvRow.get("source_production_order_no")
        );
        if (validator.equalsIgnoreCaseSafe(sourceProductionOrderNo, productionOrderNo)) {
          csvRow.putIfAbsent("erp_source_table", "ERP_CSV_PRODUCTION_MATERIAL_ISSUE");
          csvRow.putIfAbsent("erp_form_id", "PRD_PickMtrl");
          rows.add(rowMapper.toProductionMaterialIssueRowFromApi(csvRow, i + 1));
        }
      }
      if (!rows.isEmpty()) {
        return rows;
      }
      for (int i = 0; i < csvRows.size(); i += 1) {
        Map<String, Object> csvRow = csvRows.get(i);
        String sourceBillNo = firstText(csvRow.get("FSrcBillNo"), csvRow.get("source_bill_no"));
        if (validator.equalsIgnoreCaseSafe(sourceBillNo, productionOrderNo)) {
          csvRow.putIfAbsent("erp_source_table", "ERP_CSV_PRODUCTION_MATERIAL_ISSUE");
          csvRow.putIfAbsent("erp_form_id", "PRD_PickMtrl");
          rows.add(rowMapper.toProductionMaterialIssueRowFromApi(csvRow, i + 1));
        }
      }
    }
    return rows;
  }

  private Path resolveProductionOrderCsvPath() {
    Path explicit = resolveExplicitCsvPath(productionOrderCsvPath);
    if (explicit != null) {
      return explicit;
    }
    return resolveLatestDemoCsv("production_order.csv");
  }

  private Path resolvePickMaterialCsvPath() {
    return resolveLatestDemoCsv("pick_material.csv");
  }

  private Path resolveExplicitCsvPath(String rawPath) {
    if (rawPath == null || rawPath.isBlank()) {
      return null;
    }
    try {
      Path path = Path.of(rawPath);
      return Files.exists(path) ? path : null;
    } catch (Exception ex) {
      return null;
    }
  }

  private Path resolveLatestDemoCsv(String fileName) {
    if (erpDemoOutDir == null || erpDemoOutDir.isBlank()) {
      return null;
    }
    try {
      Path baseDir = Path.of(erpDemoOutDir);
      if (!Files.exists(baseDir) || !Files.isDirectory(baseDir)) {
        return null;
      }
      List<Path> candidates = new ArrayList<>();
      Path direct = baseDir.resolve(fileName);
      if (Files.exists(direct)) {
        candidates.add(direct);
      }
      try (var stream = Files.list(baseDir)) {
        stream
          .filter(Files::isDirectory)
          .forEach(dir -> {
            Path candidate = dir.resolve(fileName);
            if (Files.exists(candidate)) {
              candidates.add(candidate);
            }
          });
      }
      if (candidates.isEmpty()) {
        return null;
      }
      candidates.sort(Comparator.comparingLong(this::safeLastModifiedMillis).reversed());
      for (Path candidate : candidates) {
        if (!looksLikeNonEmptyCsv(candidate)) {
          continue;
        }
        return candidate;
      }
      return null;
    } catch (Exception ex) {
      return null;
    }
  }

  private boolean looksLikeNonEmptyCsv(Path csvPath) {
    if (csvPath == null || !Files.exists(csvPath) || !Files.isRegularFile(csvPath)) {
      return false;
    }
    try {
      if (Files.size(csvPath) < 32L) {
        return false;
      }
    } catch (Exception ignore) {
      return false;
    }
    try (BufferedReader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
      String headerLine = reader.readLine();
      if (headerLine == null || headerLine.isBlank()) {
        return false;
      }
      String dataLine = reader.readLine();
      return dataLine != null && !dataLine.isBlank();
    } catch (Exception ex) {
      return false;
    }
  }

  private long safeLastModifiedMillis(Path path) {
    try {
      return Files.getLastModifiedTime(path).toMillis();
    } catch (Exception ex) {
      return 0L;
    }
  }

  private List<Map<String, Object>> readCsvRows(Path csvPath) {
    if (csvPath == null || !Files.exists(csvPath)) {
      return List.of();
    }
    List<Map<String, Object>> rows = new ArrayList<>();
    try (BufferedReader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
      String headerLine = reader.readLine();
      if (headerLine == null) {
        return List.of();
      }
      List<String> headers = parseCsvLine(headerLine);
      if (!headers.isEmpty()) {
        headers.set(0, headers.get(0).replace("\uFEFF", ""));
      }
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank()) {
          continue;
        }
        List<String> values = parseCsvLine(line);
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i += 1) {
          String key = headers.get(i);
          if (key == null || key.isBlank()) {
            continue;
          }
          row.put(key, i < values.size() ? values.get(i) : "");
        }
        rows.add(row);
      }
    } catch (Exception ex) {
      log.warn("Failed to read ERP csv rows from {}.", csvPath, ex);
      return List.of();
    }
    return rows;
  }
}
