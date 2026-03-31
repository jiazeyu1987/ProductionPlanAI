package com.autoproduction.mvp.module.integration.erp;

import com.autoproduction.mvp.core.ErpSqliteOrderLoader;
import com.autoproduction.mvp.module.integration.erp.manager.ErpInventoryUtils;
import com.autoproduction.mvp.module.integration.erp.manager.ErpMaterialCodeUtils;
import com.autoproduction.mvp.module.integration.erp.manager.MaterialInventoryCacheEntry;
import com.autoproduction.mvp.module.integration.erp.persistence.ErpSnapshotPersistenceService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

final class ErpMaterialInventoryCache {
  private final ErpSqliteOrderLoader loader;
  private final ErpSnapshotPersistenceService persistenceService;
  private final java.util.function.Supplier<String> snapshotIdSupplier;
  private final ConcurrentHashMap<String, MaterialInventoryCacheEntry> cache = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, CompletableFuture<Map<String, Object>>> inFlight = new ConcurrentHashMap<>();

  ErpMaterialInventoryCache(
    ErpSqliteOrderLoader loader,
    ErpSnapshotPersistenceService persistenceService,
    java.util.function.Supplier<String> snapshotIdSupplier
  ) {
    this.loader = loader;
    this.persistenceService = persistenceService;
    this.snapshotIdSupplier = snapshotIdSupplier;
  }

  void clear() {
    cache.clear();
  }

  Map<String, Map<String, Object>> getMaterialInventoryByCodes(List<String> materialCodes, boolean forceRefresh) {
    Set<String> normalizedCodes = ErpMaterialCodeUtils.normalizeMaterialCodeSet(materialCodes);
    if (normalizedCodes.isEmpty()) {
      return Map.of();
    }

    if (forceRefresh) {
      for (String code : normalizedCodes) {
        cache.remove(code);
      }
    }

    List<String> missingCodes = new ArrayList<>();
    List<String> claimedCodes = new ArrayList<>();
    List<CompletableFuture<Map<String, Object>>> waitFutures = new ArrayList<>();
    for (String code : normalizedCodes) {
      if (cache.containsKey(code)) {
        continue;
      }
      Map<String, Object> persisted = persistenceService.loadInventoryRows(List.of(code)).get(code);
      if (persisted != null && !persisted.isEmpty()) {
        cache.put(code, new MaterialInventoryCacheEntry(ErpImmutableRows.freezeRow(persisted), System.currentTimeMillis()));
        continue;
      }
      CompletableFuture<Map<String, Object>> pending = new CompletableFuture<>();
      CompletableFuture<Map<String, Object>> existing = inFlight.putIfAbsent(code, pending);
      if (existing == null) {
        missingCodes.add(code);
        claimedCodes.add(code);
        waitFutures.add(pending);
      } else {
        waitFutures.add(existing);
      }
    }
    if (!missingCodes.isEmpty()) {
      try {
        List<Map<String, Object>> loadedRows = ErpImmutableRows.safeRows(loader.loadMaterialInventoryByCodes(missingCodes, forceRefresh));
        Map<String, Map<String, Object>> loadedByCode = new LinkedHashMap<>();
        for (Map<String, Object> row : loadedRows) {
          String materialCode = ErpMaterialCodeUtils.normalizeText(ErpMaterialCodeUtils.stringValue(row, "material_code"));
          if (materialCode == null) {
            continue;
          }
          Map<String, Object> normalized = ErpInventoryUtils.normalizeInventoryRow(materialCode, row, false);
          loadedByCode.put(materialCode, normalized);
        }
        List<String> unresolvedCodes = missingCodes.stream().filter(code -> !loadedByCode.containsKey(code)).toList();
        if (!unresolvedCodes.isEmpty()) {
          throw new IllegalStateException("ERP material inventory rows missing for materialCodes=" + unresolvedCodes);
        }
        long now = System.currentTimeMillis();
        for (String code : missingCodes) {
          Map<String, Object> normalized = loadedByCode.get(code);
          Map<String, Object> frozen = ErpImmutableRows.freezeRow(normalized);
          cache.put(code, new MaterialInventoryCacheEntry(frozen, now));
          CompletableFuture<Map<String, Object>> pending = inFlight.get(code);
          if (pending != null) {
            pending.complete(frozen);
          }
        }
        persistenceService.persistInventoryRows(snapshotIdSupplier.get(), Collections.unmodifiableMap(loadedByCode));
        trimIfNeeded();
      } catch (RuntimeException ex) {
        for (String code : missingCodes) {
          CompletableFuture<Map<String, Object>> pending = inFlight.get(code);
          if (pending != null) {
            pending.completeExceptionally(ex);
          }
        }
        throw ex;
      } finally {
        for (String code : claimedCodes) {
          CompletableFuture<Map<String, Object>> pending = inFlight.get(code);
          inFlight.remove(code, pending);
        }
      }
    } else {
      for (CompletableFuture<Map<String, Object>> future : waitFutures) {
        await(future);
      }
    }

    Map<String, Map<String, Object>> out = new LinkedHashMap<>();
    for (String code : normalizedCodes) {
      MaterialInventoryCacheEntry entry = cache.get(code);
      if (entry == null) {
        throw new IllegalStateException("ERP material inventory cache miss for materialCode=" + code);
      }
      out.put(code, entry.data());
    }
    return Collections.unmodifiableMap(out);
  }

  private void trimIfNeeded() {
    if (cache.size() <= 4_000) {
      return;
    }
    int removed = 0;
    for (String key : new ArrayList<>(cache.keySet())) {
      cache.remove(key);
      removed += 1;
      if (cache.size() <= 3_200 || removed >= 800) {
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
