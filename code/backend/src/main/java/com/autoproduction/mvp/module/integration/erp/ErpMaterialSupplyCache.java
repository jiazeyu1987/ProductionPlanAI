package com.autoproduction.mvp.module.integration.erp;

import com.autoproduction.mvp.core.ErpSqliteOrderLoader;
import com.autoproduction.mvp.module.integration.erp.manager.ErpMaterialCodeUtils;
import com.autoproduction.mvp.module.integration.erp.manager.ErpMaterialSupplyUtils;
import com.autoproduction.mvp.module.integration.erp.manager.ErpSnapshot;
import com.autoproduction.mvp.module.integration.erp.manager.MaterialSupplyCacheEntry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

final class ErpMaterialSupplyCache {
  private final ErpSqliteOrderLoader loader;
  private final Runnable ensureCacheReady;
  private final Supplier<ErpSnapshot> snapshotSupplier;
  private final ConcurrentHashMap<String, MaterialSupplyCacheEntry> cache = new ConcurrentHashMap<>();

  ErpMaterialSupplyCache(ErpSqliteOrderLoader loader, Runnable ensureCacheReady, Supplier<ErpSnapshot> snapshotSupplier) {
    this.loader = loader;
    this.ensureCacheReady = ensureCacheReady;
    this.snapshotSupplier = snapshotSupplier;
  }

  void clear() {
    cache.clear();
  }

  Map<String, Object> getMaterialSupplyInfo(String materialCode) {
    String normalizedMaterialCode = ErpMaterialCodeUtils.normalizeText(materialCode);
    if (normalizedMaterialCode == null) {
      return Map.of();
    }
    MaterialSupplyCacheEntry cached = cache.get(normalizedMaterialCode);
    if (cached != null) {
      return cached.data();
    }

    Map<String, Object> loaded = ErpImmutableRows.freezeRow(loader.loadMaterialSupplyInfo(normalizedMaterialCode));
    Map<String, Object> finalized = finalizeMaterialSupplyInfo(normalizedMaterialCode, loaded);
    cache.put(normalizedMaterialCode, new MaterialSupplyCacheEntry(finalized, System.currentTimeMillis()));
    trimIfNeeded();
    return finalized;
  }

  private Map<String, Object> finalizeMaterialSupplyInfo(String materialCode, Map<String, Object> loaded) {
    String supplyType = ErpMaterialCodeUtils.normalizeText(ErpMaterialCodeUtils.stringValue(loaded, "supply_type"));
    if (ErpMaterialSupplyUtils.isKnownSupplyType(supplyType)) {
      return loaded;
    }
    String inferredType = inferMaterialSupplyTypeFromSnapshot(materialCode);
    if (!ErpMaterialSupplyUtils.isKnownSupplyType(inferredType)) {
      inferredType = ErpMaterialSupplyUtils.inferMaterialSupplyTypeByCode(materialCode);
    }
    if (!ErpMaterialSupplyUtils.isKnownSupplyType(inferredType)) {
      return loaded;
    }
    Map<String, Object> merged = new LinkedHashMap<>();
    if (loaded != null) {
      merged.putAll(loaded);
    }
    merged.put("material_code", materialCode);
    merged.put("supply_type", inferredType);
    merged.put("supply_type_name_cn", ErpMaterialSupplyUtils.toSupplyTypeNameCn(inferredType));
    merged.putIfAbsent("supply_type_raw", "SNAPSHOT_INFERRED");
    return ErpImmutableRows.freezeRow(merged);
  }

  private String inferMaterialSupplyTypeFromSnapshot(String materialCode) {
    ensureCacheReady.run();
    String normalized = ErpMaterialCodeUtils.normalizeText(materialCode);
    if (normalized == null) {
      return null;
    }
    ErpSnapshot currentSnapshot = snapshotSupplier.get();
    boolean existsInPurchase = ErpMaterialCodeUtils.existsMaterialCode(
      currentSnapshot.purchaseOrders(),
      List.of("material_code", "materialCode", "FMaterialId.FNumber"),
      normalized
    );
    boolean existsInProduction = ErpMaterialCodeUtils.existsMaterialCode(
      currentSnapshot.productionOrders(),
      List.of("product_code", "productCode", "material_code", "FMaterialId.FNumber"),
      normalized
    );
    if (existsInPurchase) {
      return "PURCHASED";
    }
    if (existsInProduction) {
      return "SELF_MADE";
    }
    return null;
  }

  private void trimIfNeeded() {
    if (cache.size() <= 1_000) {
      return;
    }
    int removed = 0;
    for (String key : new ArrayList<>(cache.keySet())) {
      cache.remove(key);
      removed += 1;
      if (cache.size() <= 800 || removed >= 200) {
        break;
      }
    }
  }
}

