package com.autoproduction.mvp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ErpSqliteOrderLoaderTest {

  @Test
  void loaderReadsSqliteAndSplitsCanonicalAndRawTables() throws Exception {
    Path sqliteFile = Files.createTempFile("mvp-erp-loader-", ".db");
    try {
      seedSqlite(sqliteFile);
      ErpSqliteOrderLoader loader = new ErpSqliteOrderLoader(new ObjectMapper(), true, sqliteFile.toString());

      List<Map<String, Object>> salesRows = loader.loadSalesOrderLines();
      assertEquals(1, salesRows.size());
      Map<String, Object> sales = salesRows.get(0);
      assertEquals("SO-0001", sales.get("sales_order_no"));
      assertEquals("MAT-001", sales.get("product_code"));
      assertNotNull(sales.get("erp_record_id"));
      assertNotNull(sales.get("erp_line_id"));
      assertFalse(sales.containsKey("erp_header_json"));
      assertFalse(sales.containsKey("erp_line_json"));

      List<Map<String, Object>> productionRows = loader.loadProductionOrders();
      assertEquals(1, productionRows.size());
      Map<String, Object> production = productionRows.get(0);
      assertEquals("MO-0001", production.get("production_order_no"));
      assertEquals("SO-0001", production.get("source_sales_order_no"));
      assertEquals("MAT-001", production.get("product_code"));
      assertEquals("生产订单", production.get("product_name_cn"));
      assertNotNull(production.get("erp_record_id"));
      assertNotNull(production.get("erp_line_id"));
      assertFalse(production.containsKey("erp_header_json"));
      assertFalse(production.containsKey("erp_line_json"));

      List<Map<String, Object>> salesHeaderRawRows = loader.loadSalesOrderHeadersRaw();
      assertEquals(1, salesHeaderRawRows.size());
      assertEquals("SO-0001", salesHeaderRawRows.get(0).get("erp_bill_no"));
      assertNotNull(salesHeaderRawRows.get(0).get("erp_header_json"));

      List<Map<String, Object>> salesLineRawRows = loader.loadSalesOrderLinesRaw();
      assertEquals(1, salesLineRawRows.size());
      assertNotNull(salesLineRawRows.get(0).get("erp_line_json"));

      List<Map<String, Object>> productionHeaderRawRows = loader.loadProductionOrderHeadersRaw();
      assertEquals(1, productionHeaderRawRows.size());
      assertEquals("MO-0001", productionHeaderRawRows.get(0).get("erp_bill_no"));
      assertNotNull(productionHeaderRawRows.get(0).get("erp_header_json"));

      List<Map<String, Object>> productionLineRawRows = loader.loadProductionOrderLinesRaw();
      assertEquals(1, productionLineRawRows.size());
      assertNotNull(productionLineRawRows.get(0).get("erp_line_json"));
    } finally {
      Files.deleteIfExists(sqliteFile);
    }
  }

  private static void seedSqlite(Path sqliteFile) throws Exception {
    try (
      Connection connection = DriverManager.getConnection("jdbc:sqlite:" + sqliteFile.toAbsolutePath());
      Statement statement = connection.createStatement()
    ) {
      statement.execute("""
        CREATE TABLE sales_orders_raw (
          run_id INTEGER NOT NULL,
          fid INTEGER NOT NULL,
          bill_no TEXT,
          bill_date TEXT,
          modify_date TEXT,
          document_status TEXT,
          org_no TEXT,
          payload_json TEXT NOT NULL,
          fetched_at TEXT NOT NULL
        )
        """);
      statement.execute("""
        CREATE TABLE production_orders_raw (
          run_id INTEGER NOT NULL,
          fid INTEGER NOT NULL,
          bill_no TEXT,
          bill_date TEXT,
          modify_date TEXT,
          document_status TEXT,
          org_no TEXT,
          payload_json TEXT NOT NULL,
          fetched_at TEXT NOT NULL
        )
        """);
    }

    String salesPayload = """
      {
        "Id": 1,
        "FFormId": "SAL_SaleOrder",
        "BillNo": "SO-0001",
        "Date": "2026-03-20T00:00:00",
        "DocumentStatus": "C",
        "SaleOrderEntry": [
          {
            "Seq": 1,
            "Qty": 10,
            "Priority": 0,
            "DeliveryDate": "2026-03-25T00:00:00",
            "MaterialId": {
              "Number": "MAT-001",
              "Name": [{"Key": 2052, "Value": "销售订单"}],
              "Specification": [{"Key": 2052, "Value": "规格A"}]
            }
          }
        ]
      }
      """;

    String productionPayload = """
      {
        "Id": 2,
        "FFormId": "PRD_MO",
        "BillNo": "MO-0001",
        "Date": "2026-03-21T00:00:00",
        "DocumentStatus": "C",
        "TreeEntity": [
          {
            "Seq": 1,
            "Qty": 20,
            "SaleOrderNo": "SO-0001",
            "SaleOrderEntrySeq": 1,
            "PlanStartDate": "2026-03-21T00:00:00",
            "PlanFinishDate": "2026-03-26T00:00:00",
            "RepQuaQty": 5,
            "MaterialId": {
              "Number": "MAT-001",
              "Name": [{"Key": 2052, "Value": "生产订单"}],
              "Specification": [{"Key": 2052, "Value": "规格B"}]
            }
          }
        ]
      }
      """;

    try (
      Connection connection = DriverManager.getConnection("jdbc:sqlite:" + sqliteFile.toAbsolutePath());
      PreparedStatement sales = connection.prepareStatement(
        "INSERT INTO sales_orders_raw (run_id,fid,bill_no,bill_date,modify_date,document_status,org_no,payload_json,fetched_at)"
          + " VALUES (?,?,?,?,?,?,?,?,?)"
      );
      PreparedStatement production = connection.prepareStatement(
        "INSERT INTO production_orders_raw (run_id,fid,bill_no,bill_date,modify_date,document_status,org_no,payload_json,fetched_at)"
          + " VALUES (?,?,?,?,?,?,?,?,?)"
      )
    ) {
      sales.setLong(1, 1L);
      sales.setLong(2, 1001L);
      sales.setString(3, "SO-0001");
      sales.setString(4, "2026-03-20T00:00:00");
      sales.setString(5, "2026-03-20T08:00:00");
      sales.setString(6, "C");
      sales.setString(7, "908");
      sales.setString(8, salesPayload);
      sales.setString(9, "2026-03-22T10:00:00");
      sales.executeUpdate();

      production.setLong(1, 1L);
      production.setLong(2, 2001L);
      production.setString(3, "MO-0001");
      production.setString(4, "2026-03-21T00:00:00");
      production.setString(5, "2026-03-21T09:00:00");
      production.setString(6, "C");
      production.setString(7, "881");
      production.setString(8, productionPayload);
      production.setString(9, "2026-03-22T10:01:00");
      production.executeUpdate();
    }
  }
}
