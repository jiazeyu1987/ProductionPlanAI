package com.autoproduction.mvp.core;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

final class MvpStoreSimulationManualSnapshotSupport {
  private MvpStoreSimulationManualSnapshotSupport() {}

  static void ensureManualSimulationSnapshot(MvpStoreSimulationEngineSupport domain) {
    if (domain.manualSimulationSnapshot != null) {
      return;
    }
    MvpStoreRuntimeBase.ManualSimulationSnapshot snapshot = new MvpStoreRuntimeBase.ManualSimulationSnapshot();
    snapshot.state = domain.deepCopyState(domain.state);
    snapshot.simulationState = domain.deepCopySimulationState(domain.simulationState);
    snapshot.reportingSeq = domain.reportingSeq.get();
    snapshot.replanSeq = domain.replanSeq.get();
    snapshot.alertSeq = domain.alertSeq.get();
    snapshot.dispatchSeq = domain.dispatchSeq.get();
    snapshot.dispatchApprovalSeq = domain.dispatchApprovalSeq.get();
    snapshot.salesSeq = domain.salesSeq.get();
    snapshot.productionSeq = domain.productionSeq.get();
    snapshot.simulationEventSeq = domain.simulationEventSeq.get();
    snapshot.snapshotAt = OffsetDateTime.now(ZoneOffset.UTC).toString();
    domain.manualSimulationSnapshot = snapshot;
  }

  static void restoreManualSimulationSnapshot(MvpStoreSimulationEngineSupport domain) {
    if (domain.manualSimulationSnapshot == null) {
      return;
    }
    domain.state = domain.deepCopyState(domain.manualSimulationSnapshot.state);
    domain.restoreSimulationState(domain.manualSimulationSnapshot.simulationState);
    domain.reportingSeq.set(domain.manualSimulationSnapshot.reportingSeq);
    domain.replanSeq.set(domain.manualSimulationSnapshot.replanSeq);
    domain.alertSeq.set(domain.manualSimulationSnapshot.alertSeq);
    domain.dispatchSeq.set(domain.manualSimulationSnapshot.dispatchSeq);
    domain.dispatchApprovalSeq.set(domain.manualSimulationSnapshot.dispatchApprovalSeq);
    domain.salesSeq.set(domain.manualSimulationSnapshot.salesSeq);
    domain.productionSeq.set(domain.manualSimulationSnapshot.productionSeq);
    domain.simulationEventSeq.set(domain.manualSimulationSnapshot.simulationEventSeq);
    domain.finalCompletedByOrderProductCache.clear();
    domain.finalCompletedSyncCursor = 0;
    domain.manualSimulationSnapshot = null;
  }
}

