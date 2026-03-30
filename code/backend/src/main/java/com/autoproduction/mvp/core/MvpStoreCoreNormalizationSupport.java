package com.autoproduction.mvp.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.autoproduction.mvp.module.integration.erp.ErpDataManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service

abstract class MvpStoreCoreNormalizationSupport extends MvpStoreCoreExceptionSupport {
  protected MvpStoreCoreNormalizationSupport(ErpDataManager erpDataManager) {
    super(erpDataManager);
  }

  protected String pickProductCode(Random random) {
    List<String> products = new ArrayList<>(state.processRoutes.keySet());
    products.sort(String::compareTo);
    if (products.isEmpty()) {
      return "PROD_UNKNOWN";
    }
    return products.get(random.nextInt(products.size()));
  }

  protected static String normalizeScenario(String scenario) {
    String normalized = scenario == null ? "" : scenario.trim().toUpperCase();
    return switch (normalized) {
      case "STABLE", "TIGHT", "BREAKDOWN" -> normalized;
      default -> DEFAULT_SIM_SCENARIO;
    };
  }

  protected static String normalizeScheduleStrategy(String strategy) {
    String normalized = strategy == null ? "" : strategy.trim().toUpperCase();
    return switch (normalized) {
      case "MAX_CAPACITY_FIRST", "最大产能优先" -> STRATEGY_MAX_CAPACITY_FIRST;
      case "MIN_DELAY_FIRST", "MIN_TARDINESS_FIRST", "交期最小延期优先" -> STRATEGY_MIN_DELAY_FIRST;
      case "KEY_ORDER_FIRST", "CRITICAL_ORDER_FIRST", "关键订单优先" -> STRATEGY_KEY_ORDER_FIRST;
      default -> STRATEGY_KEY_ORDER_FIRST;
    };
  }

  protected static String scheduleStrategyNameCn(String strategyCode) {
    return SCHEDULE_STRATEGY_NAME_CN.getOrDefault(
      normalizeScheduleStrategy(strategyCode),
      SCHEDULE_STRATEGY_NAME_CN.get(STRATEGY_KEY_ORDER_FIRST)
    );
  }

  protected static String normalizeCode(String value) {
    return value == null ? "" : value.trim().toUpperCase();
  }

  protected static String normalizeCompanyCode(String value) {
    String normalized = normalizeCode(value);
    return normalized.isBlank() ? DEFAULT_COMPANY_CODE : normalized;
  }

  protected static String normalizeMaterialCode(String value) {
    return normalizeCode(value);
  }

  protected static List<Map<String, Object>> freezeMaterialRows(List<Map<String, Object>> rows) {
    if (rows == null || rows.isEmpty()) {
      return List.of();
    }
    List<Map<String, Object>> frozen = new ArrayList<>(rows.size());
    for (Map<String, Object> row : rows) {
      if (row == null) {
        continue;
      }
      frozen.add(new LinkedHashMap<>(row));
    }
    return frozen;
  }

  protected static List<Map<String, Object>> copyMaterialRows(List<Map<String, Object>> rows) {
    if (rows == null || rows.isEmpty()) {
      return List.of();
    }
    List<Map<String, Object>> copied = new ArrayList<>(rows.size());
    for (Map<String, Object> row : rows) {
      copied.add(new LinkedHashMap<>(row));
    }
    return copied;
  }

  protected static String materialListBaseCode(String materialListNo) {
    String normalized = normalizeMaterialCode(materialListNo);
    if (normalized.isBlank()) {
      return "";
    }
    int splitIndex = normalized.indexOf('_');
    if (splitIndex <= 0) {
      splitIndex = normalized.indexOf("-V");
    }
    if (splitIndex <= 0) {
      return normalized;
    }
    return normalized.substring(0, splitIndex);
  }

  protected static boolean isSameMaterialCode(String left, String right) {
    if (left == null || right == null) {
      return false;
    }
    return normalizeMaterialCode(left).equals(normalizeMaterialCode(right));
  }

  protected static String orderProcessKey(String orderNo, String processCode) {
    return (orderNo == null ? "" : orderNo.trim()) + "#" + normalizeCode(processCode);
  }

  protected static double scenarioCapacityFactor(String scenario, Random random) {
    return switch (scenario) {
      case "TIGHT" -> 0.75d + random.nextDouble() * 0.15d;
      case "BREAKDOWN" -> 0.70d + random.nextDouble() * 0.15d;
      default -> 0.95d + random.nextDouble() * 0.10d;
    };
  }

  protected static double scenarioExecutionRate(String scenario, Random random) {
    return switch (scenario) {
      case "TIGHT" -> 0.55d + random.nextDouble() * 0.25d;
      case "BREAKDOWN" -> 0.30d + random.nextDouble() * 0.35d;
      default -> 0.78d + random.nextDouble() * 0.17d;
    };
  }

  protected static int clampInt(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  protected static double round2(double value) {
    return Math.round(value * 100d) / 100d;
  }

  protected static double round3(double value) {
    return Math.round(value * 1000d) / 1000d;
  }

  protected static long elapsedMillis(long startNanos) {
    return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
  }

}