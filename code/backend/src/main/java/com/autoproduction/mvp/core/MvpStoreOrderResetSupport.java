package com.autoproduction.mvp.core;

final class MvpStoreOrderResetSupport {
  private MvpStoreOrderResetSupport() {}

  static void reset(MvpStoreOrderDomain domain) {
    domain.state = SeedDataFactory.build();
    domain.reportingSeq.set(0);
    domain.replanSeq.set(0);
    domain.alertSeq.set(0);
    domain.dispatchSeq.set(0);
    domain.dispatchApprovalSeq.set(0);
    domain.salesSeq.set(0);
    domain.productionSeq.set(0);
    domain.simulationEventSeq.set(0);
    domain.manualSimulationSnapshot = null;
    domain.finalCompletedByOrderProductCache.clear();
    domain.orderPoolMaterialsCache.clear();
    domain.materialChildrenByParentCache.clear();
    domain.finalCompletedSyncCursor = 0;
    domain.resetSimulationState(
      MvpStoreRuntimeBase.DEFAULT_SIM_SEED,
      MvpStoreRuntimeBase.DEFAULT_SIM_SCENARIO,
      MvpStoreRuntimeBase.DEFAULT_SIM_DAILY_SALES
    );
  }
}

