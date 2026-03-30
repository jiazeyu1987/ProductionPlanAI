package com.autoproduction.mvp.core.erp.loader;

import java.util.ArrayList;
import java.util.List;

public final class ErpLoaderCsvUtils {
  private ErpLoaderCsvUtils() {}

  public static List<String> parseCsvLine(String line) {
    List<String> values = new ArrayList<>();
    if (line == null) {
      return values;
    }
    StringBuilder current = new StringBuilder();
    boolean inQuotes = false;
    for (int i = 0; i < line.length(); i += 1) {
      char ch = line.charAt(i);
      if (ch == '"') {
        if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
          current.append('"');
          i += 1;
          continue;
        }
        inQuotes = !inQuotes;
        continue;
      }
      if (ch == ',' && !inQuotes) {
        values.add(current.toString());
        current.setLength(0);
        continue;
      }
      current.append(ch);
    }
    values.add(current.toString());
    return values;
  }
}

