package com.autoproduction.mvp.module.integration.erp.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "erp_material_issue_raw")
public class ErpMaterialIssueRawEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @Column(name = "snapshot_id", length = 64) private String snapshotId;
  @Column(name = "production_order_no", length = 64) private String productionOrderNo;
  @Column(name = "material_list_no", length = 64) private String materialListNo;
  @Column(name = "child_material_code", length = 64) private String childMaterialCode;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "row_json", nullable = false, columnDefinition = "jsonb") private JsonNode rowJson = JsonNodeFactory.instance.objectNode();
  public Long getId(){return id;} public void setId(Long id){this.id=id;}
  public String getSnapshotId(){return snapshotId;} public void setSnapshotId(String snapshotId){this.snapshotId=snapshotId;}
  public String getProductionOrderNo(){return productionOrderNo;} public void setProductionOrderNo(String productionOrderNo){this.productionOrderNo=productionOrderNo;}
  public String getMaterialListNo(){return materialListNo;} public void setMaterialListNo(String materialListNo){this.materialListNo=materialListNo;}
  public String getChildMaterialCode(){return childMaterialCode;} public void setChildMaterialCode(String childMaterialCode){this.childMaterialCode=childMaterialCode;}
  public JsonNode getRowJson(){return rowJson;}
  public void setRowJson(JsonNode rowJson){this.rowJson=rowJson == null ? JsonNodeFactory.instance.objectNode() : rowJson;}
  public void setRowJson(String rowJson){this.rowJson=rowJson == null ? JsonNodeFactory.instance.objectNode() : JsonNodeFactory.instance.textNode(rowJson);}
}
