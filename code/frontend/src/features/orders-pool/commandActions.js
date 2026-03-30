import {
  createAndApproveDispatchCommand,
  deleteOrder,
  markOrderCompleted,
  patchOrderExpectedStartDate,
} from "../order-execution/ordersPoolService";
import { commandLabel } from "./formatters";

async function createOrderCommandAndRefresh(orderNo, commandType, refresh) {
  await createAndApproveDispatchCommand({ orderNo, commandType });
  await refresh();
  return `已执行${commandLabel(commandType)}：${orderNo}`;
}

async function saveExpectedStartDateAndRefresh(orderNo, expectedStartDate, refresh) {
  await patchOrderExpectedStartDate(orderNo, expectedStartDate);
  await refresh();
  return `已更新预计开始时间：${orderNo} -> ${expectedStartDate}`;
}

async function markOrderCompletedAndRefresh(orderNo, row, refresh) {
  await markOrderCompleted(orderNo, row);
  await refresh();
  return `已标记订单完成：${orderNo}`;
}

async function deleteOrderAndRefresh(orderNo, refresh) {
  await deleteOrder(orderNo);
  await refresh();
  return `已删除订单：${orderNo}`;
}

export {
  createOrderCommandAndRefresh,
  deleteOrderAndRefresh,
  markOrderCompletedAndRefresh,
  saveExpectedStartDateAndRefresh,
};
