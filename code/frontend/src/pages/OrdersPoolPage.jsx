import OrdersPoolOrderDetailPanel from "../features/orders-pool/page/OrdersPoolOrderDetailPanel";
import OrdersPoolOrdersTable from "../features/orders-pool/page/OrdersPoolOrdersTable";
import OrdersPoolToolbar from "../features/orders-pool/page/OrdersPoolToolbar";
import { useOrdersPoolPageController } from "../features/orders-pool/page/useOrdersPoolPageController";

export default function OrdersPoolPage() {
  const controller = useOrdersPoolPageController();
  const totalCount = controller.allRows?.length ?? 0;
  const filteredCount = controller.filteredRows?.length ?? 0;

  return (
    <section>
      <h2>生产订单</h2>
      <OrdersPoolToolbar
        submitting={controller.submitting}
        onRefresh={() => controller.refresh().catch(() => {})}
        productKeyword={controller.productKeyword}
        onProductKeywordChange={controller.setProductKeyword}
        showUnfinishedOnly={controller.showUnfinishedOnly}
        onShowUnfinishedOnlyChange={controller.setShowUnfinishedOnly}
        orderNoFilter={controller.orderNoFilter}
        onClearOrderNoFilter={controller.clearOrderNoFilter}
        totalCount={totalCount}
        filteredCount={filteredCount}
      />
      {controller.orderNoFilter ? (
        <p className="hint">当前按生产订单定位：{controller.orderNoFilter}</p>
      ) : null}
      {controller.error ? <p className="error">{controller.error}</p> : null}
      {controller.notice ? <p className="notice">{controller.notice}</p> : null}
      {!controller.error && totalCount > 0 && filteredCount === 0 ? (
        <p className="notice">当前筛选条件无匹配，请清空“产品关键词过滤”或取消“仅显示未完成订单”。</p>
      ) : null}

      <OrdersPoolOrderDetailPanel
        selectedOrder={controller.selectedOrder}
        selectedOrderNo={controller.selectedOrderNo}
        selectedOrderIsScheduled={controller.selectedOrderIsScheduled}
        selectedOrderInProgress={controller.selectedOrderInProgress}
        expectedStartDraft={controller.expectedStartDraft}
        onExpectedStartDraftChange={controller.setExpectedStartDraft}
        submitting={controller.submitting}
        onSaveExpectedStartTime={() => controller.saveExpectedStartTime().catch(() => {})}
        finishedSummary={controller.finishedSummary}
        materialsLoading={controller.materialsLoading}
        materialsRefreshing={controller.materialsRefreshing}
        materialsError={controller.materialsError}
        materialsRefreshWarning={controller.materialsRefreshWarning}
        materialTreeRows={controller.materialTreeRows}
        onRefreshMaterialsFromErp={() => controller.refreshMaterialsFromErp().catch(() => {})}
        onToggleMaterialNode={(row) => controller.toggleMaterialNode(row).catch(() => {})}
        selectedOrderReportings={controller.selectedOrderReportings}
        selectedOrderProcessContexts={controller.selectedOrderProcessContexts}
      />
      {controller.orderNoFilter && !controller.selectedOrder ? (
        <p className="error">未找到该生产订单，请检查生产订单号。</p>
      ) : null}

      <OrdersPoolOrdersTable
        rows={controller.filteredRows}
        submitting={controller.submitting}
        onCreateCommand={(orderNo, commandType) => controller.createCommand(orderNo, commandType)}
        onDeleteOrder={(orderNo) => controller.deleteOrder(orderNo)}
        onMarkCompleted={(orderNo, row) => controller.markCompleted(orderNo, row)}
      />
    </section>
  );
}
