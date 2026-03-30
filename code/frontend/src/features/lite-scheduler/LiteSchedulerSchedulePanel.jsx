import {
  compareDate,
  isCnStatutoryHoliday,
  makeLineOrderKey,
  WEEKEND_REST_MODE,
} from "../../utils/liteSchedulerEngine";
import { parseIsoAsUtcDate } from "./calendarUtils";
import { formatNumber, toNumber } from "./numberFormatUtils";
import { resolveTaskBorderClass } from "./taskColorUtils";

const WEEKDAY_LABELS = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"];

export function LiteSchedulerSchedulePanel({
  active,
  isDurationMode,
  calendarPlan,
  calendarMonth,
  selectCalendarMonth,
  moveCalendarMonth,
  exportScheduledOrdersExcel,
  calendarWeeks,
  scheduleDateSet,
  scenario,
  orderMetaMap,
  lineOrderColorClassMap,
  setDateWorkMode,
  dateWorkMode,
  openFinishModal,
}) {
  if (!active) {
    return null;
  }

  return (
    <div className="panel">
      <h3>每日工作安排（按产线）</h3>
      <p className="hint">
        {isDurationMode
          ? "按天数模式：可在日历点击订单手动报结束；未报结束会顺延后续订单。"
          : `瓶颈产线：${calendarPlan.summary.bottleneckLineName || "-"}。可在日历上点“休息/排产”按天调整。`}
      </p>

      <div className="toolbar">
        <button
          type="button"
          onClick={() => moveCalendarMonth(-1)}
          data-testid="calendar-prev-month-btn"
        >
          上个月
        </button>
        <label>
          选择月份
          <input
            type="month"
            data-testid="calendar-month-input"
            value={calendarMonth}
            onChange={(e) => selectCalendarMonth(e.target.value)}
          />
        </label>
        <button
          type="button"
          onClick={() => moveCalendarMonth(1)}
          data-testid="calendar-next-month-btn"
        >
          下个月
        </button>
        <button
          type="button"
          data-testid="calendar-export-excel-btn"
          onClick={exportScheduledOrdersExcel}
        >
          导出已排订单
        </button>
      </div>

      <div className="lite-calendar-wrap">
        <div className="lite-calendar-head">
          {WEEKDAY_LABELS.map((label) => (
            <div key={label} className="lite-calendar-weekday">
              {label}
            </div>
          ))}
        </div>

        <div className="lite-calendar-grid">
          {calendarWeeks.flatMap((week, weekIdx) =>
            week.map((date, dayIdx) => {
              if (!date) {
                return (
                  <article
                    key={`${weekIdx}-${dayIdx}`}
                    className="lite-calendar-cell lite-calendar-cell-empty"
                  />
                );
              }

              const inRange = scheduleDateSet.has(date);
              const manualMode = scenario.dateWorkModeByDate?.[date] || null;
              const parsedDate = parseIsoAsUtcDate(date);
              const weekDay = parsedDate?.getUTCDay();
              const isHoliday =
                scenario.skipStatutoryHolidays && isCnStatutoryHoliday(date);
              const isWeekendByMode =
                scenario.skipStatutoryHolidays &&
                (scenario.weekendRestMode === WEEKEND_REST_MODE.DOUBLE
                  ? weekDay === 0 || weekDay === 6
                  : scenario.weekendRestMode === WEEKEND_REST_MODE.SINGLE
                    ? weekDay === 0
                    : false);
              const beforeStart = compareDate(date, scenario.horizonStart) < 0;
              const restReason =
                !inRange && isHoliday
                  ? "法定节假日"
                  : !inRange && isWeekendByMode
                    ? "周末休息"
                    : "";

              const totalAssigned = calendarPlan.lineRows.reduce(
                (sum, line) => sum + (line.daily[date]?.assigned || 0),
                0,
              );
              const totalCapacity = calendarPlan.lineRows.reduce(
                (sum, line) => sum + (line.daily[date]?.capacity || 0),
                0,
              );

              const lineAssignments = inRange
                ? calendarPlan.lineRows
                    .map((line) => {
                      const items = line.daily[date]?.items || [];
                      if (items.length === 0) {
                        return null;
                      }

                      return {
                        lineId: line.lineId,
                        lineName: line.lineName,
                        orders: items.map((item) => {
                          const orderMeta = orderMetaMap[item.orderId];
                          const orderNo = orderMeta?.orderNo || item.orderId;
                          const productName = String(
                            orderMeta?.productName || "",
                          ).trim();
                          const label = productName
                            ? `${orderNo}/${productName}`
                            : orderNo;
                          const spec = String(orderMeta?.spec || "").trim();
                          const batchNo = String(orderMeta?.batchNo || "").trim();
                          const orderMetaParts = [];
                          if (spec) {
                            orderMetaParts.push(`规格:${spec}`);
                          }
                          if (batchNo) {
                            orderMetaParts.push(`批号:${batchNo}`);
                          }
                          const linePlanQty = Math.max(
                            0,
                            Math.round(
                              toNumber(
                                orderMeta?.linePlanQuantities?.[line.lineId],
                                0,
                              ),
                            ),
                          );
                          const lineWorkloadQty = Math.max(
                            0,
                            toNumber(orderMeta?.lineWorkloads?.[line.lineId], 0),
                          );
                          const lineTotalQty = isDurationMode
                            ? linePlanQty
                            : lineWorkloadQty;
                          orderMetaParts.push(`数量:${formatNumber(lineTotalQty)}`);

                          const lineOrderKey = makeLineOrderKey(
                            line.lineId,
                            item.orderId,
                          );
                          const manualFinishDate =
                            scenario.manualFinishByLineOrder?.[lineOrderKey] || "";

                          return {
                            id: `${date}-${line.lineId}-${item.orderId}`,
                            lineId: line.lineId,
                            lineName: line.lineName,
                            orderId: item.orderId,
                            orderLabel: label,
                            taskBorderClass:
                              lineOrderColorClassMap[lineOrderKey] ||
                              resolveTaskBorderClass(item.orderId),
                            text: isDurationMode
                              ? label
                              : `${label}(${formatNumber(item.workloadDays)})`,
                            metaText: orderMetaParts.join(" "),
                            segmentStartDate: item.segmentStartDate || date,
                            manualFinishDate,
                          };
                        }),
                      };
                    })
                    .filter(Boolean)
                : [];

              const dayCellClassName = [
                "lite-calendar-cell",
                inRange ? "" : "lite-calendar-cell-out",
                isHoliday && !restReason ? "lite-calendar-cell-holiday" : "",
                restReason ? "lite-calendar-cell-rest" : "",
              ]
                .filter(Boolean)
                .join(" ");

              return (
                <article
                  key={`${weekIdx}-${date}`}
                  data-testid={`calendar-day-${date}`}
                  className={dayCellClassName}
                >
                  <div className="lite-calendar-date">{date.slice(8)}</div>

                  {restReason ? (
                    <div className="lite-holiday-tag">{restReason}</div>
                  ) : null}

                  <div className="lite-cal-summary">
                    <span>{isDurationMode ? "总排线 / 产线数" : "总排产 / 产能"}</span>
                    <strong>
                      {formatNumber(totalAssigned)} / {formatNumber(totalCapacity)}
                    </strong>
                  </div>

                  {!beforeStart ? (
                    <div className="lite-cal-mode-actions">
                      <button
                        type="button"
                        data-testid={`calendar-toggle-${date}`}
                        className={`lite-cal-mode-btn ${inRange ? "lite-cal-mode-btn-rest" : "lite-cal-mode-btn-work"}`}
                        onClick={() =>
                          setDateWorkMode(
                            date,
                            inRange ? dateWorkMode.REST : dateWorkMode.WORK,
                          )
                        }
                      >
                        {inRange ? "休息" : "排产"}
                      </button>
                      {manualMode ? (
                        <button
                          type="button"
                          className="lite-cal-mode-btn"
                          data-testid={`calendar-clear-mode-${date}`}
                          onClick={() => setDateWorkMode(date, null)}
                        >
                          恢复默认
                        </button>
                      ) : null}
                    </div>
                  ) : null}

                  <div className="lite-cal-line-list">
                    {lineAssignments.length === 0 ? (
                      <span className="hint">无分配</span>
                    ) : (
                      lineAssignments.map((entry) => (
                        <div
                          key={`${date}-${entry.lineId}`}
                          className="lite-cal-line-item"
                        >
                          <div className="lite-cal-line-name">{entry.lineName}</div>
                          <div className="lite-cal-line-orders">
                            {isDurationMode
                              ? entry.orders.map((orderItem, orderIdx) => (
                                  <button
                                    type="button"
                                    key={`${orderItem.id}-${orderIdx}`}
                                    className={`lite-cal-order-btn ${orderItem.taskBorderClass || ""}`}
                                    data-testid={`calendar-order-${date}-${entry.lineId}-${orderItem.orderId}-${orderIdx}`}
                                    onClick={() => openFinishModal(orderItem)}
                                  >
                                    <span className="lite-cal-order-main">
                                      {orderItem.text}
                                    </span>
                                    {orderItem.metaText ? (
                                      <span className="lite-cal-order-meta">
                                        {orderItem.metaText}
                                      </span>
                                    ) : null}
                                    {orderItem.manualFinishDate
                                      ? `（已结束:${orderItem.manualFinishDate}）`
                                      : ""}
                                  </button>
                                ))
                              : entry.orders.map((orderItem, orderIdx) => (
                                  <span
                                    key={`${orderItem.id}-${orderIdx}`}
                                    className={`lite-cal-order-chip ${orderItem.taskBorderClass || ""}`}
                                  >
                                    <span className="lite-cal-order-main">
                                      {orderItem.text}
                                    </span>
                                    {orderItem.metaText ? (
                                      <span className="lite-cal-order-meta">
                                        {orderItem.metaText}
                                      </span>
                                    ) : null}
                                  </span>
                                ))}
                          </div>
                        </div>
                      ))
                    )}
                  </div>
                </article>
              );
            }),
          )}
        </div>
      </div>
    </div>
  );
}
