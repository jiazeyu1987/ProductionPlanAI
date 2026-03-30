import { useSearchParams } from "react-router-dom";
import { MasterdataConfigPanels, RouteTabPanel, normalizeCompanyCode } from "../features/masterdata";
import { useMasterdataBootstrap } from "../features/masterdata/page/useMasterdataBootstrap";
import { useMasterdataConfigPanelsController } from "../features/masterdata/page/useMasterdataConfigPanelsController";
import { useMasterdataRouteTabController } from "../features/masterdata/page/useMasterdataRouteTabController";
import {
  CONFIG_SUB_LEADER,
  CONFIG_SUB_MATERIAL,
  CONFIG_SUB_RESOURCE,
  CONFIG_SUB_WINDOW,
  CONFIG_TAB,
  DEVICE_CONFIG_TAB,
  ROUTE_TAB,
} from "../features/masterdata/page/constants";

export default function MasterdataPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const processCodeFilter = (searchParams.get("process_code") || "").trim();
  const productCodeFilter = (searchParams.get("product_code") || "").trim();
  const tabParam = (searchParams.get("tab") || "").trim().toLowerCase();
  const configSubParam = (searchParams.get("config_sub") || searchParams.get("config_tab") || "").trim().toLowerCase();
  const topologyCompanyParam = normalizeCompanyCode(searchParams.get("company_code"));
  const topologyWorkshopParam = (searchParams.get("workshop_code") || "").trim();
  const topologyLineParam = (searchParams.get("line_code") || "").trim().toUpperCase();

  const bootstrap = useMasterdataBootstrap();

  const configController = useMasterdataConfigPanelsController({
    routes: bootstrap.routes,
    configRes: bootstrap.configRes,
    rulesRes: bootstrap.rulesRes,
    schedules: bootstrap.schedules,
    tabParam,
    configSubParam,
    topologyCompanyParam,
    topologyWorkshopParam,
    topologyLineParam,
    processCodeFilter,
    productCodeFilter,
    orderMaterialRows: bootstrap.orderMaterialRows,
    orderMaterialLoading: bootstrap.orderMaterialLoading,
    orderMaterialRefreshing: bootstrap.orderMaterialRefreshing,
    orderMaterialError: bootstrap.orderMaterialError,
    refreshOrderMaterialRows: bootstrap.refreshOrderMaterialRows,
    ensureOrderMaterialRowsLoaded: bootstrap.ensureOrderMaterialRowsLoaded,
  });

  const routeController = useMasterdataRouteTabController({
    routes: bootstrap.routes,
    lineTopology: configController.lineTopology,
    processCodeFilter,
    productCodeFilter,
    searchParams,
    setSearchParams,
    refreshMasterdata: bootstrap.refreshMasterdata,
  });

  return (
    <section>
      <h2>Master Data</h2>
      <div className="report-tabs" role="tablist" aria-label="Master data tabs">
        <button
          role="tab"
          aria-selected={configController.activeTab === ROUTE_TAB}
          className={configController.activeTab === ROUTE_TAB ? "active" : ""}
          onClick={() => configController.setActiveTab(ROUTE_TAB)}
        >
          工艺路线
        </button>
        <button
          role="tab"
          aria-selected={configController.activeTab === CONFIG_TAB && configController.activeConfigSubTab === CONFIG_SUB_MATERIAL}
          className={configController.activeTab === CONFIG_TAB && configController.activeConfigSubTab === CONFIG_SUB_MATERIAL ? "active" : ""}
          onClick={() => {
            configController.setActiveTab(CONFIG_TAB);
            configController.setActiveConfigSubTab(CONFIG_SUB_MATERIAL);
          }}
        >
          计划配置
        </button>
        <button
          role="tab"
          aria-selected={configController.activeTab === DEVICE_CONFIG_TAB}
          className={configController.activeTab === DEVICE_CONFIG_TAB ? "active" : ""}
          onClick={() => configController.setActiveTab(DEVICE_CONFIG_TAB)}
        >
          设备拓扑
        </button>
      </div>

      {configController.activeTab === ROUTE_TAB ? (
        <RouteTabPanel
          processCodeFilter={processCodeFilter}
          productCodeFilter={productCodeFilter}
          onClearFocus={routeController.onClearFocus}
          routeMessage={routeController.routeMessage}
          routeError={routeController.routeError}
          routeEditorOpen={routeController.routeEditorOpen}
          routeEditorMode={routeController.routeEditorMode}
          routeSaving={routeController.routeSaving}
          routeSourceProductCode={routeController.routeSourceProductCode}
          routeTargetProductCode={routeController.routeTargetProductCode}
          setRouteTargetProductCode={routeController.setRouteTargetProductCode}
          submitRouteEditor={routeController.submitRouteEditor}
          closeRouteEditor={routeController.closeRouteEditor}
          routeStepsDraft={routeController.routeStepsDraft}
          updateRouteStep={routeController.updateRouteStep}
          processOptions={routeController.processOptions}
          normalizeDependencyType={routeController.normalizeDependencyType}
          removeRouteStep={routeController.removeRouteStep}
          addRouteStep={routeController.addRouteStep}
          routeEditorCreateMode={routeController.routeEditorCreateMode}
          routeEditorEditMode={routeController.routeEditorEditMode}
          processDetails={routeController.processDetails}
          productDetails={routeController.productDetails}
          groupedRouteRows={routeController.groupedRouteRows}
          openCreateRouteEditor={routeController.openCreateRouteEditor}
          openEditRouteEditor={routeController.openEditRouteEditor}
          openCopyRouteEditor={routeController.openCopyRouteEditor}
          deleteRoute={routeController.deleteRoute}
          resolveRouteListDisplayCode={routeController.resolveRouteListDisplayCode}
          resolveRouteListProductName={routeController.resolveRouteListProductName}
          dependencyTypeLabel={routeController.dependencyTypeLabel}
        />
      ) : null}

      <MasterdataConfigPanels
        showPanels={[CONFIG_TAB, DEVICE_CONFIG_TAB].includes(configController.activeTab)}
        isConfigTab={configController.activeTab === CONFIG_TAB}
        showWindowPanel={configController.activeConfigSubTab === CONFIG_SUB_WINDOW}
        showDeviceTopology={configController.activeTab === DEVICE_CONFIG_TAB}
        showLeaderPanel={configController.activeConfigSubTab === CONFIG_SUB_LEADER}
        showResourcePanel={configController.activeConfigSubTab === CONFIG_SUB_RESOURCE}
        showMaterialPanel={configController.activeConfigSubTab === CONFIG_SUB_MATERIAL}
        saving={configController.saving}
        onSaveConfig={configController.saveConfig}
        saveMessage={configController.saveMessage}
        saveError={configController.saveError}
        planningWindowProps={configController.planningWindowProps}
        deviceTopologyProps={configController.deviceTopologyProps}
        sectionLeaderProps={configController.sectionLeaderProps}
        resourcePoolProps={configController.resourcePoolProps}
        orderMaterialProps={configController.orderMaterialProps}
      />
    </section>
  );
}
