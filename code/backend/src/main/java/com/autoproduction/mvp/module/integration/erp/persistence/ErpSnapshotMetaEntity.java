package com.autoproduction.mvp.module.integration.erp.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "erp_snapshot_meta")
public class ErpSnapshotMetaEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "snapshot_id", nullable = false, unique = true, length = 64)
  private String snapshotId;
  @Column(name = "source_type", nullable = false, length = 32)
  private String sourceType;
  @Column(name = "request_id", length = 100)
  private String requestId;
  @Column(name = "operator", length = 64)
  private String operator;
  @Column(name = "status", nullable = false, length = 32)
  private String status;
  @Column(name = "version_token")
  private Long versionToken;
  @Column(name = "started_at")
  private OffsetDateTime startedAt;
  @Column(name = "finished_at")
  private OffsetDateTime finishedAt;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "detail_json", nullable = false, columnDefinition = "jsonb")
  private JsonNode detailJson = JsonNodeFactory.instance.objectNode();
  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public String getSnapshotId() { return snapshotId; }
  public void setSnapshotId(String snapshotId) { this.snapshotId = snapshotId; }
  public String getSourceType() { return sourceType; }
  public void setSourceType(String sourceType) { this.sourceType = sourceType; }
  public String getRequestId() { return requestId; }
  public void setRequestId(String requestId) { this.requestId = requestId; }
  public String getOperator() { return operator; }
  public void setOperator(String operator) { this.operator = operator; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Long getVersionToken() { return versionToken; }
  public void setVersionToken(Long versionToken) { this.versionToken = versionToken; }
  public OffsetDateTime getStartedAt() { return startedAt; }
  public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }
  public OffsetDateTime getFinishedAt() { return finishedAt; }
  public void setFinishedAt(OffsetDateTime finishedAt) { this.finishedAt = finishedAt; }
  public JsonNode getDetailJson() { return detailJson; }
  public void setDetailJson(JsonNode detailJson) { this.detailJson = detailJson == null ? JsonNodeFactory.instance.objectNode() : detailJson; }
  public void setDetailJson(String detailJson) { this.detailJson = detailJson == null ? JsonNodeFactory.instance.objectNode() : JsonNodeFactory.instance.textNode(detailJson); }
}
