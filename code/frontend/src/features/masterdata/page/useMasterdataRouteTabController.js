import { useMemo } from "react";
import {
  buildGroupedRouteRowsForList,
  buildProcessDetails,
  buildProcessOptions,
  buildProductDetails,
  buildRouteStepsByProduct,
  createEmptyRouteStep,
  dependencyTypeLabel,
  isRouteVisibleInList,
  normalizeDependencyType,
  resolveRouteListDisplayCode,
  resolveRouteListProductName,
  sortRoutesForList,
  useMasterdataRouteEditor,
} from "..";

import {
  HIDDEN_ROUTE_PRODUCT_CODES,
  ROUTE_EDITOR_COPY,
  ROUTE_EDITOR_CREATE,
  ROUTE_EDITOR_EDIT,
  ROUTE_PRODUCT_NAME_BY_CODE,
} from "./constants";

export function useMasterdataRouteTabController({
  routes,
  lineTopology,
  processCodeFilter,
  productCodeFilter,
  searchParams,
  setSearchParams,
  refreshMasterdata,
}) {
  const filteredRoutes = useMemo(
    () =>
      sortRoutesForList(
        (routes || []).filter((row) =>
          isRouteVisibleInList(row, processCodeFilter, productCodeFilter, HIDDEN_ROUTE_PRODUCT_CODES),
        ),
      ),
    [routes, processCodeFilter, productCodeFilter],
  );

  const groupedRouteRows = useMemo(
    () => buildGroupedRouteRowsForList(filteredRoutes),
    [filteredRoutes],
  );

  const routeStepsByProduct = useMemo(
    () => buildRouteStepsByProduct(routes || []),
    [routes],
  );

  const {
    routeEditorOpen,
    routeEditorMode,
    routeSourceProductCode,
    routeTargetProductCode,
    setRouteTargetProductCode,
    routeStepsDraft,
    routeSaving,
    routeMessage,
    routeError,
    openCreateRouteEditor,
    openEditRouteEditor,
    openCopyRouteEditor,
    closeRouteEditor,
    updateRouteStep,
    addRouteStep,
    removeRouteStep,
    submitRouteEditor,
    deleteRoute,
  } = useMasterdataRouteEditor({
    routeStepsByProduct,
    refreshMasterdata,
    createEmptyRouteStep,
    normalizeDependencyType,
    modes: {
      create: ROUTE_EDITOR_CREATE,
      edit: ROUTE_EDITOR_EDIT,
      copy: ROUTE_EDITOR_COPY,
    },
  });

  const processOptions = useMemo(
    () => buildProcessOptions(routes || [], lineTopology || []),
    [routes, lineTopology],
  );

  const processDetails = useMemo(
    () => buildProcessDetails(routes || [], processCodeFilter),
    [routes, processCodeFilter],
  );

  const productDetails = useMemo(
    () => buildProductDetails(routes || [], productCodeFilter),
    [routes, productCodeFilter],
  );

  const onClearFocus = useMemo(
    () => () => {
      const next = new URLSearchParams(searchParams);
      next.delete("process_code");
      next.delete("product_code");
      setSearchParams(next);
    },
    [searchParams, setSearchParams],
  );

  return {
    groupedRouteRows,
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
    routeEditorCreateMode: ROUTE_EDITOR_CREATE,
    routeEditorEditMode: ROUTE_EDITOR_EDIT,
    processDetails,
    productDetails,
    openCreateRouteEditor,
    openEditRouteEditor,
    openCopyRouteEditor,
    deleteRoute,
    resolveRouteListDisplayCode: (row) => resolveRouteListDisplayCode(row, ROUTE_PRODUCT_NAME_BY_CODE),
    resolveRouteListProductName: (row) => resolveRouteListProductName(row, ROUTE_PRODUCT_NAME_BY_CODE),
    dependencyTypeLabel,
    onClearFocus,
  };
}

