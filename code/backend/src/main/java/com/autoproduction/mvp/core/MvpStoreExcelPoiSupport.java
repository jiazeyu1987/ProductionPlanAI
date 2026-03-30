package com.autoproduction.mvp.core;

import java.util.Map;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;

final class MvpStoreExcelPoiSupport {
  private MvpStoreExcelPoiSupport() {}

  static CellStyle createTitleStyle(Workbook workbook) {
    CellStyle style = workbook.createCellStyle();
    Font font = workbook.createFont();
    font.setBold(true);
    font.setFontHeightInPoints((short) 14);
    style.setFont(font);
    style.setAlignment(HorizontalAlignment.CENTER);
    style.setVerticalAlignment(VerticalAlignment.CENTER);
    setCellBorder(style);
    return style;
  }

  static CellStyle createHeaderStyle(Workbook workbook) {
    CellStyle style = workbook.createCellStyle();
    Font font = workbook.createFont();
    font.setBold(true);
    style.setFont(font);
    style.setAlignment(HorizontalAlignment.CENTER);
    style.setVerticalAlignment(VerticalAlignment.CENTER);
    setCellBorder(style);
    return style;
  }

  static CellStyle createBodyStyle(Workbook workbook) {
    CellStyle style = workbook.createCellStyle();
    style.setVerticalAlignment(VerticalAlignment.CENTER);
    setCellBorder(style);
    return style;
  }

  static void setCellBorder(CellStyle style) {
    style.setBorderTop(BorderStyle.THIN);
    style.setBorderBottom(BorderStyle.THIN);
    style.setBorderLeft(BorderStyle.THIN);
    style.setBorderRight(BorderStyle.THIN);
  }

  static void writeRowValues(Row row, Map<String, Object> source, String[] keys, CellStyle style) {
    for (int i = 0; i < keys.length; i += 1) {
      writeCell(row, i, source.get(keys[i]), style);
    }
  }

  static void writeCell(Row row, int columnIndex, Object value, CellStyle style) {
    Cell cell = row.createCell(columnIndex);
    if (style != null) {
      cell.setCellStyle(style);
    }
    if (value == null) {
      cell.setCellValue("");
      return;
    }
    if (value instanceof Number number) {
      cell.setCellValue(number.doubleValue());
      return;
    }
    if (value instanceof Boolean boolValue) {
      cell.setCellValue(boolValue);
      return;
    }
    cell.setCellValue(String.valueOf(value));
  }
}

