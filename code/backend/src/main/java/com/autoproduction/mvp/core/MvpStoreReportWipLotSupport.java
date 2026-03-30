package com.autoproduction.mvp.core;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class MvpStoreReportWipLotSupport {
  private MvpStoreReportWipLotSupport() {}

  static Map<String, Object> ingestWipLotEvent(
    MvpStoreReportDomain domain,
    Map<String, Object> payload,
    String requestId,
    String operator
  ) {
    String wipLotId = domain.string(payload, "wip_lot_id", null);
    String orderNo = domain.string(payload, "order_no", null);
    String processCode = domain.string(payload, "process_code", null);
    double qty = domain.number(payload, "qty", -1d);
    String eventTime = domain.string(payload, "event_time", null);
    if (wipLotId == null || orderNo == null || processCode == null || qty < 0 || eventTime == null) {
      throw domain.badRequest("wip lot event payload is invalid.");
    }
    Map<String, Object> lot = domain.state.wipLots.stream()
      .filter(row -> Objects.equals(row.get("wip_lot_id"), wipLotId))
      .findFirst()
      .orElse(null);
    if (lot == null) {
      lot = new LinkedHashMap<>();
      lot.put("wip_lot_id", wipLotId);
      lot.put("order_no", orderNo);
      lot.put("process_code", processCode);
      lot.put("qty", 0d);
      lot.put("status", "ACTIVE");
      lot.put("created_at", OffsetDateTime.now(ZoneOffset.UTC).toString());
      domain.state.wipLots.add(lot);
    }
    lot.put("qty", domain.number(lot, "qty", 0d) + qty);
    lot.put("updated_at", OffsetDateTime.now(ZoneOffset.UTC).toString());

    domain.state.wipLotEvents.add(Map.of(
      "event_id",
      "WIP-EVT-%05d".formatted(domain.state.wipLotEvents.size() + 1),
      "wip_lot_id",
      wipLotId,
      "order_no",
      orderNo,
      "process_code",
      processCode,
      "qty",
      qty,
      "event_time",
      eventTime,
      "request_id",
      requestId
    ));
    domain.appendAudit("WIP_LOT", wipLotId, "INGEST_WIP_EVENT", operator, requestId, null);
    domain.appendInbox("MES_WIP_EVENT", wipLotId, requestId, "SUCCESS", null);
    domain.erpDataManager.refreshTriggered("MES_WIP_EVENT", requestId, "wip lot event accepted");
    return Map.of("request_id", requestId, "accepted", true, "message", "WIP event accepted.");
  }
}

