package com.autoproduction.mvp.module.integration.erp.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ErpSnapshotPersistenceServiceTest {

  @Test
  void persistMaterialIssuesStoresJsonObjectNodes() {
    ErpSnapshotMetaJpaRepository metaRepository = mock(ErpSnapshotMetaJpaRepository.class);
    ErpProductionOrderRawJpaRepository productionRepository = mock(ErpProductionOrderRawJpaRepository.class);
    ErpPurchaseOrderRawJpaRepository purchaseRepository = mock(ErpPurchaseOrderRawJpaRepository.class);
    ErpMaterialIssueRawJpaRepository issueRepository = mock(ErpMaterialIssueRawJpaRepository.class);
    ErpMaterialInventoryRawJpaRepository inventoryRepository = mock(ErpMaterialInventoryRawJpaRepository.class);
    ErpMaterialSupplyRawJpaRepository supplyRepository = mock(ErpMaterialSupplyRawJpaRepository.class);
    ErpOrderMaterialConstraintProjectionJpaRepository projectionRepository = mock(ErpOrderMaterialConstraintProjectionJpaRepository.class);

    List<ErpMaterialIssueRawEntity> captured = new ArrayList<>();
    doAnswer(invocation -> {
      @SuppressWarnings("unchecked")
      List<ErpMaterialIssueRawEntity> entities = (List<ErpMaterialIssueRawEntity>) invocation.getArgument(0);
      captured.addAll(entities);
      return entities;
    }).when(issueRepository).saveAll(org.mockito.ArgumentMatchers.anyIterable());

    when(projectionRepository.findTopByOrderNo("881MO091041")).thenReturn(java.util.Optional.empty());

    ErpSnapshotPersistenceService service = new ErpSnapshotPersistenceService(
      metaRepository,
      productionRepository,
      purchaseRepository,
      issueRepository,
      inventoryRepository,
      supplyRepository,
      projectionRepository,
      new ObjectMapper()
    );

    service.persistMaterialIssues(
      "ERP-TEST-001",
      "881MO091041",
      "YXN.067.005.1006_V1.0",
      List.of(Map.of(
        "child_material_code", "A001.02.012.20011",
        "child_material_name_cn", "造影导管胚管",
        "spec_model", "5F 1.17*1.68*1300mm 72D 蓝 全编织网"
      ))
    );

    verify(issueRepository).deleteByProductionOrderNo("881MO091041");
    assertEquals(1, captured.size());
    assertTrue(captured.get(0).getRowJson().isObject());
    assertEquals("造影导管胚管", captured.get(0).getRowJson().get("child_material_name_cn").asText());
  }

  @Test
  void normalizeWrappedJsonColumnsRewritesTextNodesToObjects() {
    ErpSnapshotMetaJpaRepository metaRepository = mock(ErpSnapshotMetaJpaRepository.class);
    ErpProductionOrderRawJpaRepository productionRepository = mock(ErpProductionOrderRawJpaRepository.class);
    ErpPurchaseOrderRawJpaRepository purchaseRepository = mock(ErpPurchaseOrderRawJpaRepository.class);
    ErpMaterialIssueRawJpaRepository issueRepository = mock(ErpMaterialIssueRawJpaRepository.class);
    ErpMaterialInventoryRawJpaRepository inventoryRepository = mock(ErpMaterialInventoryRawJpaRepository.class);
    ErpMaterialSupplyRawJpaRepository supplyRepository = mock(ErpMaterialSupplyRawJpaRepository.class);
    ErpOrderMaterialConstraintProjectionJpaRepository projectionRepository = mock(ErpOrderMaterialConstraintProjectionJpaRepository.class);

    ErpMaterialIssueRawEntity issue = new ErpMaterialIssueRawEntity();
    issue.setProductionOrderNo("881MO091041");
    issue.setChildMaterialCode("A001.02.012.20011");
    issue.setRowJson("\"{\\\"child_material_code\\\":\\\"A001.02.012.20011\\\",\\\"child_material_name_cn\\\":\\\"造影导管胚管\\\"}\"");

    when(metaRepository.findAll()).thenReturn(List.of());
    when(productionRepository.findAll()).thenReturn(List.of());
    when(purchaseRepository.findAll()).thenReturn(List.of());
    when(issueRepository.findAll()).thenReturn(List.of(issue));
    when(inventoryRepository.findAll()).thenReturn(List.of());
    when(supplyRepository.findAll()).thenReturn(List.of());
    when(projectionRepository.findAll()).thenReturn(List.of());

    ErpSnapshotPersistenceService service = new ErpSnapshotPersistenceService(
      metaRepository,
      productionRepository,
      purchaseRepository,
      issueRepository,
      inventoryRepository,
      supplyRepository,
      projectionRepository,
      new ObjectMapper()
    );

    Map<String, Integer> updated = service.normalizeWrappedJsonColumns();

    assertEquals(1, updated.get("erp_material_issue_raw"));
    assertTrue(issue.getRowJson().isObject());
    assertEquals("造影导管胚管", issue.getRowJson().get("child_material_name_cn").asText());
  }

  @Test
  void loadMaterialIssuesReadsJsonStringWrappedRows() {
    ErpSnapshotMetaJpaRepository metaRepository = mock(ErpSnapshotMetaJpaRepository.class);
    ErpProductionOrderRawJpaRepository productionRepository = mock(ErpProductionOrderRawJpaRepository.class);
    ErpPurchaseOrderRawJpaRepository purchaseRepository = mock(ErpPurchaseOrderRawJpaRepository.class);
    ErpMaterialIssueRawJpaRepository issueRepository = mock(ErpMaterialIssueRawJpaRepository.class);
    ErpMaterialInventoryRawJpaRepository inventoryRepository = mock(ErpMaterialInventoryRawJpaRepository.class);
    ErpMaterialSupplyRawJpaRepository supplyRepository = mock(ErpMaterialSupplyRawJpaRepository.class);
    ErpOrderMaterialConstraintProjectionJpaRepository projectionRepository = mock(ErpOrderMaterialConstraintProjectionJpaRepository.class);

    ErpMaterialIssueRawEntity entity = new ErpMaterialIssueRawEntity();
    entity.setProductionOrderNo("881MO091041");
    entity.setMaterialListNo("YXN.067.005.1006_V1.0");
    entity.setChildMaterialCode("A001.02.012.20011");
    entity.setRowJson(
      "\"{\\\"child_material_code\\\":\\\"A001.02.012.20011\\\",\\\"child_material_name_cn\\\":\\\"造影导管胚管\\\",\\\"spec_model\\\":\\\"5F 1.17*1.68*1300mm 72D 蓝 全编织网\\\",\\\"source_bill_type\\\":\\\"ENG_BOM\\\"}\""
    );
    when(issueRepository.findByProductionOrderNoOrderByIdDesc("881MO091041")).thenReturn(List.of(entity));

    ErpSnapshotPersistenceService service = new ErpSnapshotPersistenceService(
      metaRepository,
      productionRepository,
      purchaseRepository,
      issueRepository,
      inventoryRepository,
      supplyRepository,
      projectionRepository,
      new ObjectMapper()
    );

    List<Map<String, Object>> rows = service.loadMaterialIssues("881MO091041", null);

    assertEquals(1, rows.size());
    assertEquals("A001.02.012.20011", rows.get(0).get("child_material_code"));
    assertEquals("造影导管胚管", rows.get(0).get("child_material_name_cn"));
    assertEquals("5F 1.17*1.68*1300mm 72D 蓝 全编织网", rows.get(0).get("spec_model"));
    assertEquals("ENG_BOM", rows.get(0).get("source_bill_type"));
  }

  @Test
  void loadMaterialIssuesFallsBackForLegacyNonJsonRows() {
    ErpSnapshotMetaJpaRepository metaRepository = mock(ErpSnapshotMetaJpaRepository.class);
    ErpProductionOrderRawJpaRepository productionRepository = mock(ErpProductionOrderRawJpaRepository.class);
    ErpPurchaseOrderRawJpaRepository purchaseRepository = mock(ErpPurchaseOrderRawJpaRepository.class);
    ErpMaterialIssueRawJpaRepository issueRepository = mock(ErpMaterialIssueRawJpaRepository.class);
    ErpMaterialInventoryRawJpaRepository inventoryRepository = mock(ErpMaterialInventoryRawJpaRepository.class);
    ErpMaterialSupplyRawJpaRepository supplyRepository = mock(ErpMaterialSupplyRawJpaRepository.class);
    ErpOrderMaterialConstraintProjectionJpaRepository projectionRepository = mock(ErpOrderMaterialConstraintProjectionJpaRepository.class);

    ErpMaterialIssueRawEntity entity = new ErpMaterialIssueRawEntity();
    entity.setProductionOrderNo("881MO091048");
    entity.setMaterialListNo("YXN.041.011.1004_V1.1");
    entity.setChildMaterialCode("A003.017.01.004");
    entity.setRowJson(
      "{production_order_no=881MO091048, child_material_code=A003.017.01.004, child_material_name_cn=弯曲连接件, spec_model=(83819) 本色, required_qty=227}"
    );
    when(issueRepository.findByProductionOrderNoOrderByIdDesc("881MO091048")).thenReturn(List.of(entity));

    ErpSnapshotPersistenceService service = new ErpSnapshotPersistenceService(
      metaRepository,
      productionRepository,
      purchaseRepository,
      issueRepository,
      inventoryRepository,
      supplyRepository,
      projectionRepository,
      new ObjectMapper()
    );

    List<Map<String, Object>> rows = service.loadMaterialIssues("881MO091048", "YXN.041.011.1004_V1.1");

    assertEquals(1, rows.size());
    assertEquals("881MO091048", rows.get(0).get("production_order_no"));
    assertEquals("A003.017.01.004", rows.get(0).get("child_material_code"));
    assertEquals("弯曲连接件", rows.get(0).get("child_material_name_cn"));
    assertEquals("(83819) 本色", rows.get(0).get("spec_model"));
    assertEquals("227", String.valueOf(rows.get(0).get("required_qty")));
    assertTrue(rows.get(0).containsKey("_legacy_row_json"));
  }

  @Test
  void loadMaterialIssuesKeepsOnlyLatestPersistedRows() {
    ErpSnapshotMetaJpaRepository metaRepository = mock(ErpSnapshotMetaJpaRepository.class);
    ErpProductionOrderRawJpaRepository productionRepository = mock(ErpProductionOrderRawJpaRepository.class);
    ErpPurchaseOrderRawJpaRepository purchaseRepository = mock(ErpPurchaseOrderRawJpaRepository.class);
    ErpMaterialIssueRawJpaRepository issueRepository = mock(ErpMaterialIssueRawJpaRepository.class);
    ErpMaterialInventoryRawJpaRepository inventoryRepository = mock(ErpMaterialInventoryRawJpaRepository.class);
    ErpMaterialSupplyRawJpaRepository supplyRepository = mock(ErpMaterialSupplyRawJpaRepository.class);
    ErpOrderMaterialConstraintProjectionJpaRepository projectionRepository = mock(ErpOrderMaterialConstraintProjectionJpaRepository.class);

    ErpMaterialIssueRawEntity latest = new ErpMaterialIssueRawEntity();
    latest.setId(20L);
    latest.setSnapshotId("ERP-002");
    latest.setProductionOrderNo("881MO091041");
    latest.setChildMaterialCode("A001.02.014.10022");
    latest.setRowJson("{\"child_material_code\":\"A001.02.014.10022\",\"issue_qty\":13,\"required_qty\":13}");

    ErpMaterialIssueRawEntity oldSameSnapshot = new ErpMaterialIssueRawEntity();
    oldSameSnapshot.setId(19L);
    oldSameSnapshot.setSnapshotId("ERP-002");
    oldSameSnapshot.setProductionOrderNo("881MO091041");
    oldSameSnapshot.setChildMaterialCode("A001.02.014.10022");
    oldSameSnapshot.setRowJson("{\"child_material_code\":\"A001.02.014.10022\",\"issue_qty\":0,\"required_qty\":0}");

    ErpMaterialIssueRawEntity oldSnapshot = new ErpMaterialIssueRawEntity();
    oldSnapshot.setId(10L);
    oldSnapshot.setSnapshotId("ERP-001");
    oldSnapshot.setProductionOrderNo("881MO091041");
    oldSnapshot.setChildMaterialCode("A001.02.014.10022");
    oldSnapshot.setRowJson("{\"child_material_code\":\"A001.02.014.10022\",\"issue_qty\":0,\"required_qty\":0}");

    when(issueRepository.findByProductionOrderNoOrderByIdDesc("881MO091041"))
      .thenReturn(List.of(latest, oldSameSnapshot, oldSnapshot));

    ErpSnapshotPersistenceService service = new ErpSnapshotPersistenceService(
      metaRepository,
      productionRepository,
      purchaseRepository,
      issueRepository,
      inventoryRepository,
      supplyRepository,
      projectionRepository,
      new ObjectMapper()
    );

    List<Map<String, Object>> rows = service.loadMaterialIssues("881MO091041", null);

    assertEquals(1, rows.size());
    assertEquals(13, ((Number) rows.get(0).get("issue_qty")).intValue());
    assertEquals(13, ((Number) rows.get(0).get("required_qty")).intValue());
  }
}
