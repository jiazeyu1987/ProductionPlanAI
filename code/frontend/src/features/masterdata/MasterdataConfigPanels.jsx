import DeviceTopologyPanel from "./DeviceTopologyPanel";
import OrderMaterialPanel from "./OrderMaterialPanel";
import PlanningWindowPanel from "./PlanningWindowPanel";
import ResourcePoolPanel from "./ResourcePoolPanel";
import SectionLeaderPanel from "./SectionLeaderPanel";

export default function MasterdataConfigPanels({
  showPanels,
  isConfigTab,
  showWindowPanel,
  showDeviceTopology,
  showLeaderPanel,
  showResourcePanel,
  showMaterialPanel,
  saving,
  onSaveConfig,
  saveMessage,
  saveError,
  planningWindowProps,
  deviceTopologyProps,
  sectionLeaderProps,
  resourcePoolProps,
  orderMaterialProps,
}) {
  if (!showPanels) {
    return null;
  }

  return (
    <>
      {isConfigTab ? (
        <div className="panel-head masterdata-config-tabs-head">
          <h3>物料可用量</h3>
          <button disabled={saving} onClick={() => onSaveConfig().catch(() => {})}>
            {saving ? "保存中..." : "保存参数"}
          </button>
        </div>
      ) : null}
      {saveMessage ? <p className="notice">{saveMessage}</p> : null}
      {saveError ? <p className="error">{saveError}</p> : null}

      {showWindowPanel ? <PlanningWindowPanel {...planningWindowProps} /> : null}
      {showDeviceTopology ? <DeviceTopologyPanel {...deviceTopologyProps} /> : null}
      {showLeaderPanel ? <SectionLeaderPanel {...sectionLeaderProps} /> : null}
      {showResourcePanel ? <ResourcePoolPanel {...resourcePoolProps} /> : null}
      {showMaterialPanel ? <OrderMaterialPanel {...orderMaterialProps} /> : null}
    </>
  );
}
