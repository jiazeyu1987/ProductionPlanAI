const express = require("express");
const cors = require("cors");
const { MemoryStore } = require("./services/memoryStore");

class AppError extends Error {
  constructor(statusCode, code, message, retryable = false) {
    super(message);
    this.statusCode = statusCode;
    this.code = code;
    this.retryable = retryable;
  }
}

function createApp(deps = {}) {
  const app = express();
  const store = deps.store || new MemoryStore();

  app.use(cors());
  app.use(express.json({ limit: "1mb" }));

  app.use((req, res, next) => {
    if (isContractRoute(req.path)) {
      const auth = req.headers.authorization || "";
      if (!auth.startsWith("Bearer ")) {
        return next(new AppError(401, "UNAUTHORIZED", "Bearer token is required."));
      }
    }
    return next();
  });

  app.get("/api/health", (req, res) => {
    res.json({ status: "ok", time: new Date().toISOString() });
  });

  app.get("/api/orders", (req, res) => {
    res.json({ items: store.listOrders() });
  });

  app.post("/api/orders", (req, res, next) => {
    try {
      const requestId = requireRequestId(req);
      const item = store.upsertOrder(req.body || {}, { requestId, operator: "api" });
      res.status(201).json(item);
    } catch (error) {
      next(error);
    }
  });

  app.patch("/api/orders/:orderNo", (req, res, next) => {
    try {
      const requestId = requireRequestId(req);
      const item = store.patchOrder(req.params.orderNo, req.body || {}, { requestId, operator: "api" });
      res.json(item);
    } catch (error) {
      next(error);
    }
  });

  app.post("/api/schedules/generate", (req, res, next) => {
    try {
      const requestId = requireRequestId(req);
      const schedule = store.generateSchedule(
        { ...req.body, request_id: requestId },
        { requestId, operator: "api" }
      );
      res.status(201).json(schedule);
    } catch (error) {
      next(error);
    }
  });

  app.get("/api/schedules", (req, res) => {
    res.json({ items: store.listSchedules() });
  });

  app.get("/api/schedules/latest", (req, res, next) => {
    try {
      res.json(store.getLatestSchedule());
    } catch (error) {
      next(error);
    }
  });

  app.get("/api/schedules/latest/validation", (req, res, next) => {
    try {
      res.json(store.validateSchedule());
    } catch (error) {
      next(error);
    }
  });

  app.get("/api/schedules/:versionNo/validation", (req, res, next) => {
    try {
      res.json(store.validateSchedule(req.params.versionNo));
    } catch (error) {
      next(error);
    }
  });

  app.post("/api/schedules/:versionNo/publish", (req, res, next) => {
    try {
      const requestId = requireRequestId(req);
      res.json(
        store.publishSchedule(req.params.versionNo, { ...req.body, request_id: requestId }, { requestId, operator: "api" })
      );
    } catch (error) {
      next(error);
    }
  });

  app.post("/api/reportings", (req, res, next) => {
    try {
      const requestId = requireRequestId(req);
      const reporting = store.recordReporting(req.body || {}, { requestId, operator: "api" });
      res.status(201).json(reporting);
    } catch (error) {
      next(error);
    }
  });

  app.get("/api/reportings", (req, res) => {
    res.json({ items: store.listReportings() });
  });

  app.post("/api/reset", (req, res) => {
    store.reset();
    res.json({ reset: true });
  });

  app.get("/v1/erp/sales-order-lines", (req, res) => sendList(req, res, store.listSalesOrderLines()));
  app.get("/v1/erp/plan-orders", (req, res) => sendList(req, res, store.listPlanOrders()));
  app.get("/v1/erp/production-orders", (req, res) => sendList(req, res, store.listProductionOrders()));
  app.get("/v1/erp/schedule-controls", (req, res) => sendList(req, res, store.listScheduleControls()));
  app.get("/v1/erp/mrp-links", (req, res) => sendList(req, res, store.listMrpLinks()));
  app.get("/v1/erp/delivery-progress", (req, res) => sendList(req, res, store.listDeliveryProgress()));
  app.get("/v1/erp/material-availability", (req, res) => sendList(req, res, store.listMaterialAvailability()));
  app.get("/v1/mes/equipments", (req, res) => sendList(req, res, store.listEquipments()));
  app.get("/v1/mes/process-routes", (req, res) => sendList(req, res, store.listProcessRoutes()));
  app.get("/v1/mes/reportings", (req, res) => sendList(req, res, store.listReportingsForMes()));
  app.get("/v1/mes/equipment-process-capabilities", (req, res) =>
    sendList(req, res, store.listEquipmentProcessCapabilities())
  );
  app.get("/v1/mes/employee-skills", (req, res) => sendList(req, res, store.listEmployeeSkills()));
  app.get("/v1/mes/shift-calendar", (req, res) => sendList(req, res, store.listShiftCalendar()));

  app.post("/v1/erp/schedule-results", (req, res, next) => {
    try {
      const requestId = requireRequestId(req);
      const response = store.writeScheduleResults(req.body || {}, { requestId, operator: "erp" });
      sendWithRequestId(res, requestId, response);
    } catch (error) {
      next(error);
    }
  });

  app.post("/v1/erp/schedule-status", (req, res, next) => {
    try {
      const requestId = requireRequestId(req);
      const response = store.writeScheduleStatus(req.body || {}, { requestId, operator: "erp" });
      sendWithRequestId(res, requestId, response);
    } catch (error) {
      next(error);
    }
  });

  app.post("/v1/internal/wip-lots", (req, res, next) => {
    try {
      const requestId = requireRequestId(req);
      const response = store.ingestWipLotEvent(req.body || {}, { requestId, operator: "mes" });
      sendWithRequestId(res, requestId, response, 202);
    } catch (error) {
      next(error);
    }
  });

  app.post("/v1/internal/replan-jobs", (req, res, next) => {
    try {
      const requestId = requireRequestId(req);
      store.triggerReplanJob(req.body || {}, { requestId, operator: "system" });
      sendWithRequestId(res, requestId, { request_id: requestId, accepted: true, message: "Replan accepted." }, 202);
    } catch (error) {
      next(error);
    }
  });

  app.get("/internal/v1/internal/order-pool", (req, res) => {
    sendList(req, res, store.listOrderPool(req.query));
  });
  app.get("/internal/v1/internal/schedule-versions", (req, res) => {
    sendList(req, res, store.listScheduleVersions(req.query));
  });
  app.get("/internal/v1/internal/schedule-versions/:version_no/tasks", (req, res, next) => {
    try {
      sendList(req, res, store.getScheduleTasks(req.params.version_no));
    } catch (error) {
      next(error);
    }
  });
  app.get("/internal/v1/internal/schedule-versions/:version_no/diff", (req, res, next) => {
    try {
      const requestId = resolveRequestId(req) || newRequestId();
      const compareWith = req.query.compare_with;
      if (!compareWith) {
        throw new AppError(400, "MISSING_COMPARE_VERSION", "compare_with is required.");
      }
      sendWithRequestId(
        res,
        requestId,
        store.getVersionDiff(req.params.version_no, compareWith, requestId)
      );
    } catch (error) {
      next(error);
    }
  });

  app.get("/internal/v1/internal/dispatch-commands", (req, res) => {
    sendList(req, res, store.listDispatchCommands(req.query));
  });

  app.post("/internal/v1/internal/dispatch-commands", (req, res, next) => {
    try {
      const requestId = requireRequestId(req);
      const response = store.createDispatchCommand(req.body || {}, { requestId, operator: "dispatch" });
      sendWithRequestId(res, requestId, response, 202);
    } catch (error) {
      next(error);
    }
  });

  app.post("/internal/v1/internal/dispatch-commands/:command_id/approvals", (req, res, next) => {
    try {
      const requestId = requireRequestId(req);
      const response = store.approveDispatchCommand(req.params.command_id, req.body || {}, {
        requestId,
        operator: "approver",
      });
      sendWithRequestId(res, requestId, response);
    } catch (error) {
      next(error);
    }
  });

  app.post("/internal/v1/internal/schedule-versions/:version_no/publish", (req, res, next) => {
    try {
      const requestId = requireRequestId(req);
      const response = store.publishSchedule(req.params.version_no, req.body || {}, {
        requestId,
        operator: req.body.operator || "publisher",
      });
      sendWithRequestId(res, requestId, response);
    } catch (error) {
      next(error);
    }
  });

  app.post("/internal/v1/internal/schedule-versions/:version_no/rollback", (req, res, next) => {
    try {
      const requestId = requireRequestId(req);
      const response = store.rollbackSchedule(req.params.version_no, req.body || {}, {
        requestId,
        operator: req.body.operator || "publisher",
      });
      sendWithRequestId(res, requestId, response);
    } catch (error) {
      next(error);
    }
  });

  app.get("/internal/v1/internal/alerts", (req, res) => {
    sendList(req, res, store.listAlerts(req.query));
  });

  app.post("/internal/v1/internal/alerts/:alert_id/ack", (req, res, next) => {
    try {
      const requestId = requireRequestId(req);
      const response = store.ackAlert(req.params.alert_id, req.body || {}, {
        requestId,
        operator: req.body.operator || "operator",
      });
      sendWithRequestId(res, requestId, response);
    } catch (error) {
      next(error);
    }
  });

  app.post("/internal/v1/internal/alerts/:alert_id/close", (req, res, next) => {
    try {
      const requestId = requireRequestId(req);
      const response = store.closeAlert(req.params.alert_id, req.body || {}, {
        requestId,
        operator: req.body.operator || "operator",
      });
      sendWithRequestId(res, requestId, response);
    } catch (error) {
      next(error);
    }
  });

  app.post("/internal/v1/internal/replan-jobs", (req, res, next) => {
    try {
      const requestId = requireRequestId(req);
      store.triggerReplanJob(req.body || {}, { requestId, operator: req.body.operator || "system" });
      sendWithRequestId(res, requestId, { request_id: requestId, accepted: true, message: "Replan accepted." }, 202);
    } catch (error) {
      next(error);
    }
  });

  app.get("/internal/v1/internal/replan-jobs/:job_no", (req, res, next) => {
    try {
      const requestId = resolveRequestId(req) || newRequestId();
      const response = store.getReplanJob(req.params.job_no, requestId);
      sendWithRequestId(res, requestId, response);
    } catch (error) {
      next(error);
    }
  });

  app.get("/internal/v1/internal/audit-logs", (req, res) => {
    sendList(req, res, store.listAuditLogs(req.query));
  });

  app.use((error, req, res, next) => {
    const requestId = resolveRequestId(req) || newRequestId();
    const inferredStatus = /not found/i.test(String(error.message || "")) ? 404 : 400;
    const statusCode = Number(error.statusCode || inferredStatus);
    const code = error.code || (statusCode >= 500 ? "INTERNAL_ERROR" : "BAD_REQUEST");
    const message = error.message || "Unknown error";

    if (isContractRoute(req.path)) {
      sendWithRequestId(
        res,
        requestId,
        {
          request_id: requestId,
          code,
          message,
          retryable: !!error.retryable,
          timestamp: new Date().toISOString(),
        },
        statusCode
      );
      return;
    }

    res.status(statusCode).json({
      error: {
        request_id: requestId,
        code,
        message,
      },
    });
  });

  return { app, store };
}

function sendList(req, res, items) {
  const requestId = resolveRequestId(req) || newRequestId();
  const { page, page_size } = parsePage(req);
  const start = (page - 1) * page_size;
  const paged = (items || []).slice(start, start + page_size);
  sendWithRequestId(res, requestId, {
    request_id: requestId,
    page,
    page_size,
    total: (items || []).length,
    items: paged,
  });
}

function sendWithRequestId(res, requestId, body, statusCode = 200) {
  res.setHeader("x-request-id", requestId);
  res.status(statusCode).json(body);
}

function parsePage(req) {
  const page = Math.max(1, Number(req.query.page || 1));
  const page_size = Math.min(1000, Math.max(1, Number(req.query.page_size || 200)));
  return { page, page_size };
}

function resolveRequestId(req) {
  const headers = req.headers || {};
  return (
    headers["x-request-id"] ||
    headers["x_request_id"] ||
    (req.body && (req.body.request_id || req.body.requestId)) ||
    req.query.request_id ||
    null
  );
}

function requireRequestId(req) {
  const requestId = resolveRequestId(req);
  if (!requestId) {
    throw new AppError(400, "REQUEST_ID_REQUIRED", "request_id is required.");
  }
  return requestId;
}

function isContractRoute(path) {
  return (
    path === "/v1" ||
    path.startsWith("/v1/") ||
    path === "/internal/v1" ||
    path.startsWith("/internal/v1/")
  );
}

function newRequestId() {
  return `req-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

module.exports = {
  createApp,
};
