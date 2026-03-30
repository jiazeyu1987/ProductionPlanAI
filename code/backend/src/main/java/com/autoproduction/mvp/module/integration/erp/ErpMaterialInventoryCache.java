package com.autoproduction.mvp.module.integration.erp;

import com.autoproduction.mvp.core.ErpSqliteOrderLoader;
import com.autoproduction.mvp.module.integration.erp.manager.ErpInventoryUtils;
import com.autoproduction.mvp.module.integration.erp.manager.ErpMaterialCodeUtils;
import com.autoproduction.mvp.module.integration.erp.manager.MaterialInventoryCacheEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class ErpMaterialInventoryCache {
  private final ErpSqliteOrderLoader loader;
  private final ConcurrentHashMap<String, MaterialInventoryCacheEntry> cache = new ConcurrentHashMap<>();

  ErpMaterialInventoryCache(ErpSqliteOrderLoader loader) {
    this.loader = loader;
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
    for (String code : normalizedCodes) {
      if (!cache.containsKey(code)) {
        missingCodes.add(code);
      }
    }
    if (!missingCodes.isEmpty()) {
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
      for (String code : missingCodes) {
        Map<String, Object> normalized = loadedByCode.getOrDefault(code, ErpInventoryUtils.normalizeInventoryRow(code, Map.of(), true));
        cache.put(code, new MaterialInventoryCacheEntry(ErpImmutableRows.freezeRow(normalized), System.currentTimeMillis()));
      }
      trimIfNeeded();
    }

    Map<String, Map<String, Object>> out = new LinkedHashMap<>();
    for (String code : normalizedCodes) {
      MaterialInventoryCacheEntry entry = cache.get(code);
      if (entry == null) {
        out.put(code, ErpImmutableRows.freezeRow(ErpInventoryUtils.normalizeInventoryRow(code, Map.of(), true)));
      } else {
        out.put(code, entry.data());
      }
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
}

