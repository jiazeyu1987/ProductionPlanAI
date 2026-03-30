import SimpleTable from "../../../components/SimpleTable";
import ProductCell from "../ProductCell";
import { orderStatusText, progressText, toIntText } from "..";
import { formatDateTime } from "../../../utils/datetime";
import OrdersPoolMaterialsPanel from "./OrdersPoolMaterialsPanel";

export default function OrdersPoolOrderDetailPanel({
  selectedOrder,
  selectedOrderNo,
  selectedOrderIsScheduled,
  selectedOrderInProgress,
  expectedStartDraft,
  onExpectedStartDraftChange,
  submitting,
  onSaveExpectedStartTime,
  finishedSummary,
  materialsLoading,
  materialsRefreshing,
  materialsError,
  materialsRefreshWarning,
  materialTreeRows,
  onRefreshMaterialsFromErp,
  onToggleMaterialNode,
  selectedOrderReportings,
  selectedOrderProcessContexts,
}) {
  if (!selectedOrder) {
    return null;
  }

  return (
    <div className="panel">
      <h3>生产订单详情</h3>
      {selectedOrderIsScheduled ? <p className="notice">该生产订单已进入排产。</p> : null}
      <div className="table-wrap">
        <table>
          <tbody>
            <tr>
              <th>生产订单号</th>
              <td>{selectedOrder.order_no}</td>
              <th>产品</th>
              <td>
                <ProductCell
                  code={selectedOrder.product_code}
                  name={selectedOrder.product_name_cn || selectedOrder.product_code}
                />
              </td>
            </tr>
            <tr>
              <th>数量</th>
              <td>{selectedOrder.order_qty ?? "-"}</td>
              <th>承诺交期</th>
              <td>{formatDateTime(selectedOrder.promised_due_date) ?? "-"}</td>
            </tr>
            <tr>
              <th>预计开始时间</th>
              <td>{formatDateTime(selectedOrder.expected_start_time) ?? "-"}</td>
              <th>预计完成时间</th>
              <td>{formatDateTime(selectedOrder.expected_finish_time) ?? "-"}</td>
            </tr>
            <tr>
              <th>调整预计开始</th>
              <td colSpan={3}>
                <div className="toolbar">
                  <input
                    type="date"
                    value={expectedStartDraft}
                    onChange={(e) => onExpectedStartDraftChange(e.target.value)}
                    disabled={submitting}
                  />
                  <button
                    disabled={submitting || !expectedStartDraft}
                    onClick={onSaveExpectedStartTime}
                  >
                    保存并重算预计完成
                  </button>
                </div>
              </td>
            </tr>
            <tr>
              <th>加急</th>
              <td>{Number(selectedOrder.urgent_flag) === 1 ? "是" : "否"}</td>
              <th>冻结</th>
              <td>{Number(selectedOrder.frozen_flag) === 1 ? "是" : "否"}</td>
            </tr>
            <tr>
              <th>状态</th>
              <td colSpan={3}>{orderStatusText(selectedOrder)}</td>
            </tr>
            <tr>
              <th>进行中进度</th>
              <td colSpan={3}>
                {selectedOrderInProgress ? progressText(selectedOrder) : "-"}
              </td>
            </tr>
            <tr>
              <th>成品完成数</th>
              <td>{toIntText(finishedSummary.finishedQty)}</td>
              <th>批次</th>
              <td>{finishedSummary.batchNo}</td>
            </tr>
            <tr>
              <th>成品完成日期</th>
              <td>{formatDateTime(finishedSummary.finishedDate) ?? "-"}</td>
              <th>总量</th>
              <td>{toIntText(finishedSummary.totalQty)}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <OrdersPoolMaterialsPanel
        selectedOrderNo={selectedOrderNo}
        materialsLoading={materialsLoading}
        materialsRefreshing={materialsRefreshing}
        materialsError={materialsError}
        materialsRefreshWarning={materialsRefreshWarning}
        materialTreeRows={materialTreeRows}
        onRefreshMaterialsFromErp={onRefreshMaterialsFromErp}
        onToggleMaterialNode={onToggleMaterialNode}
      />

      <h3>历史报工数据</h3>
      <SimpleTable
        columns={[
          { key: "report_id", title: "报工ID" },
          {
            key: "process_code",
            title: "工序",
            render: (value, row) => row.process_name_cn || value || "-",
          },
          {
            key: "report_qty",
            title: "报工量",
            render: (value) => toIntText(value),
          },
          { key: "report_time", title: "报工时间" },
        ]}
        rows={selectedOrderReportings}
      />

      <h3>工序-车间-产线对应</h3>
      <SimpleTable
        columns={[
          { key: "sequence_no", title: "顺序" },
          { key: "process_name_cn", title: "工序" },
          { key: "workshop_code", title: "车间" },
          { key: "line_code", title: "产线" },
        ]}
        rows={selectedOrderProcessContexts}
      />
    </div>
  );
}
