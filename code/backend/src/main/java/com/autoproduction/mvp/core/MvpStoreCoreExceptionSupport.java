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

abstract class MvpStoreCoreExceptionSupport extends MvpStoreRuntimeBase {
  protected MvpStoreCoreExceptionSupport(ErpDataManager erpDataManager) {
    super(erpDataManager);
  }

  protected MvpServiceException badRequest(String message) {
    return new MvpServiceException(400, "BAD_REQUEST", message, false);
  }

  protected MvpServiceException notFound(String message) {
    return new MvpServiceException(404, "NOT_FOUND", message, false);
  }

  protected static String string(Map<String, Object> payload, String key, String fallback) {
    Object value = payload == null ? null : payload.get(key);
    if (value == null) {
      return fallback;
    }
    String parsed = String.valueOf(value);
    return parsed.isBlank() ? fallback : parsed;
  }

  protected static double number(Map<String, Object> payload, String key, double fallback) {
    Object value = payload == null ? null : payload.get(key);
    if (value == null) {
      return fallback;
    }
    if (value instanceof Number n) {
      return n.doubleValue();
    }
    try {
      return Double.parseDouble(String.valueOf(value));
    } catch (NumberFormatException ignore) {
      return fallback;
    }
  }

  protected static boolean bool(Map<String, Object> payload, String key, boolean fallback) {
    Object value = payload == null ? null : payload.get(key);
    if (value == null) {
      return fallback;
    }
    if (value instanceof Boolean b) {
      return b;
    }
    if (value instanceof Number n) {
      return n.intValue() != 0;
    }
    String normalized = String.valueOf(value).trim().toLowerCase();
    if ("true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized)) {
      return true;
    }
    if ("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized)) {
      return false;
    }
    return fallback;
  }

  protected List<Map<String, Object>> maps(Object raw) {
    if (raw == null) {
      return List.of();
    }
    return objectMapper.convertValue(raw, new TypeReference<List<Map<String, Object>>>() {});
  }

  protected Map<String, Object> deepCopyMap(Map<String, Object> raw) {
    if (raw == null) {
      return new LinkedHashMap<>();
    }
    return objectMapper.convertValue(raw, new TypeReference<LinkedHashMap<String, Object>>() {});
  }

  protected List<Map<String, Object>> deepCopyList(List<Map<String, Object>> raw) {
    if (raw == null) {
      return List.of();
    }
    return objectMapper.convertValue(raw, new TypeReference<List<Map<String, Object>>>() {});
  }
}
