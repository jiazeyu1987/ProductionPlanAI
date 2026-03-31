package com.autoproduction.mvp.module.integration.erp.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import jakarta.persistence.*;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "erp_order_material_constraint_projection")
public class ErpOrderMaterialConstraintProjectionEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @Column(name = "snapshot_id", length = 64) private String snapshotId;
  @Column(name = "order_no", nullable = false, length = 64) private String orderNo;
  @Column(name = "product_code", length = 64) private String productCode;
  @Column(name = "first_process_code", length = 64) private String firstProcessCode;
  @Column(name = "max_schedulable_qty", precision = 18, scale = 4) private BigDecimal maxSchedulableQty;
  @Column(name = "data_status", nullable = false, length = 32) private String dataStatus;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "detail_json", nullable = false, columnDefinition = "jsonb") private JsonNode detailJson = JsonNodeFactory.instance.objectNode();
  public Long getId(){return id;} public void setId(Long id){this.id=id;}
  public String getSnapshotId(){return snapshotId;} public void setSnapshotId(String snapshotId){this.snapshotId=snapshotId;}
  public String getOrderNo(){return orderNo;} public void setOrderNo(String orderNo){this.orderNo=orderNo;}
  public String getProductCode(){return productCode;} public void setProductCode(String productCode){this.productCode=productCode;}
  public String getFirstProcessCode(){return firstProcessCode;} public void setFirstProcessCode(String firstProcessCode){this.firstProcessCode=firstProcessCode;}
  public BigDecimal getMaxSchedulableQty(){return maxSchedulableQty;} public void setMaxSchedulableQty(BigDecimal maxSchedulableQty){this.maxSchedulableQty=maxSchedulableQty;}
  public String getDataStatus(){return dataStatus;} public void setDataStatus(String dataStatus){this.dataStatus=dataStatus;}
  public JsonNode getDetailJson(){return detailJson;}
  public void setDetailJson(JsonNode detailJson){this.detailJson=detailJson == null ? JsonNodeFactory.instance.objectNode() : detailJson;}
  public void setDetailJson(String detailJson){this.detailJson=detailJson == null ? JsonNodeFactory.instance.objectNode() : JsonNodeFactory.instance.textNode(detailJson);}
}
