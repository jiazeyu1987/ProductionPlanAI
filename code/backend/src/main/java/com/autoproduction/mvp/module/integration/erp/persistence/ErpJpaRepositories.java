package com.autoproduction.mvp.module.integration.erp.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface ErpSnapshotMetaJpaRepository extends JpaRepository<ErpSnapshotMetaEntity, Long> {
  Optional<ErpSnapshotMetaEntity> findTopByOrderByFinishedAtDesc();
}
interface ErpProductionOrderRawJpaRepository extends JpaRepository<ErpProductionOrderRawEntity, Long> {}
interface ErpPurchaseOrderRawJpaRepository extends JpaRepository<ErpPurchaseOrderRawEntity, Long> {}
interface ErpMaterialIssueRawJpaRepository extends JpaRepository<ErpMaterialIssueRawEntity, Long> {
  List<ErpMaterialIssueRawEntity> findByProductionOrderNoOrMaterialListNo(String productionOrderNo, String materialListNo);
  List<ErpMaterialIssueRawEntity> findByProductionOrderNo(String productionOrderNo);
  List<ErpMaterialIssueRawEntity> findByMaterialListNo(String materialListNo);
  List<ErpMaterialIssueRawEntity> findByProductionOrderNoOrderByIdDesc(String productionOrderNo);
  List<ErpMaterialIssueRawEntity> findByMaterialListNoOrderByIdDesc(String materialListNo);
  void deleteByProductionOrderNo(String productionOrderNo);
  void deleteByMaterialListNo(String materialListNo);
}
interface ErpMaterialInventoryRawJpaRepository extends JpaRepository<ErpMaterialInventoryRawEntity, Long> {
  List<ErpMaterialInventoryRawEntity> findByMaterialCodeIn(List<String> materialCodes);
}
interface ErpMaterialSupplyRawJpaRepository extends JpaRepository<ErpMaterialSupplyRawEntity, Long> {
  Optional<ErpMaterialSupplyRawEntity> findTopByMaterialCode(String materialCode);
}
interface ErpOrderMaterialConstraintProjectionJpaRepository extends JpaRepository<ErpOrderMaterialConstraintProjectionEntity, Long> {
  Optional<ErpOrderMaterialConstraintProjectionEntity> findTopByOrderNo(String orderNo);
}
