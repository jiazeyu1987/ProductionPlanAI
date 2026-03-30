package com.autoproduction.mvp.core.erp.loader;

public final class ErpLoaderValueUtils {
  private ErpLoaderValueUtils() {}

  public static String trimToEmpty(String value) {
    return value == null ? "" : value.trim();
  }

  public static String firstText(Object... values) {
    for (Object value : values) {
      if (value == null) {
        continue;
      }
      String text = String.valueOf(value).trim();
      if (!text.isEmpty() && !"null".equalsIgnoreCase(text)) {
        return text;
      }
    }
    return null;
  }

  public static Number toNumber(Object value) {
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    String text = firstText(value);
    if (text == null) {
      return null;
    }
    try {
      return Double.parseDouble(text);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  public static int toInt(Object value) {
    Number number = toNumber(value);
    return number == null ? 0 : number.intValue();
  }

  public static boolean toBoolean(Object value) {
    if (value instanceof Boolean boolValue) {
      return boolValue;
    }
    if (value instanceof Number number) {
      return number.intValue() != 0;
    }
    String text = firstText(value);
    if (text == null) {
      return false;
    }
    String normalized = text.trim().toLowerCase();
    return "true".equals(normalized)
      || "1".equals(normalized)
      || "yes".equals(normalized)
      || "y".equals(normalized);
  }

  public static Number firstNonNullNumber(Number... values) {
    if (values == null) {
      return null;
    }
    for (Number value : values) {
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  public static double round3(double value) {
    return Math.round(value * 1000d) / 1000d;
  }
}

