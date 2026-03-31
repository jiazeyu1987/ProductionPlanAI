package com.autoproduction.mvp.module.integration.erp.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "erp_purchase_order_raw")
public class ErpPurchaseOrderRawEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @Column(name = "snapshot_id", nullable = false, length = 64) private String snapshotId;
  @Column(name = "material_code", length = 64) private String materialCode;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "row_json", nullable = false, columnDefinition = "jsonb") private JsonNode rowJson = JsonNodeFactory.instance.objectNode();
  public Long getId(){return id;} public void setId(Long id){this.id=id;}
  public String getSnapshotId(){return snapshotId;} public void setSnapshotId(String snapshotId){this.snapshotId=snapshotId;}
  public String getMaterialCode(){return materialCode;} public void setMaterialCode(String materialCode){this.materialCode=materialCode;}
  public JsonNode getRowJson(){return rowJson;}
  public void setRowJson(JsonNode rowJson){this.rowJson=rowJson == null ? JsonNodeFactory.instance.objectNode() : rowJson;}
  public void setRowJson(String rowJson){this.rowJson=rowJson == null ? JsonNodeFactory.instance.objectNode() : JsonNodeFactory.instance.textNode(rowJson);}
}
