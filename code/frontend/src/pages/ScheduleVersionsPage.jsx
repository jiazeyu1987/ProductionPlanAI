import ScheduleVersionAlgorithmPanel from "../features/schedule-versions/components/ScheduleVersionAlgorithmPanel";
import ScheduleVersionDiffPanel from "../features/schedule-versions/components/ScheduleVersionDiffPanel";
import ScheduleVersionsListPanel from "../features/schedule-versions/components/ScheduleVersionsListPanel";
import { useScheduleVersionsController } from "../features/schedule-versions/page/useScheduleVersionsController";

export default function ScheduleVersionsPage() {
  const controller = useScheduleVersionsController();

  return (
    <section>
      <h2>排产历史</h2>
      <ScheduleVersionsListPanel
        visibleRows={controller.visibleRows}
        showDraft={controller.showDraft}
        compareWith={controller.compareWith}
        message={controller.message}
        onShowDraftChange={controller.setShowDraft}
        onCompareWithChange={controller.setCompareWith}
        onCompare={controller.compare}
        onShowAlgorithm={controller.showAlgorithm}
        onPublish={controller.publish}
        onRollback={controller.rollback}
      />
      {controller.diff ? <ScheduleVersionDiffPanel diff={controller.diff} /> : null}
      {controller.algorithmDetail ? (
        <ScheduleVersionAlgorithmPanel
          algorithmDetail={controller.algorithmDetail}
          algorithmNarrative={controller.algorithmNarrative}
          processSummaryRows={controller.processSummaryRows}
          selectedMaxAllocationRow={controller.selectedMaxAllocationRow}
          onSelectMaxAllocationRow={controller.setSelectedMaxAllocationRow}
        />
      ) : null}
    </section>
  );
}

