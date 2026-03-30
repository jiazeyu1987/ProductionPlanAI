import { postContract } from "../../services/api";

export function createDispatchCommand(payload) {
  return postContract("/internal/v1/internal/dispatch-commands", payload);
}

export function approveDispatchCommand(commandId, payload) {
  return postContract(
    `/internal/v1/internal/dispatch-commands/${commandId}/approvals`,
    payload,
  );
}

export function patchOrderPoolOrder(orderNo, payload) {
  return postContract(
    `/internal/v1/internal/order-pool/${encodeURIComponent(orderNo)}/patch`,
    payload,
  );
}

export function deleteOrderPoolOrder(orderNo, payload = {}) {
  return postContract(
    `/internal/v1/internal/order-pool/${encodeURIComponent(orderNo)}/delete`,
    payload,
  );
}
