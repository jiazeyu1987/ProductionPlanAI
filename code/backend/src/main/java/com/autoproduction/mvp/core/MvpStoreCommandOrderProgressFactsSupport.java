package com.autoproduction.mvp.core;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class MvpStoreCommandOrderProgressFactsSupport {
  private MvpStoreCommandOrderProgressFactsSupport() {}

  static void updateOrderProgressFacts(MvpStoreCommandOrderPatchSupport domain, MvpDomain.Order order) {
    MvpDomain.OrderBusinessData business = domain.businessData(order);
    double totalQty = order.items.stream().mapToDouble(item -> item.qty).sum();
    double completedQty = order.items.stream().mapToDouble(item -> item.completedQty).sum();
    business.workshopCompletedQty = domain.round2(completedQty);
    if (completedQty > 0d) {
      business.workshopCompletedTime = OffsetDateTime.now(ZoneOffset.UTC).toString();
    }
    if (totalQty > 0d && completedQty + 1e-9 >= totalQty) {
      business.outerCompletedQty = domain.round2(completedQty);
      business.outerCompletedTime = OffsetDateTime.now(ZoneOffset.UTC).toString();
      business.matchStatus = "瀹告彃灏柊";
    } else {
      business.outerCompletedQty = domain.round2(Math.max(0d, completedQty - (0.1d * totalQty)));
      business.matchStatus = "瀵板懎灏柊";
    }
  }

  static void syncCompletedQtyFromFinalProcessReports(MvpStoreCommandOrderPatchSupport domain) {
    if (domain.state.reportings == null) {
      return;
    }

    int reportingSize = domain.state.reportings.size();
    boolean fullResync = false;
    if (reportingSize < domain.finalCompletedSyncCursor) {
      domain.finalCompletedByOrderProductCache.clear();
      domain.finalCompletedSyncCursor = 0;
      fullResync = true;
    }

    Set<String> touchedOrderNos = new HashSet<>();
    for (int index = domain.finalCompletedSyncCursor; index < reportingSize; index += 1) {
      MvpDomain.Reporting reporting = domain.state.reportings.get(index);
      if (!domain.isFinalProcessForProduct(reporting.productCode, reporting.processCode)) {
        continue;
      }
      String key = reporting.orderNo + "#" + reporting.productCode;
      domain.finalCompletedByOrderProductCache.merge(key, reporting.reportQty, Double::sum);
      touchedOrderNos.add(reporting.orderNo);
    }
    domain.finalCompletedSyncCursor = reportingSize;

    if (!fullResync && touchedOrderNos.isEmpty()) {
      return;
    }

    for (MvpDomain.Order order : domain.state.orders) {
      if (!fullResync && !touchedOrderNos.contains(order.orderNo)) {
        continue;
      }
      boolean updated = false;
      for (MvpDomain.OrderItem item : order.items) {
        String key = order.orderNo + "#" + item.productCode;
        if (!domain.finalCompletedByOrderProductCache.containsKey(key)) {
          continue;
        }
        double corrected = Math.min(item.qty, Math.max(0d, domain.finalCompletedByOrderProductCache.get(key)));
        if (Math.abs(item.completedQty - corrected) > 1e-9) {
          item.completedQty = corrected;
          updated = true;
        }
      }
      if (updated) {
        domain.updateOrderProgressFacts(order);
      }
    }
  }

  static boolean isFinalProcessForProduct(MvpStoreCommandOrderPatchSupport domain, String productCode, String processCode) {
    String normalizedProcessCode = domain.normalizeCode(processCode);
    if (normalizedProcessCode.isBlank()) {
      return false;
    }
    List<MvpDomain.ProcessStep> route = domain.state.processRoutes.get(productCode);
    if (route == null || route.isEmpty()) {
      return isFallbackFinalProcess(normalizedProcessCode);
    }
    MvpDomain.ProcessStep lastStep = route.get(route.size() - 1);
    String normalizedLastStepCode = domain.normalizeCode(lastStep.processCode);
    if (normalizedLastStepCode.isBlank()) {
      return isFallbackFinalProcess(normalizedProcessCode);
    }
    return normalizedProcessCode.equals(normalizedLastStepCode);
  }

  static boolean isFallbackFinalProcess(String normalizedProcessCode) {
    return "PROC_STERILE".equals(normalizedProcessCode)
      || normalizedProcessCode.endsWith("_STERILE")
      || normalizedProcessCode.contains("FINAL");
  }
}

