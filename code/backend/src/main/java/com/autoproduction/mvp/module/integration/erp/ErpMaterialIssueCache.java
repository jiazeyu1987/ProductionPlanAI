package com.autoproduction.mvp.module.integration.erp;

import com.autoproduction.mvp.core.ErpSqliteOrderLoader;
import com.autoproduction.mvp.module.integration.erp.manager.ErpMaterialCodeUtils;
import com.autoproduction.mvp.module.integration.erp.manager.MaterialIssueCacheEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class ErpMaterialIssueCache {
  private final ErpSqliteOrderLoader loader;
  private final long ttlMs;
  private final ConcurrentHashMap<String, MaterialIssueCacheEntry> cache = new ConcurrentHashMap<>();

  ErpMaterialIssueCache(ErpSqliteOrderLoader loader, long ttlMs) {
    this.loader = loader;
    this.ttlMs = Math.max(0L, ttlMs);
  }

  void clear() {
    cache.clear();
  }

  List<Map<String, Object>> getProductionMaterialIssuesByOrder(
    String productionOrderNo,
    String materialListNo,
    boolean forceRefresh
  ) {
    String key = materialIssueCacheKey(productionOrderNo, materialListNo);
    long now = System.currentTimeMillis();
    if (forceRefresh) {
      cache.remove(key);
    } else if (ttlMs > 0L) {
      MaterialIssueCacheEntry cached = cache.get(key);
      if (cached != null && now - cached.cachedAtMs() <= ttlMs) {
        return cached.rows();
      }
    }

    List<Map<String, Object>> loaded = ErpImmutableRows.freezeRows(
      ErpImmutableRows.safeRows(loader.loadProductionMaterialIssuesByOrder(productionOrderNo, materialListNo, forceRefresh))
    );
    if (ttlMs > 0L) {
      cache.put(key, new MaterialIssueCacheEntry(loaded, now));
      trimIfNeeded();
    }
    return loaded;
  }

  private static String materialIssueCacheKey(String productionOrderNo, String materialListNo) {
    String orderPart = ErpMaterialCodeUtils.normalizeText(productionOrderNo);
    String materialPart = ErpMaterialCodeUtils.normalizeText(materialListNo);
    return (orderPart == null ? "" : orderPart) + "|" + (materialPart == null ? "" : materialPart);
  }

  private void trimIfNeeded() {
    if (cache.size() <= 200) {
      return;
    }
    int removed = 0;
    for (String key : new ArrayList<>(cache.keySet())) {
      cache.remove(key);
      removed += 1;
      if (cache.size() <= 150 || removed >= 50) {
        break;
      }
    }
  }
}

