package com.autoproduction.mvp.module.integration.erp;

import com.autoproduction.mvp.core.ErpSqliteOrderLoader;
import com.autoproduction.mvp.module.integration.erp.manager.ErpMaterialCodeUtils;
import com.autoproduction.mvp.module.integration.erp.manager.ErpMaterialSupplyUtils;
import com.autoproduction.mvp.module.integration.erp.manager.MaterialSupplyCacheEntry;
import com.autoproduction.mvp.module.integration.erp.persistence.ErpSnapshotPersistenceService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

final class ErpMaterialSupplyCache {
  private final ErpSqliteOrderLoader loader;
  private final ErpSnapshotPersistenceService persistenceService;
  private final Supplier<String> snapshotIdSupplier;
  private final ConcurrentHashMap<String, MaterialSupplyCacheEntry> cache = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, CompletableFuture<Map<String, Object>>> inFlight = new ConcurrentHashMap<>();

  ErpMaterialSupplyCache(
    ErpSqliteOrderLoader loader,
    ErpSnapshotPersistenceService persistenceService,
    Supplier<String> snapshotIdSupplier
  ) {
    this.loader = loader;
    this.persistenceService = persistenceService;
    this.snapshotIdSupplier = snapshotIdSupplier;
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
    Map<String, Object> persisted = persistenceService.loadSupplyRow(normalizedMaterialCode);
    if (!persisted.isEmpty()) {
      Map<String, Object> finalizedPersisted = finalizeMaterialSupplyInfo(normalizedMaterialCode, persisted);
      cache.put(normalizedMaterialCode, new MaterialSupplyCacheEntry(finalizedPersisted, System.currentTimeMillis()));
      return finalizedPersisted;
    }
    CompletableFuture<Map<String, Object>> pending = new CompletableFuture<>();
    CompletableFuture<Map<String, Object>> existing = inFlight.putIfAbsent(normalizedMaterialCode, pending);
    if (existing != null) {
      return await(existing);
    }
    try {
      Map<String, Object> loaded = ErpImmutableRows.freezeRow(loader.loadMaterialSupplyInfo(normalizedMaterialCode));
      Map<String, Object> finalized = finalizeMaterialSupplyInfo(normalizedMaterialCode, loaded);
      cache.put(normalizedMaterialCode, new MaterialSupplyCacheEntry(finalized, System.currentTimeMillis()));
      persistenceService.persistSupplyRow(snapshotIdSupplier.get(), normalizedMaterialCode, finalized);
      trimIfNeeded();
      pending.complete(finalized);
      return finalized;
    } catch (RuntimeException ex) {
      pending.completeExceptionally(ex);
      throw ex;
    } finally {
      inFlight.remove(normalizedMaterialCode, pending);
    }
  }

  private Map<String, Object> finalizeMaterialSupplyInfo(String materialCode, Map<String, Object> loaded) {
    if (loaded == null || loaded.isEmpty()) {
      throw new IllegalStateException("ERP material supply row missing for materialCode=" + materialCode);
    }
    String supplyType = ErpMaterialCodeUtils.normalizeText(ErpMaterialCodeUtils.stringValue(loaded, "supply_type"));
    if (!ErpMaterialSupplyUtils.isKnownSupplyType(supplyType)) {
      throw new IllegalStateException(
        "ERP material supply row has unknown supply_type. materialCode=" + materialCode + ", row=" + loaded
      );
    }
    Map<String, Object> merged = new LinkedHashMap<>();
    merged.putAll(loaded);
    merged.put("material_code", materialCode);
    merged.put("supply_type", supplyType);
    merged.putIfAbsent("supply_type_name_cn", ErpMaterialSupplyUtils.toSupplyTypeNameCn(supplyType));
    return ErpImmutableRows.freezeRow(merged);
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

  private static Map<String, Object> await(CompletableFuture<Map<String, Object>> future) {
    try {
      return future.join();
    } catch (RuntimeException ex) {
      Throwable cause = ex.getCause();
      if (cause instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw ex;
    }
  }
}
