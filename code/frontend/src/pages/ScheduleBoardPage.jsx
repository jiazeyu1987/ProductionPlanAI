import { useScheduleBoardController } from "../features/schedule-board/page/useScheduleBoardController";
import ScheduleBoardAllocationPanel from "../features/schedule-board/components/ScheduleBoardAllocationPanel";
import ScheduleBoardDailyLoadPanel from "../features/schedule-board/components/ScheduleBoardDailyLoadPanel";
import ScheduleBoardSummaryCards from "../features/schedule-board/components/ScheduleBoardSummaryCards";
import ScheduleBoardTasksPanel from "../features/schedule-board/components/ScheduleBoardTasksPanel";
import ScheduleBoardToolbar from "../features/schedule-board/components/ScheduleBoardToolbar";

export default function ScheduleBoardPage() {
  const controller = useScheduleBoardController();

  return (
    <section>
      <h2>调度台</h2>
      <ScheduleBoardToolbar
        strategyCode={controller.strategyCode}
        onStrategyChange={controller.onStrategyChange}
        onGenerate={() => controller.generate().catch((e) => controller.setMessage(e.message))}
        onPublish={() => controller.publishSelectedDraft().catch((e) => controller.setMessage(e.message))}
        publishing={controller.publishing}
        versions={controller.versions}
        selectedVersionNo={controller.selectedVersionNo}
        onSelectedVersionNoChange={controller.onSelectVersionNo}
        canPublishSelected={controller.canPublishSelected}
      />

      {controller.message ? <p className="notice">{controller.message}</p> : null}
      <p className="hint">
        订单列展示的是生产订单号。一个订单会被拆成多行任务，不同工序/日期/班次，所以会重复出现，这是正常现象。
      </p>
      <p className="hint">
        下面“资源占比”按当前草稿中的人力+设备用量估算；如果该单没有资源用量数据，回退为计划量占比。
      </p>

      <ScheduleBoardSummaryCards boardSummary={controller.boardSummary} />

      <ScheduleBoardAllocationPanel
        detailLoading={controller.detailLoading}
        allocationExplainSummary={controller.allocationExplainSummary}
        orderAllocationRows={controller.orderAllocationRows}
      />

      <ScheduleBoardTasksPanel tasks={controller.tasks} affectedTaskCount={controller.affectedTaskCount} />

      <ScheduleBoardDailyLoadPanel dailyProcessLoadRows={controller.dailyProcessLoadRows} />
    </section>
  );
}

