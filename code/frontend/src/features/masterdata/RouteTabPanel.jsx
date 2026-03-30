import RouteDetailsPanels from "./RouteDetailsPanels";
import RouteEditorPanel from "./RouteEditorPanel";
import RouteListPanel from "./RouteListPanel";

export default function RouteTabPanel({
  processCodeFilter,
  productCodeFilter,
  onClearFocus,
  routeMessage,
  routeError,
  routeEditorOpen,
  routeEditorMode,
  routeSaving,
  routeSourceProductCode,
  routeTargetProductCode,
  setRouteTargetProductCode,
  submitRouteEditor,
  closeRouteEditor,
  routeStepsDraft,
  updateRouteStep,
  processOptions,
  normalizeDependencyType,
  removeRouteStep,
  addRouteStep,
  routeEditorCreateMode,
  routeEditorEditMode,
  processDetails,
  productDetails,
  groupedRouteRows,
  openCreateRouteEditor,
  openEditRouteEditor,
  openCopyRouteEditor,
  deleteRoute,
  resolveRouteListDisplayCode,
  resolveRouteListProductName,
  dependencyTypeLabel,
}) {
  return (
    <>
      {processCodeFilter || productCodeFilter ? (
        <div className="toolbar">
          {processCodeFilter ? <span className="hint">当前按工序定位：{processCodeFilter}</span> : null}
          {productCodeFilter ? <span className="hint">当前按产品定位：{productCodeFilter}</span> : null}
          <button onClick={onClearFocus}>清除定位</button>
        </div>
      ) : null}
      {routeMessage ? <p className="notice">{routeMessage}</p> : null}
      {routeError ? <p className="error">{routeError}</p> : null}

      <RouteEditorPanel
        routeEditorOpen={routeEditorOpen}
        routeEditorMode={routeEditorMode}
        routeSaving={routeSaving}
        routeSourceProductCode={routeSourceProductCode}
        routeTargetProductCode={routeTargetProductCode}
        setRouteTargetProductCode={setRouteTargetProductCode}
        submitRouteEditor={submitRouteEditor}
        closeRouteEditor={closeRouteEditor}
        routeStepsDraft={routeStepsDraft}
        updateRouteStep={updateRouteStep}
        processOptions={processOptions}
        normalizeDependencyType={normalizeDependencyType}
        removeRouteStep={removeRouteStep}
        addRouteStep={addRouteStep}
        routeEditorCreateMode={routeEditorCreateMode}
        routeEditorEditMode={routeEditorEditMode}
      />

      <RouteDetailsPanels processDetails={processDetails} productDetails={productDetails} />

      <RouteListPanel
        groupedRouteRows={groupedRouteRows}
        routeSaving={routeSaving}
        onCreateRoute={openCreateRouteEditor}
        onEditRoute={openEditRouteEditor}
        onCopyRoute={openCopyRouteEditor}
        onDeleteRoute={deleteRoute}
        resolveRouteListDisplayCode={resolveRouteListDisplayCode}
        resolveRouteListProductName={resolveRouteListProductName}
        dependencyTypeLabel={dependencyTypeLabel}
      />
    </>
  );
}
