package com.autoproduction.mvp.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

final class MvpStoreOrderInventoryQuerySupport {
  private MvpStoreOrderInventoryQuerySupport() {}

  static Map<String, Map<String, Object>> queryInventoryByMaterialCodesInParallel(
    MvpStoreOrderDomain domain,
    List<String> materialCodes,
    boolean refreshFromErp
  ) {
    if (materialCodes == null || materialCodes.isEmpty()) {
      return Map.of();
    }

    List<String> normalizedCodes = materialCodes.stream()
      .map(MvpStoreCoreNormalizationSupport::normalizeCode)
      .filter(code -> code != null && !code.isBlank())
      .distinct()
      .toList();
    if (normalizedCodes.isEmpty()) {
      return Map.of();
    }
    if (!refreshFromErp || normalizedCodes.size() <= MvpStoreRuntimeBase.ORDER_MATERIAL_INVENTORY_PARALLEL_CHUNK_SIZE) {
      return domain.erpDataManager.getMaterialInventoryByCodes(normalizedCodes, refreshFromErp);
    }

    List<List<String>> chunks = new ArrayList<>();
    for (
      int start = 0;
      start < normalizedCodes.size();
      start += MvpStoreRuntimeBase.ORDER_MATERIAL_INVENTORY_PARALLEL_CHUNK_SIZE
    ) {
      int end = Math.min(
        normalizedCodes.size(),
        start + MvpStoreRuntimeBase.ORDER_MATERIAL_INVENTORY_PARALLEL_CHUNK_SIZE
      );
      chunks.add(new ArrayList<>(normalizedCodes.subList(start, end)));
    }
    if (chunks.size() <= 1) {
      return domain.erpDataManager.getMaterialInventoryByCodes(normalizedCodes, refreshFromErp);
    }

    int poolSize = Math.max(1, Math.min(MvpStoreRuntimeBase.ORDER_MATERIAL_INVENTORY_PARALLEL_THREADS, chunks.size()));
    ExecutorService executor = Executors.newFixedThreadPool(poolSize);
    try {
      List<CompletableFuture<Map<String, Map<String, Object>>>> futures = new ArrayList<>();
      for (List<String> chunk : chunks) {
        futures.add(
          CompletableFuture.supplyAsync(() -> domain.erpDataManager.getMaterialInventoryByCodes(chunk, true), executor)
        );
      }

      Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
      for (CompletableFuture<Map<String, Map<String, Object>>> future : futures) {
        Map<String, Map<String, Object>> part = future.join();
        if (part == null || part.isEmpty()) {
          continue;
        }
        merged.putAll(part);
      }
      return merged;
    } catch (CompletionException ex) {
      return domain.erpDataManager.getMaterialInventoryByCodes(normalizedCodes, refreshFromErp);
    } finally {
      executor.shutdown();
      try {
        if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
          executor.shutdownNow();
        }
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        executor.shutdownNow();
      }
    }
  }
}

