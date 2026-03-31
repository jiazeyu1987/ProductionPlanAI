package com.autoproduction.mvp.module.integration.erp.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "erp_material_supply_raw")
public class ErpMaterialSupplyRawEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @Column(name = "snapshot_id", length = 64) private String snapshotId;
  @Column(name = "material_code", nullable = false, length = 64) private String materialCode;
  @Column(name = "supply_type", length = 32) private String supplyType;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "row_json", nullable = false, columnDefinition = "jsonb") private JsonNode rowJson = JsonNodeFactory.instance.objectNode();
  public Long getId(){return id;} public void setId(Long id){this.id=id;}
  public String getSnapshotId(){return snapshotId;} public void setSnapshotId(String snapshotId){this.snapshotId=snapshotId;}
  public String getMaterialCode(){return materialCode;} public void setMaterialCode(String materialCode){this.materialCode=materialCode;}
  public String getSupplyType(){return supplyType;} public void setSupplyType(String supplyType){this.supplyType=supplyType;}
  public JsonNode getRowJson(){return rowJson;}
  public void setRowJson(JsonNode rowJson){this.rowJson=rowJson == null ? JsonNodeFactory.instance.objectNode() : rowJson;}
  public void setRowJson(String rowJson){this.rowJson=rowJson == null ? JsonNodeFactory.instance.objectNode() : JsonNodeFactory.instance.textNode(rowJson);}
}
