package com.autoproduction.mvp.module.integration.erp.persistence;

import com.autoproduction.mvp.module.integration.erp.manager.ErpSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ErpSnapshotPersistenceService {
  private static final Logger log = LoggerFactory.getLogger(ErpSnapshotPersistenceService.class);

  private final ErpSnapshotMetaJpaRepository metaRepository;
  private final ErpProductionOrderRawJpaRepository productionRepository;
  private final ErpPurchaseOrderRawJpaRepository purchaseRepository;
  private final ErpMaterialIssueRawJpaRepository issueRepository;
  private final ErpMaterialInventoryRawJpaRepository inventoryRepository;
  private final ErpMaterialSupplyRawJpaRepository supplyRepository;
  private final ErpOrderMaterialConstraintProjectionJpaRepository projectionRepository;
  private final ObjectMapper objectMapper;

  public ErpSnapshotPersistenceService(
    ErpSnapshotMetaJpaRepository metaRepository,
    ErpProductionOrderRawJpaRepository productionRepository,
    ErpPurchaseOrderRawJpaRepository purchaseRepository,
    ErpMaterialIssueRawJpaRepository issueRepository,
    ErpMaterialInventoryRawJpaRepository inventoryRepository,
    ErpMaterialSupplyRawJpaRepository supplyRepository,
    ErpOrderMaterialConstraintProjectionJpaRepository projectionRepository,
    ObjectMapper objectMapper
  ) {
    this.metaRepository = metaRepository;
    this.productionRepository = productionRepository;
    this.purchaseRepository = purchaseRepository;
    this.issueRepository = issueRepository;
    this.inventoryRepository = inventoryRepository;
    this.supplyRepository = supplyRepository;
    this.projectionRepository = projectionRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public String persistSnapshot(
    ErpSnapshot snapshot,
    String sourceType,
    String requestId,
    String operator,
    long versionToken,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt
  ) {
    String snapshotId = "ERP-" + versionToken;
    ErpSnapshotMetaEntity meta = new ErpSnapshotMetaEntity();
    meta.setSnapshotId(snapshotId);
    meta.setSourceType(sourceType);
    meta.setRequestId(requestId);
    meta.setOperator(operator);
    meta.setStatus("SUCCESS");
    meta.setVersionToken(versionToken);
    meta.setStartedAt(startedAt);
    meta.setFinishedAt(finishedAt);
    meta.setDetailJson(toJsonNode(Map.of("counts", snapshot.toCountMap())));
    metaRepository.save(meta);

    List<ErpProductionOrderRawEntity> productionRows = new ArrayList<>();
    for (Map<String, Object> row : snapshot.productionOrders()) {
      ErpProductionOrderRawEntity entity = new ErpProductionOrderRawEntity();
      entity.setSnapshotId(snapshotId);
      entity.setOrderNo(text(row, "production_order_no", "order_no"));
      entity.setMaterialCode(text(row, "product_code", "material_code"));
      entity.setRowJson(toJsonNode(row));
      productionRows.add(entity);
    }
    productionRepository.saveAll(productionRows);

    List<ErpPurchaseOrderRawEntity> purchaseRows = new ArrayList<>();
    for (Map<String, Object> row : snapshot.purchaseOrders()) {
      ErpPurchaseOrderRawEntity entity = new ErpPurchaseOrderRawEntity();
      entity.setSnapshotId(snapshotId);
      entity.setMaterialCode(text(row, "material_code", "product_code"));
      entity.setRowJson(toJsonNode(row));
      purchaseRows.add(entity);
    }
    purchaseRepository.saveAll(purchaseRows);
    return snapshotId;
  }

  @Transactional
  public void persistMaterialIssues(String snapshotId, String orderNo, String materialListNo, List<Map<String, Object>> rows) {
    if (orderNo != null && !orderNo.isBlank()) {
      issueRepository.deleteByProductionOrderNo(orderNo);
    } else if (materialListNo != null && !materialListNo.isBlank()) {
      issueRepository.deleteByMaterialListNo(materialListNo);
    }
    List<ErpMaterialIssueRawEntity> entities = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      ErpMaterialIssueRawEntity entity = new ErpMaterialIssueRawEntity();
      entity.setSnapshotId(snapshotId);
      entity.setProductionOrderNo(orderNo);
      entity.setMaterialListNo(materialListNo);
      entity.setChildMaterialCode(text(row, "child_material_code", "material_code"));
      entity.setRowJson(toJsonNode(row));
      entities.add(entity);
    }
    issueRepository.saveAll(entities);
    if (orderNo == null || orderNo.isBlank()) {
      return;
    }
    ErpOrderMaterialConstraintProjectionEntity projection = projectionRepository.findTopByOrderNo(orderNo).orElseGet(ErpOrderMaterialConstraintProjectionEntity::new);
    projection.setSnapshotId(snapshotId);
    projection.setOrderNo(orderNo);
    projection.setDataStatus("RAW_LOADED");
    projection.setDetailJson(toJsonNode(Map.of("material_issues", rows)));
    projectionRepository.save(projection);
  }

  @Transactional
  public void persistInventoryRows(String snapshotId, Map<String, Map<String, Object>> rowsByCode) {
    List<ErpMaterialInventoryRawEntity> entities = new ArrayList<>();
    for (Map.Entry<String, Map<String, Object>> entry : rowsByCode.entrySet()) {
      ErpMaterialInventoryRawEntity entity = new ErpMaterialInventoryRawEntity();
      entity.setSnapshotId(snapshotId);
      entity.setMaterialCode(entry.getKey());
      entity.setRowJson(toJsonNode(entry.getValue()));
      entities.add(entity);
    }
    inventoryRepository.saveAll(entities);
  }

  @Transactional
  public void persistSupplyRow(String snapshotId, String materialCode, Map<String, Object> row) {
    ErpMaterialSupplyRawEntity entity = supplyRepository.findTopByMaterialCode(materialCode).orElseGet(ErpMaterialSupplyRawEntity::new);
    entity.setSnapshotId(snapshotId);
    entity.setMaterialCode(materialCode);
    entity.setSupplyType(text(row, "supply_type", null));
    entity.setRowJson(toJsonNode(row));
    supplyRepository.save(entity);
  }

  public List<Map<String, Object>> loadMaterialIssues(String orderNo, String materialListNo) {
    if (orderNo != null && !orderNo.isBlank()) {
      return readLatestMaterialIssueBatch(issueRepository.findByProductionOrderNoOrderByIdDesc(orderNo));
    }
    if (materialListNo != null && !materialListNo.isBlank()) {
      return readLatestMaterialIssueBatch(issueRepository.findByMaterialListNoOrderByIdDesc(materialListNo));
    }
    return List.of();
  }

  public Map<String, Map<String, Object>> loadInventoryRows(List<String> materialCodes) {
    Map<String, Map<String, Object>> out = new LinkedHashMap<>();
    for (ErpMaterialInventoryRawEntity entity : inventoryRepository.findByMaterialCodeIn(materialCodes)) {
      out.put(entity.getMaterialCode(), readJsonMap(entity));
    }
    return out;
  }

  public Map<String, Object> loadSupplyRow(String materialCode) {
    return supplyRepository.findTopByMaterialCode(materialCode).map(this::readJsonMap).orElse(Map.of());
  }

  public String latestSnapshotId() {
    return metaRepository.findTopByOrderByFinishedAtDesc().map(ErpSnapshotMetaEntity::getSnapshotId).orElse(null);
  }

  @Transactional
  public Map<String, Integer> normalizeWrappedJsonColumns() {
    int metaUpdated = 0;
    int productionUpdated = 0;
    int purchaseUpdated = 0;
    int issueUpdated = 0;
    int inventoryUpdated = 0;
    int supplyUpdated = 0;
    int projectionUpdated = 0;

    List<ErpSnapshotMetaEntity> metaEntities = metaRepository.findAll();
    for (ErpSnapshotMetaEntity entity : metaEntities) {
      JsonNode normalized = normalizeStoredJsonNode(entity.getDetailJson());
      if (normalized != entity.getDetailJson()) {
        entity.setDetailJson(normalized);
        metaUpdated += 1;
      }
    }
    if (metaUpdated > 0) {
      metaRepository.saveAll(metaEntities);
    }

    List<ErpProductionOrderRawEntity> productionEntities = productionRepository.findAll();
    for (ErpProductionOrderRawEntity entity : productionEntities) {
      JsonNode normalized = normalizeStoredJsonNode(entity.getRowJson());
      if (normalized != entity.getRowJson()) {
        entity.setRowJson(normalized);
        productionUpdated += 1;
      }
    }
    if (productionUpdated > 0) {
      productionRepository.saveAll(productionEntities);
    }

    List<ErpPurchaseOrderRawEntity> purchaseEntities = purchaseRepository.findAll();
    for (ErpPurchaseOrderRawEntity entity : purchaseEntities) {
      JsonNode normalized = normalizeStoredJsonNode(entity.getRowJson());
      if (normalized != entity.getRowJson()) {
        entity.setRowJson(normalized);
        purchaseUpdated += 1;
      }
    }
    if (purchaseUpdated > 0) {
      purchaseRepository.saveAll(purchaseEntities);
    }

    List<ErpMaterialIssueRawEntity> issueEntities = issueRepository.findAll();
    for (ErpMaterialIssueRawEntity entity : issueEntities) {
      JsonNode normalized = normalizeStoredJsonNode(entity.getRowJson());
      if (normalized != entity.getRowJson()) {
        entity.setRowJson(normalized);
        issueUpdated += 1;
      }
    }
    if (issueUpdated > 0) {
      issueRepository.saveAll(issueEntities);
    }

    List<ErpMaterialInventoryRawEntity> inventoryEntities = inventoryRepository.findAll();
    for (ErpMaterialInventoryRawEntity entity : inventoryEntities) {
      JsonNode normalized = normalizeStoredJsonNode(entity.getRowJson());
      if (normalized != entity.getRowJson()) {
        entity.setRowJson(normalized);
        inventoryUpdated += 1;
      }
    }
    if (inventoryUpdated > 0) {
      inventoryRepository.saveAll(inventoryEntities);
    }

    List<ErpMaterialSupplyRawEntity> supplyEntities = supplyRepository.findAll();
    for (ErpMaterialSupplyRawEntity entity : supplyEntities) {
      JsonNode normalized = normalizeStoredJsonNode(entity.getRowJson());
      if (normalized != entity.getRowJson()) {
        entity.setRowJson(normalized);
        supplyUpdated += 1;
      }
    }
    if (supplyUpdated > 0) {
      supplyRepository.saveAll(supplyEntities);
    }

    List<ErpOrderMaterialConstraintProjectionEntity> projectionEntities = projectionRepository.findAll();
    for (ErpOrderMaterialConstraintProjectionEntity entity : projectionEntities) {
      JsonNode normalized = normalizeStoredJsonNode(entity.getDetailJson());
      if (normalized != entity.getDetailJson()) {
        entity.setDetailJson(normalized);
        projectionUpdated += 1;
      }
    }
    if (projectionUpdated > 0) {
      projectionRepository.saveAll(projectionEntities);
    }

    return Map.of(
      "erp_snapshot_meta", metaUpdated,
      "erp_production_order_raw", productionUpdated,
      "erp_purchase_order_raw", purchaseUpdated,
      "erp_material_issue_raw", issueUpdated,
      "erp_material_inventory_raw", inventoryUpdated,
      "erp_material_supply_raw", supplyUpdated,
      "erp_order_material_constraint_projection", projectionUpdated
    );
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value == null ? Map.of() : value);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to serialize ERP row.", ex);
    }
  }

  private JsonNode toJsonNode(Object value) {
    return objectMapper.valueToTree(value == null ? Map.of() : value);
  }

  private JsonNode normalizeStoredJsonNode(JsonNode node) {
    if (node == null || node.isNull()) {
      return JsonNodeFactory.instance.objectNode();
    }
    if (!node.isTextual()) {
      return node;
    }
    try {
      Object decoded = parsePossiblyWrappedJsonObject(node.asText());
      return objectMapper.valueToTree(decoded);
    } catch (JsonProcessingException ex) {
      return node;
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> readJsonMap(Object entity) {
    JsonNode storedNode = null;
    String raw = null;
    try {
      if (entity instanceof ErpMaterialIssueRawEntity row) {
        storedNode = row.getRowJson();
      } else if (entity instanceof ErpMaterialInventoryRawEntity row) {
        storedNode = row.getRowJson();
      } else if (entity instanceof ErpMaterialSupplyRawEntity row) {
        storedNode = row.getRowJson();
      } else {
        return Map.of();
      }
      raw = jsonNodeRawText(storedNode);
      if (raw == null || raw.isBlank()) {
        return Map.of();
      }
      return parseStoredRowNode(storedNode);
    } catch (JsonProcessingException ex) {
      Map<String, Object> legacyRow = readLegacyRowMap(entity, raw);
      if (!legacyRow.isEmpty()) {
        log.warn("Recovered legacy ERP row payload using fallback parser.");
        return legacyRow;
      }
      throw new IllegalStateException("Failed to deserialize ERP row.", ex);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> parseStoredRowNode(JsonNode jsonNode) throws JsonProcessingException {
    if (jsonNode == null || jsonNode.isNull()) {
      return Map.of();
    }
    if (jsonNode.isObject()) {
      return objectMapper.convertValue(jsonNode, Map.class);
    }
    if (jsonNode.isTextual()) {
      return parsePossiblyWrappedJsonObject(jsonNode.asText());
    }
    throw new JsonProcessingException("Stored ERP row is not a JSON object.") {};
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> parsePossiblyWrappedJsonObject(String raw) throws JsonProcessingException {
    Object decoded = raw;
    for (int i = 0; i < 3; i += 1) {
      if (decoded instanceof Map<?, ?> map) {
        return (Map<String, Object>) map;
      }
      if (!(decoded instanceof String text) || text.isBlank()) {
        break;
      }
      decoded = objectMapper.readValue(text, Object.class);
    }
    throw new JsonProcessingException("Stored ERP row is not a JSON object.") {};
  }

  private String jsonNodeRawText(JsonNode jsonNode) {
    if (jsonNode == null || jsonNode.isNull()) {
      return "";
    }
    if (jsonNode.isTextual()) {
      return jsonNode.asText();
    }
    return jsonNode.toString();
  }

  private Map<String, Object> readLegacyRowMap(Object entity, String json) {
    String raw = json == null ? "" : json.trim();
    if (raw.isBlank()) {
      return Map.of();
    }

    Map<String, Object> parsed = parseLegacyMapText(raw);
    if (parsed.isEmpty()) {
      parsed = new LinkedHashMap<>();
    }
    if (entity instanceof ErpMaterialIssueRawEntity row) {
      parsed.putIfAbsent("production_order_no", row.getProductionOrderNo());
      parsed.putIfAbsent("source_production_order_no", row.getProductionOrderNo());
      parsed.putIfAbsent("material_list_no", row.getMaterialListNo());
      parsed.putIfAbsent("child_material_code", row.getChildMaterialCode());
    } else if (entity instanceof ErpMaterialInventoryRawEntity row) {
      parsed.putIfAbsent("material_code", row.getMaterialCode());
    } else if (entity instanceof ErpMaterialSupplyRawEntity row) {
      parsed.putIfAbsent("material_code", row.getMaterialCode());
      parsed.putIfAbsent("supply_type", row.getSupplyType());
    }
    parsed.putIfAbsent("_legacy_row_json", raw);
    return parsed;
  }

  private static Map<String, Object> parseLegacyMapText(String raw) {
    String text = raw == null ? "" : raw.trim();
    if (text.isBlank()) {
      return Map.of();
    }
    if (text.startsWith("{") && text.endsWith("}")) {
      text = text.substring(1, text.length() - 1).trim();
    }
    if (text.isBlank()) {
      return Map.of();
    }

    Map<String, Object> out = new LinkedHashMap<>();
    for (String token : splitTopLevel(text, ',')) {
      String part = token == null ? "" : token.trim();
      if (part.isBlank()) {
        continue;
      }
      int separator = indexOfTopLevel(part, '=');
      if (separator <= 0) {
        continue;
      }
      String key = part.substring(0, separator).trim();
      String valueText = part.substring(separator + 1).trim();
      if (key.isBlank()) {
        continue;
      }
      out.put(key, parseLegacyValue(valueText));
    }
    return out;
  }

  private static Object parseLegacyValue(String rawValue) {
    String value = rawValue == null ? "" : rawValue.trim();
    if (value.isBlank()) {
      return "";
    }
    if ("null".equalsIgnoreCase(value)) {
      return null;
    }
    if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
      return Boolean.valueOf(value);
    }
    if (value.startsWith("{") && value.endsWith("}")) {
      Map<String, Object> nested = parseLegacyMapText(value);
      return nested.isEmpty() ? value : nested;
    }
    if (value.startsWith("[") && value.endsWith("]")) {
      return value;
    }
    try {
      return new BigDecimal(value);
    } catch (NumberFormatException ex) {
      return value;
    }
  }

  private static List<String> splitTopLevel(String text, char separator) {
    if (text == null || text.isBlank()) {
      return List.of();
    }
    List<String> parts = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    int braceDepth = 0;
    int bracketDepth = 0;
    int parenDepth = 0;
    for (int i = 0; i < text.length(); i += 1) {
      char ch = text.charAt(i);
      switch (ch) {
        case '{' -> braceDepth += 1;
        case '}' -> braceDepth = Math.max(0, braceDepth - 1);
        case '[' -> bracketDepth += 1;
        case ']' -> bracketDepth = Math.max(0, bracketDepth - 1);
        case '(' -> parenDepth += 1;
        case ')' -> parenDepth = Math.max(0, parenDepth - 1);
        default -> {
          // no-op
        }
      }
      if (ch == separator && braceDepth == 0 && bracketDepth == 0 && parenDepth == 0) {
        parts.add(current.toString());
        current.setLength(0);
        continue;
      }
      current.append(ch);
    }
    parts.add(current.toString());
    return Collections.unmodifiableList(parts);
  }

  private static int indexOfTopLevel(String text, char separator) {
    if (text == null || text.isBlank()) {
      return -1;
    }
    int braceDepth = 0;
    int bracketDepth = 0;
    int parenDepth = 0;
    for (int i = 0; i < text.length(); i += 1) {
      char ch = text.charAt(i);
      switch (ch) {
        case '{' -> braceDepth += 1;
        case '}' -> braceDepth = Math.max(0, braceDepth - 1);
        case '[' -> bracketDepth += 1;
        case ']' -> bracketDepth = Math.max(0, bracketDepth - 1);
        case '(' -> parenDepth += 1;
        case ')' -> parenDepth = Math.max(0, parenDepth - 1);
        default -> {
          // no-op
        }
      }
      if (ch == separator && braceDepth == 0 && bracketDepth == 0 && parenDepth == 0) {
        return i;
      }
    }
    return -1;
  }

  private static String text(Map<String, Object> row, String primary, String fallback) {
    Object value = primary == null ? null : row.get(primary);
    if (value == null && fallback != null) {
      value = row.get(fallback);
    }
    return value == null ? null : String.valueOf(value);
  }

  private List<Map<String, Object>> readLatestMaterialIssueBatch(List<ErpMaterialIssueRawEntity> entities) {
    if (entities == null || entities.isEmpty()) {
      return List.of();
    }
    String latestSnapshotId = entities.stream()
      .map(ErpMaterialIssueRawEntity::getSnapshotId)
      .filter(snapshotId -> snapshotId != null && !snapshotId.isBlank())
      .max(String::compareTo)
      .orElse(null);

    Map<String, ErpMaterialIssueRawEntity> latestByCode = new LinkedHashMap<>();
    for (ErpMaterialIssueRawEntity entity : entities) {
      if (latestSnapshotId != null && !latestSnapshotId.equals(entity.getSnapshotId())) {
        continue;
      }
      String childCode = entity.getChildMaterialCode();
      String key = (childCode == null || childCode.isBlank()) ? "__row__" + entity.getId() : childCode.trim();
      latestByCode.putIfAbsent(key, entity);
    }

    return latestByCode.values().stream()
      .map(this::readJsonMap)
      .toList();
  }
}
