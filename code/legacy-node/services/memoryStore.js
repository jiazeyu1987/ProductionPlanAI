const { buildSeedData } = require("../data/seedData");
const { generateSchedule } = require("./schedulerEngine");
const { validateSchedule } = require("./scheduleValidator");

class MemoryStore {
  constructor(seedFactory = buildSeedData) {
    this.seedFactory = seedFactory;
    this.reset();
  }

  reset() {
    const seed = deepClone(this.seedFactory());
    this.state = {
      startDate: seed.startDate,
      horizonDays: seed.horizonDays,
      shiftsPerDay: seed.shiftsPerDay,
      shiftHours: seed.shiftHours,
      strictRoute: seed.strictRoute,
      shiftCalendar: seed.shiftCalendar || [],
      processes: seed.processes || [],
      processRoutes: seed.processRoutes || {},
      workerPools: seed.workerPools || [],
      machinePools: seed.machinePools || [],
      materialAvailability: seed.materialAvailability || [],
      orders: (seed.orders || []).map((order) => normalizeOrder(order)),
      schedules: [],
      publishedVersionNo: null,
      reportings: [],
      scheduleResultWrites: [],
      scheduleStatusWrites: [],
      wipLots: [],
      wipLotEvents: [],
      replanJobs: [],
      alerts: [],
      auditLogs: [],
      dispatchCommands: [],
      dispatchApprovals: [],
      idempotencyLedger: new Map(),
    };
  }

  getPlanningInput(options = {}) {
    const excludeLockedOrders = !!options.excludeLockedOrders;
    return {
      startDate: this.state.startDate,
      horizonDays: this.state.horizonDays,
      shiftsPerDay: this.state.shiftsPerDay,
      shiftHours: this.state.shiftHours,
      strictRoute: this.state.strictRoute,
      shiftCalendar: deepClone(this.state.shiftCalendar),
      processes: deepClone(this.state.processes),
      processRoutes: deepClone(this.state.processRoutes),
      workerPools: deepClone(this.state.workerPools),
      machinePools: deepClone(this.state.machinePools),
      materialAvailability: deepClone(this.state.materialAvailability),
      orders: this.state.orders.map((order) => ({
        orderNo: order.orderNo,
        dueDate: order.dueDate,
        urgent: order.urgent,
        frozen: order.frozen || (excludeLockedOrders && order.lockFlag),
        items: order.items.map((item) => ({
          productCode: item.productCode,
          qty: Math.max(0, item.qty - item.completedQty),
        })),
      })),
    };
  }

  listOrders() {
    return deepClone(this.state.orders);
  }

  listOrderPool(filters = {}) {
    let items = this.state.orders.map((order) => toOrderPoolItem(order));
    if (filters.status) {
      items = items.filter((item) => item.status === filters.status);
    }
    if (Object.prototype.hasOwnProperty.call(filters, "frozen_flag")) {
      items = items.filter((item) => item.frozen_flag === Number(filters.frozen_flag));
    }
    if (Object.prototype.hasOwnProperty.call(filters, "urgent_flag")) {
      items = items.filter((item) => item.urgent_flag === Number(filters.urgent_flag));
    }
    return deepClone(items);
  }

  getOrder(orderNo) {
    return deepClone(this.findOrder(orderNo));
  }

  upsertOrder(payload, context = {}) {
    const orderNo = pick(payload, ["orderNo", "order_no"]);
    if (!orderNo) {
      throw new Error("orderNo is required.");
    }

    const found = this.state.orders.find((order) => order.orderNo === orderNo);
    if (!found) {
      const created = normalizeOrder(payload);
      this.state.orders.push(created);
      this.appendAudit({
        entityType: "ORDER",
        entityId: created.orderNo,
        action: "CREATE_ORDER",
        operator: context.operator || "system",
        requestId: context.requestId,
      });
      return deepClone(created);
    }

    if (pick(payload, ["dueDate", "due_date"])) {
      found.dueDate = pick(payload, ["dueDate", "due_date"]);
    }
    if (Object.prototype.hasOwnProperty.call(payload, "urgent")) {
      found.urgent = !!payload.urgent;
    }
    if (Object.prototype.hasOwnProperty.call(payload, "urgent_flag")) {
      found.urgent = Number(payload.urgent_flag) === 1;
    }
    if (Object.prototype.hasOwnProperty.call(payload, "frozen")) {
      found.frozen = !!payload.frozen;
    }
    if (Object.prototype.hasOwnProperty.call(payload, "frozen_flag")) {
      found.frozen = Number(payload.frozen_flag) === 1;
    }
    if (Object.prototype.hasOwnProperty.call(payload, "lockFlag")) {
      found.lockFlag = !!payload.lockFlag;
    }
    if (Object.prototype.hasOwnProperty.call(payload, "lock_flag")) {
      found.lockFlag = Number(payload.lock_flag) === 1;
    }
    if (pick(payload, ["status", "order_status"])) {
      found.status = pick(payload, ["status", "order_status"]);
    }

    if (Array.isArray(payload.items) && payload.items.length > 0) {
      found.items = payload.items.map((item) => ({
        productCode: pick(item, ["productCode", "product_code"]),
        qty: Number(item.qty),
        completedQty: Number(pick(item, ["completedQty", "completed_qty"]) || 0),
      }));
    }

    this.appendAudit({
      entityType: "ORDER",
      entityId: found.orderNo,
      action: "UPDATE_ORDER",
      operator: context.operator || "system",
      requestId: context.requestId,
    });
    return deepClone(found);
  }

  patchOrder(orderNo, patch, context = {}) {
    const order = this.findOrder(orderNo);

    if (Object.prototype.hasOwnProperty.call(patch, "frozen")) {
      order.frozen = !!patch.frozen;
    }
    if (Object.prototype.hasOwnProperty.call(patch, "frozen_flag")) {
      order.frozen = Number(patch.frozen_flag) === 1;
    }
    if (Object.prototype.hasOwnProperty.call(patch, "urgent")) {
      order.urgent = !!patch.urgent;
    }
    if (Object.prototype.hasOwnProperty.call(patch, "urgent_flag")) {
      order.urgent = Number(patch.urgent_flag) === 1;
    }
    if (Object.prototype.hasOwnProperty.call(patch, "lockFlag")) {
      order.lockFlag = !!patch.lockFlag;
    }
    if (Object.prototype.hasOwnProperty.call(patch, "lock_flag")) {
      order.lockFlag = Number(patch.lock_flag) === 1;
    }
    if (patch.dueDate) {
      order.dueDate = patch.dueDate;
    }
    if (patch.due_date) {
      order.dueDate = patch.due_date;
    }
    if (patch.status) {
      order.status = patch.status;
    }
    if (patch.order_status) {
      order.status = patch.order_status;
    }

    if (Array.isArray(patch.items)) {
      for (const itemPatch of patch.items) {
        const productCode = pick(itemPatch, ["productCode", "product_code"]);
        const item = order.items.find((it) => it.productCode === productCode);
        if (!item) {
          continue;
        }
        if (Object.prototype.hasOwnProperty.call(itemPatch, "qty")) {
          item.qty = Number(itemPatch.qty);
          item.completedQty = Math.min(item.completedQty, item.qty);
        }
      }
    }

    this.appendAudit({
      entityType: "ORDER",
      entityId: orderNo,
      action: "PATCH_ORDER",
      operator: context.operator || "system",
      requestId: context.requestId,
    });
    return deepClone(order);
  }

  generateSchedule(options = {}, context = {}) {
    const excludedLockedOrders = options.autoReplan
      ? this.state.orders.filter((order) => order.lockFlag).map((order) => order.orderNo)
      : [];
    const planningInput = this.getPlanningInput({
      excludeLockedOrders: !!options.autoReplan,
    });
    const versionNo = `V${String(this.state.schedules.length + 1).padStart(3, "0")}`;
    const schedule = generateSchedule(planningInput, {
      requestId: pick(options, ["requestId", "request_id"]),
      versionNo,
    });

    schedule.status = "DRAFT";
    schedule.basedOnVersion = pick(options, ["baseVersionNo", "base_version_no"]) || null;
    schedule.ruleVersionNo = "RULE-P0-BASE";
    schedule.publishTime = null;
    schedule.createdBy = context.operator || "system";
    schedule.createdAt = schedule.generatedAt;
    schedule.metadata = {
      ...schedule.metadata,
      autoReplan: !!options.autoReplan,
      excludedLockedOrders,
    };

    this.state.schedules.push(schedule);
    this.appendAudit({
      entityType: "SCHEDULE_VERSION",
      entityId: schedule.versionNo,
      action: options.autoReplan ? "AUTO_REPLAN_SCHEDULE" : "GENERATE_SCHEDULE",
      operator: context.operator || "system",
      requestId: pick(options, ["requestId", "request_id"]) || context.requestId,
      reason: options.reason,
    });
    return deepClone(schedule);
  }

  listSchedules() {
    return deepClone(this.state.schedules);
  }

  listScheduleVersions(filters = {}) {
    let versions = this.state.schedules.map((schedule) => ({
      version_no: schedule.versionNo,
      status: schedule.status || "DRAFT",
      based_on_version: schedule.basedOnVersion || null,
      rule_version_no: schedule.ruleVersionNo || "RULE-P0-BASE",
      publish_time: schedule.publishTime,
      created_by: schedule.createdBy || "system",
      created_at: schedule.createdAt || schedule.generatedAt,
    }));
    if (filters.status) {
      versions = versions.filter((item) => item.status === filters.status);
    }
    return deepClone(versions);
  }

  getLatestSchedule() {
    if (this.state.schedules.length === 0) {
      throw new Error("No schedule generated.");
    }
    return deepClone(this.state.schedules[this.state.schedules.length - 1]);
  }

  getSchedule(versionNo) {
    const found = this.state.schedules.find((item) => item.versionNo === versionNo);
    if (!found) {
      throw new Error(`Schedule ${versionNo} not found.`);
    }
    return deepClone(found);
  }

  getScheduleTasks(versionNo) {
    const schedule = this.getSchedule(versionNo);
    const tasks = [];
    let id = 1;
    for (const allocation of schedule.allocations || []) {
      const times = toShiftTimeWindow(allocation.date, allocation.shiftCode);
      tasks.push({
        id,
        version_no: schedule.versionNo,
        order_no: allocation.orderNo,
        order_type: "production",
        process_code: allocation.processCode,
        calendar_date: allocation.date,
        shift_code: allocation.shiftCode === "D" ? "DAY" : "NIGHT",
        plan_start_time: times.plan_start_time,
        plan_finish_time: times.plan_finish_time,
        plan_qty: allocation.scheduledQty,
        lock_flag: this.findOrder(allocation.orderNo).lockFlag ? 1 : 0,
        priority: this.findOrder(allocation.orderNo).urgent ? 1 : 0,
      });
      id += 1;
    }
    return tasks;
  }

  getVersionDiff(versionNo, compareWith, requestId) {
    const base = this.getSchedule(compareWith);
    const current = this.getSchedule(versionNo);
    const baseMap = new Map();
    const currentMap = new Map();

    for (const row of base.allocations || []) {
      baseMap.set(`${row.taskKey}#${row.shiftId}`, Number(row.scheduledQty));
    }
    for (const row of current.allocations || []) {
      currentMap.set(`${row.taskKey}#${row.shiftId}`, Number(row.scheduledQty));
    }

    const allKeys = new Set([...baseMap.keys(), ...currentMap.keys()]);
    let changedTaskCount = 0;
    for (const key of allKeys) {
      if (Math.abs(Number(baseMap.get(key) || 0) - Number(currentMap.get(key) || 0)) > 1e-9) {
        changedTaskCount += 1;
      }
    }

    const baseOrders = new Set((base.allocations || []).map((row) => row.orderNo));
    const currentOrders = new Set((current.allocations || []).map((row) => row.orderNo));
    let changedOrderCount = 0;
    for (const orderNo of new Set([...baseOrders, ...currentOrders])) {
      if (!baseOrders.has(orderNo) || !currentOrders.has(orderNo)) {
        changedOrderCount += 1;
      }
    }

    return {
      request_id: requestId,
      base_version_no: compareWith,
      compare_version_no: versionNo,
      changed_order_count: changedOrderCount,
      changed_task_count: changedTaskCount,
      delayed_order_delta: (current.unscheduled || []).length - (base.unscheduled || []).length,
      overloaded_process_delta: countCapacityBlocked(current.unscheduled) - countCapacityBlocked(base.unscheduled),
    };
  }

  publishSchedule(versionNo, payload = {}, context = {}) {
    const requestId = pick(payload, ["request_id", "requestId"]) || context.requestId;
    return this.runIdempotent(requestId, `PUBLISH#${versionNo}`, () => {
      const schedule = this.state.schedules.find((item) => item.versionNo === versionNo);
      if (!schedule) {
        throw new Error(`Schedule ${versionNo} not found.`);
      }

      const previousPublished = this.state.publishedVersionNo
        ? this.state.schedules.find((item) => item.versionNo === this.state.publishedVersionNo)
        : null;
      if (previousPublished && previousPublished.versionNo !== versionNo) {
        previousPublished.status = "SUPERSEDED";
      }

      this.state.publishedVersionNo = versionNo;
      schedule.status = "PUBLISHED";
      schedule.publishTime = nowIso();

      this.appendAudit({
        entityType: "SCHEDULE_VERSION",
        entityId: versionNo,
        action: "PUBLISH_VERSION",
        operator: payload.operator || context.operator || "system",
        requestId,
        reason: payload.reason,
      });

      return {
        request_id: requestId,
        success: true,
        message: `Version ${versionNo} published.`,
        version_no: versionNo,
        versionNo,
        publishedAt: schedule.publishTime,
        status: "PUBLISHED",
      };
    });
  }

  rollbackSchedule(versionNo, payload = {}, context = {}) {
    const requestId = pick(payload, ["request_id", "requestId"]) || context.requestId;
    return this.runIdempotent(requestId, `ROLLBACK#${versionNo}`, () => {
      const target = this.state.schedules.find((item) => item.versionNo === versionNo);
      if (!target) {
        throw new Error(`Schedule ${versionNo} not found.`);
      }

      const currentPublished = this.state.publishedVersionNo
        ? this.state.schedules.find((item) => item.versionNo === this.state.publishedVersionNo)
        : null;
      if (currentPublished && currentPublished.versionNo !== versionNo) {
        currentPublished.status = "ROLLED_BACK";
      }

      target.status = "PUBLISHED";
      target.rollbackFrom = currentPublished ? currentPublished.versionNo : null;
      target.publishTime = nowIso();
      this.state.publishedVersionNo = versionNo;

      this.appendAudit({
        entityType: "SCHEDULE_VERSION",
        entityId: versionNo,
        action: "ROLLBACK_VERSION",
        operator: payload.operator || context.operator || "system",
        requestId,
        reason: payload.reason,
      });

      return {
        request_id: requestId,
        success: true,
        message: `Rollback to ${versionNo} completed.`,
        version_no: versionNo,
      };
    });
  }

  validateSchedule(versionNo) {
    const schedule = versionNo ? this.getSchedule(versionNo) : this.getLatestSchedule();
    const validation = validateSchedule(this.getPlanningInput(), schedule);
    return {
      versionNo: schedule.versionNo,
      ...validation,
    };
  }

  recordReporting(payload, context = {}) {
    const requestId = pick(payload, ["request_id", "requestId"]) || context.requestId;
    const orderNo = pick(payload, ["orderNo", "order_no"]);
    const order = this.findOrder(orderNo);
    const productCode = pick(payload, ["productCode", "product_code"]) || (order.items[0] && order.items[0].productCode);
    const item = order.items.find((it) => it.productCode === productCode);
    if (!item) {
      throw new Error(`Product ${productCode} not found in order ${orderNo}.`);
    }

    const reportQty = Number(pick(payload, ["reportQty", "report_qty"]));
    if (!Number.isFinite(reportQty) || reportQty <= 0) {
      throw new Error("reportQty must be > 0.");
    }

    item.completedQty = Math.min(item.qty, item.completedQty + reportQty);
    const reporting = {
      reportingId: `RPT-${String(this.state.reportings.length + 1).padStart(5, "0")}`,
      request_id: requestId,
      orderNo,
      productCode,
      processCode: pick(payload, ["processCode", "process_code"]) || "UNKNOWN",
      reportQty,
      reportTime: nowIso(),
    };

    this.state.reportings.push(reporting);
    this.appendAudit({
      entityType: "REPORTING",
      entityId: reporting.reportingId,
      action: "CREATE_REPORTING",
      operator: context.operator || "mes",
      requestId,
    });

    const trigger = this.maybeTriggerProgressGapReplan(reporting, requestId);
    if (trigger) {
      reporting.triggered_replan_job_no = trigger.job_no;
      reporting.triggered_alert_id = trigger.alert_id;
    }
    return deepClone(reporting);
  }

  listReportings() {
    return deepClone(this.state.reportings);
  }

  listReportingsForMes() {
    return this.state.reportings.map((item) => ({
      report_id: item.reportingId,
      order_no: item.orderNo,
      order_type: "production",
      process_code: item.processCode,
      report_qty: item.reportQty,
      report_time: item.reportTime,
      shift_code: deriveShiftCodeByTime(item.reportTime),
      team_code: "TEAM-A",
      operator_code: "OP-A",
      last_update_time: item.reportTime,
    }));
  }

  writeScheduleResults(payload, context = {}) {
    const requestId = pick(payload, ["request_id", "requestId"]) || context.requestId;
    return this.runIdempotent(requestId, "WRITE_SCHEDULE_RESULTS", () => {
      const scheduleVersion = pick(payload, ["schedule_version", "scheduleVersion"]);
      const items = Array.isArray(payload.items) ? payload.items : [];
      if (!scheduleVersion) {
        throw new Error("schedule_version is required.");
      }
      if (items.length === 0) {
        throw new Error("items is required.");
      }

      let successCount = 0;
      let failedCount = 0;
      for (const row of items) {
        const orderNo = pick(row, ["order_no", "orderNo"]);
        const order = this.state.orders.find((item) => item.orderNo === orderNo);
        if (!order) {
          failedCount += 1;
          continue;
        }
        if (Object.prototype.hasOwnProperty.call(row, "lock_flag")) {
          order.lockFlag = Number(row.lock_flag) === 1;
        }
        if (Object.prototype.hasOwnProperty.call(row, "lockFlag")) {
          order.lockFlag = !!row.lockFlag;
        }
        successCount += 1;
      }

      this.state.scheduleResultWrites.push({
        request_id: requestId,
        schedule_version: scheduleVersion,
        item_count: items.length,
        created_at: nowIso(),
      });
      this.appendAudit({
        entityType: "ERP_WRITEBACK",
        entityId: scheduleVersion,
        action: "WRITE_SCHEDULE_RESULTS",
        operator: context.operator || "erp",
        requestId,
      });

      return {
        request_id: requestId,
        success_count: successCount,
        failed_count: failedCount,
      };
    });
  }

  writeScheduleStatus(payload, context = {}) {
    const requestId = pick(payload, ["request_id", "requestId"]) || context.requestId;
    return this.runIdempotent(requestId, "WRITE_SCHEDULE_STATUS", () => {
      const scheduleVersion = pick(payload, ["schedule_version", "scheduleVersion"]);
      const items = Array.isArray(payload.items) ? payload.items : [];
      if (!scheduleVersion) {
        throw new Error("schedule_version is required.");
      }
      if (items.length === 0) {
        throw new Error("items is required.");
      }

      let successCount = 0;
      let failedCount = 0;
      for (const row of items) {
        const orderNo = pick(row, ["order_no", "orderNo"]);
        const status = pick(row, ["status"]);
        const order = this.state.orders.find((item) => item.orderNo === orderNo);
        if (!order || !status) {
          failedCount += 1;
          continue;
        }
        order.status = status;
        successCount += 1;
      }

      this.state.scheduleStatusWrites.push({
        request_id: requestId,
        schedule_version: scheduleVersion,
        item_count: items.length,
        created_at: nowIso(),
      });
      this.appendAudit({
        entityType: "ERP_WRITEBACK",
        entityId: scheduleVersion,
        action: "WRITE_SCHEDULE_STATUS",
        operator: context.operator || "erp",
        requestId,
      });

      return {
        request_id: requestId,
        success_count: successCount,
        failed_count: failedCount,
      };
    });
  }

  ingestWipLotEvent(payload, context = {}) {
    const requestId = pick(payload, ["request_id", "requestId"]) || context.requestId;
    return this.runIdempotent(requestId, "INGEST_WIP_EVENT", () => {
      const wipLotId = pick(payload, ["wip_lot_id", "wipLotId"]);
      const orderNo = pick(payload, ["order_no", "orderNo"]);
      const processCode = pick(payload, ["process_code", "processCode"]);
      const qty = Number(pick(payload, ["qty"]));
      const eventTime = pick(payload, ["event_time", "eventTime"]);
      if (!wipLotId || !orderNo || !processCode || !Number.isFinite(qty) || !eventTime) {
        throw new Error("wip lot event payload is invalid.");
      }

      let lot = this.state.wipLots.find((item) => item.wip_lot_id === wipLotId);
      if (!lot) {
        lot = {
          wip_lot_id: wipLotId,
          order_no: orderNo,
          process_code: processCode,
          qty: 0,
          status: "ACTIVE",
          created_at: nowIso(),
          updated_at: nowIso(),
        };
        this.state.wipLots.push(lot);
      }
      lot.qty += qty;
      lot.updated_at = nowIso();

      this.state.wipLotEvents.push({
        event_id: `WIP-EVT-${String(this.state.wipLotEvents.length + 1).padStart(5, "0")}`,
        wip_lot_id: wipLotId,
        order_no: orderNo,
        process_code: processCode,
        qty,
        event_time: eventTime,
        request_id: requestId,
      });

      this.appendAudit({
        entityType: "WIP_LOT",
        entityId: wipLotId,
        action: "INGEST_WIP_EVENT",
        operator: context.operator || "mes",
        requestId,
      });

      return {
        request_id: requestId,
        accepted: true,
        message: "WIP event accepted.",
      };
    });
  }

  triggerReplanJob(payload, context = {}) {
    const requestId = pick(payload, ["request_id", "requestId"]) || context.requestId;
    return this.runIdempotent(requestId, "TRIGGER_REPLAN", () => {
      const baseVersionNo = pick(payload, ["base_version_no", "baseVersionNo"]);
      if (!baseVersionNo) {
        throw new Error("base_version_no is required.");
      }
      this.getSchedule(baseVersionNo);

      const jobNo = `RPJ-${String(this.state.replanJobs.length + 1).padStart(5, "0")}`;
      const job = {
        request_id: requestId,
        job_no: jobNo,
        trigger_type: pick(payload, ["trigger_type", "triggerType"]) || "PROGRESS_GAP",
        scope_type: pick(payload, ["scope_type", "scopeType"]) || "LOCAL",
        base_version_no: baseVersionNo,
        result_version_no: null,
        status: "RUNNING",
        created_at: nowIso(),
        finished_at: null,
      };
      this.state.replanJobs.push(job);

      try {
        const schedule = this.generateSchedule(
          {
            request_id: `${requestId}:generate`,
            base_version_no: baseVersionNo,
            autoReplan: true,
            reason: pick(payload, ["reason"]) || "Triggered by replan job",
          },
          {
            operator: context.operator || "system",
            requestId,
          }
        );
        job.result_version_no = schedule.versionNo;
        job.status = "DONE";
        job.finished_at = nowIso();
      } catch (error) {
        job.status = "FAILED";
        job.finished_at = nowIso();
        throw error;
      }

      this.appendAudit({
        entityType: "REPLAN_JOB",
        entityId: jobNo,
        action: "TRIGGER_REPLAN",
        operator: context.operator || "system",
        requestId,
        reason: pick(payload, ["reason"]),
      });

      return deepClone(job);
    });
  }

  getReplanJob(jobNo, requestId) {
    const found = this.state.replanJobs.find((item) => item.job_no === jobNo);
    if (!found) {
      throw new Error(`Replan job ${jobNo} not found.`);
    }
    return {
      request_id: requestId,
      ...deepClone(found),
    };
  }

  listAlerts(filters = {}) {
    let alerts = [...this.state.alerts];
    if (filters.status) {
      alerts = alerts.filter((item) => item.status === filters.status);
    }
    if (filters.severity) {
      alerts = alerts.filter((item) => item.severity === filters.severity);
    }
    return deepClone(alerts);
  }

  ackAlert(alertId, payload, context = {}) {
    return this.updateAlertState(alertId, payload, context, "ACKED");
  }

  closeAlert(alertId, payload, context = {}) {
    return this.updateAlertState(alertId, payload, context, "CLOSED");
  }

  listAuditLogs(filters = {}) {
    let logs = [...this.state.auditLogs];
    if (filters.entity_type) {
      logs = logs.filter((item) => item.entity_type === filters.entity_type);
    }
    if (filters.request_id) {
      logs = logs.filter((item) => item.request_id === filters.request_id);
    }
    return deepClone(logs);
  }

  createDispatchCommand(payload, context = {}) {
    const requestId = pick(payload, ["request_id", "requestId"]) || context.requestId;
    return this.runIdempotent(requestId, "CREATE_DISPATCH", () => {
      const commandId = `CMD-${String(this.state.dispatchCommands.length + 1).padStart(5, "0")}`;
      const command = {
        command_id: commandId,
        command_type: pick(payload, ["command_type", "commandType"]),
        target_order_no: pick(payload, ["target_order_no", "targetOrderNo"]),
        target_order_type: pick(payload, ["target_order_type", "targetOrderType"]) || "production",
        effective_time: pick(payload, ["effective_time", "effectiveTime"]) || nowIso(),
        reason: pick(payload, ["reason"]) || "",
        created_by: pick(payload, ["created_by", "createdBy"]) || context.operator || "dispatcher",
        approved_flag: 0,
        created_at: nowIso(),
      };
      if (!command.command_type || !command.target_order_no) {
        throw new Error("dispatch command payload is invalid.");
      }

      this.state.dispatchCommands.push(command);
      this.appendAudit({
        entityType: "DISPATCH_COMMAND",
        entityId: commandId,
        action: "CREATE_DISPATCH_COMMAND",
        operator: command.created_by,
        requestId,
        reason: command.reason,
      });

      return {
        request_id: requestId,
        accepted: true,
        message: "Dispatch command accepted.",
        command_id: commandId,
      };
    });
  }

  approveDispatchCommand(commandId, payload, context = {}) {
    const requestId = pick(payload, ["request_id", "requestId"]) || context.requestId;
    return this.runIdempotent(requestId, `APPROVE_DISPATCH#${commandId}`, () => {
      const command = this.state.dispatchCommands.find((item) => item.command_id === commandId);
      if (!command) {
        throw new Error(`Dispatch command ${commandId} not found.`);
      }

      const decision = pick(payload, ["decision"]);
      if (decision !== "APPROVED" && decision !== "REJECTED") {
        throw new Error("decision must be APPROVED or REJECTED.");
      }

      const approval = {
        approval_id: `APP-${String(this.state.dispatchApprovals.length + 1).padStart(5, "0")}`,
        command_id: commandId,
        approver: pick(payload, ["approver"]) || context.operator || "approver",
        decision,
        decision_reason: pick(payload, ["decision_reason", "decisionReason"]) || null,
        decision_time: pick(payload, ["decision_time", "decisionTime"]) || nowIso(),
        request_id: requestId,
      };
      this.state.dispatchApprovals.push(approval);
      if (decision === "APPROVED") {
        command.approved_flag = 1;
        this.applyDispatchCommand(command);
      }

      this.appendAudit({
        entityType: "DISPATCH_COMMAND",
        entityId: commandId,
        action: decision === "APPROVED" ? "APPROVE_DISPATCH_COMMAND" : "REJECT_DISPATCH_COMMAND",
        operator: approval.approver,
        requestId,
        reason: approval.decision_reason || undefined,
      });

      return {
        request_id: requestId,
        success: true,
        message: `Dispatch command ${decision.toLowerCase()}.`,
      };
    });
  }

  listDispatchCommands(filters = {}) {
    let items = [...this.state.dispatchCommands];
    if (filters.command_type) {
      items = items.filter((item) => item.command_type === filters.command_type);
    }
    return deepClone(items);
  }

  listSalesOrderLines() {
    const now = nowIso();
    const rows = [];
    for (const order of this.state.orders) {
      for (let i = 0; i < order.items.length; i += 1) {
        const item = order.items[i];
        rows.push({
          sales_order_no: `SO-${order.orderNo}`,
          line_no: String(i + 1),
          product_code: item.productCode,
          order_qty: item.qty,
          order_date: toDateTime(this.state.startDate, "D"),
          expected_due_date: toDateTime(order.dueDate || this.state.startDate, "D"),
          requested_ship_date: toDateTime(order.dueDate || this.state.startDate, "N"),
          urgent_flag: order.urgent ? 1 : 0,
          order_status: order.status,
          last_update_time: now,
        });
      }
    }
    return rows;
  }

  listPlanOrders() {
    const now = nowIso();
    return this.state.orders.map((order) => ({
      plan_order_no: `PO-${order.orderNo}`,
      source_sales_order_no: `SO-${order.orderNo}`,
      source_line_no: "1",
      release_type: "PRODUCTION",
      release_status: "RELEASED",
      release_time: now,
      last_update_time: now,
    }));
  }

  listProductionOrders() {
    const now = nowIso();
    return this.state.orders.map((order) => ({
      production_order_no: order.orderNo,
      source_sales_order_no: `SO-${order.orderNo}`,
      source_line_no: "1",
      source_plan_order_no: `PO-${order.orderNo}`,
      material_list_no: `ML-${order.orderNo}`,
      product_code: (order.items[0] && order.items[0].productCode) || "UNKNOWN",
      plan_qty: order.items.reduce((sum, item) => sum + item.qty, 0),
      production_status: order.status,
      last_update_time: now,
    }));
  }

  listScheduleControls() {
    const now = nowIso();
    return this.state.orders.map((order) => ({
      order_no: order.orderNo,
      order_type: order.orderType,
      review_passed_flag: 1,
      frozen_flag: order.frozen ? 1 : 0,
      schedulable_flag: order.status === "CLOSED" ? 0 : 1,
      close_flag: order.status === "CLOSED" ? 1 : 0,
      promised_due_date: toDateTime(order.dueDate || this.state.startDate, "D"),
      last_update_time: now,
    }));
  }

  listMrpLinks() {
    const now = nowIso();
    return this.state.orders.map((order) => ({
      order_no: order.orderNo,
      order_type: order.orderType,
      mrp_run_id: `MRP-${order.orderNo}`,
      run_time: now,
      last_update_time: now,
    }));
  }

  listDeliveryProgress() {
    const now = nowIso();
    return this.state.orders.map((order) => ({
      order_no: order.orderNo,
      order_type: order.orderType,
      warehoused_qty: order.items.reduce((sum, item) => sum + item.completedQty, 0),
      shipped_qty: 0,
      delivery_status: "IN_PROGRESS",
      last_update_time: now,
    }));
  }

  listMaterialAvailability() {
    const now = nowIso();
    return this.state.materialAvailability.map((row) => ({
      material_code: row.productCode,
      order_no: null,
      process_code: row.processCode,
      available_qty: row.availableQty,
      available_time: toDateTime(row.date, row.shiftCode),
      ready_flag: row.availableQty > 0 ? 1 : 0,
      last_update_time: now,
    }));
  }

  listEquipments() {
    const now = nowIso();
    const codes = new Set();
    const rows = [];
    for (const row of this.state.machinePools) {
      const count = Math.max(1, Number(row.availableMachines || 0));
      for (let i = 1; i <= count; i += 1) {
        const code = `${row.processCode}-EQ-${i}`;
        if (codes.has(code)) {
          continue;
        }
        codes.add(code);
        rows.push({
          equipment_code: code,
          line_code: "LINE-A",
          workshop_code: "WS-A",
          status: "AVAILABLE",
          capacity_per_shift: 1,
          last_update_time: now,
        });
      }
    }
    return rows;
  }

  listProcessRoutes() {
    const rows = [];
    const now = nowIso();
    for (const [productCode, steps] of Object.entries(this.state.processRoutes || {})) {
      for (let i = 0; i < steps.length; i += 1) {
        const process = this.state.processes.find((p) => p.processCode === steps[i].processCode) || {};
        rows.push({
          route_no: `ROUTE-${productCode}`,
          product_code: productCode,
          process_code: steps[i].processCode,
          sequence_no: i + 1,
          dependency_type: steps[i].dependencyType || "FS",
          capacity_per_shift: process.capacityPerShift,
          required_manpower_per_group: process.requiredWorkers,
          required_equipment_count: process.requiredMachines,
          enabled_flag: 1,
          last_update_time: now,
        });
      }
    }
    return rows;
  }

  listEquipmentProcessCapabilities() {
    const now = nowIso();
    return this.state.processes.map((process) => ({
      equipment_code: `${process.processCode}-EQ-1`,
      process_code: process.processCode,
      enabled_flag: 1,
      capacity_factor: 1,
      last_update_time: now,
    }));
  }

  listEmployeeSkills() {
    const now = nowIso();
    return this.state.processes.map((process) => ({
      employee_id: `${process.processCode}-EMP-1`,
      process_code: process.processCode,
      skill_level: "L2",
      enabled_flag: 1,
      last_update_time: now,
    }));
  }

  listShiftCalendar() {
    return this.state.shiftCalendar.map((row) => ({
      calendar_date: row.date,
      shift_code: row.shiftCode,
      shift_start_time: toDateTime(row.date, row.shiftCode),
      shift_end_time: toShiftTimeWindow(row.date, row.shiftCode).plan_finish_time,
      open_flag: row.open === false ? 0 : 1,
      workshop_code: "WS-A",
      last_update_time: nowIso(),
    }));
  }

  findOrder(orderNo) {
    const found = this.state.orders.find((order) => order.orderNo === orderNo);
    if (!found) {
      throw new Error(`Order ${orderNo} not found.`);
    }
    return found;
  }

  maybeTriggerProgressGapReplan(reporting, requestId) {
    if (this.state.schedules.length === 0) {
      return null;
    }
    const latest = this.state.schedules[this.state.schedules.length - 1];
    const plannedQty = (latest.allocations || [])
      .filter(
        (row) =>
          row.orderNo === reporting.orderNo &&
          row.productCode === reporting.productCode &&
          row.processCode === reporting.processCode
      )
      .reduce((sum, row) => sum + Number(row.scheduledQty || 0), 0);

    if (plannedQty <= 0) {
      return null;
    }
    const reportedQty = this.state.reportings
      .filter(
        (row) =>
          row.orderNo === reporting.orderNo &&
          row.productCode === reporting.productCode &&
          row.processCode === reporting.processCode
      )
      .reduce((sum, row) => sum + Number(row.reportQty || 0), 0);

    const deviationRate = (Math.abs(plannedQty - reportedQty) / plannedQty) * 100;
    if (deviationRate <= 10) {
      return null;
    }

    const syntheticRequestId = `${requestId || "system"}:progress-gap:${this.state.replanJobs.length + 1}`;
    const job = this.triggerReplanJob(
      {
        request_id: syntheticRequestId,
        trigger_type: "PROGRESS_GAP",
        scope_type: "LOCAL",
        base_version_no: latest.versionNo,
        reason: "Auto-trigger by reporting deviation > 10%",
      },
      {
        operator: "system",
        requestId: syntheticRequestId,
      }
    );

    const alert = this.createAlert({
      alert_type: "PROGRESS_GAP",
      severity: "WARN",
      order_no: reporting.orderNo,
      order_type: "production",
      process_code: reporting.processCode,
      version_no: latest.versionNo,
      trigger_value: round2(deviationRate),
      threshold_value: 10,
      status: "OPEN",
    });

    return {
      job_no: job.job_no,
      alert_id: alert.alert_id,
    };
  }

  createAlert(payload) {
    const alert = {
      alert_id: `ALT-${String(this.state.alerts.length + 1).padStart(5, "0")}`,
      alert_type: payload.alert_type,
      severity: payload.severity || "INFO",
      order_no: payload.order_no || null,
      order_type: payload.order_type || null,
      process_code: payload.process_code || null,
      version_no: payload.version_no || null,
      trigger_value: payload.trigger_value || null,
      threshold_value: payload.threshold_value || null,
      status: payload.status || "OPEN",
      created_at: nowIso(),
      ack_by: null,
      ack_time: null,
      close_by: null,
      close_time: null,
    };
    this.state.alerts.push(alert);
    return alert;
  }

  updateAlertState(alertId, payload, context, nextStatus) {
    const requestId = pick(payload, ["request_id", "requestId"]) || context.requestId;
    return this.runIdempotent(requestId, `${nextStatus}_ALERT#${alertId}`, () => {
      const alert = this.state.alerts.find((item) => item.alert_id === alertId);
      if (!alert) {
        throw new Error(`Alert ${alertId} not found.`);
      }
      if (nextStatus === "ACKED") {
        alert.status = "ACKED";
        alert.ack_by = payload.operator || context.operator || "operator";
        alert.ack_time = nowIso();
      }
      if (nextStatus === "CLOSED") {
        alert.status = "CLOSED";
        alert.close_by = payload.operator || context.operator || "operator";
        alert.close_time = nowIso();
      }
      this.appendAudit({
        entityType: "ALERT",
        entityId: alertId,
        action: nextStatus === "ACKED" ? "ACK_ALERT" : "CLOSE_ALERT",
        operator: payload.operator || context.operator || "operator",
        requestId,
        reason: payload.reason,
      });
      return {
        request_id: requestId,
        success: true,
        message: `Alert ${alertId} set to ${nextStatus}.`,
      };
    });
  }

  applyDispatchCommand(command) {
    const order = this.state.orders.find((item) => item.orderNo === command.target_order_no);
    if (!order) {
      return;
    }
    if (command.command_type === "LOCK") {
      order.lockFlag = true;
      return;
    }
    if (command.command_type === "UNLOCK") {
      order.lockFlag = false;
      return;
    }
    if (command.command_type === "FREEZE") {
      order.frozen = true;
      return;
    }
    if (command.command_type === "UNFREEZE") {
      order.frozen = false;
      return;
    }
    if (command.command_type === "PRIORITY") {
      order.urgent = true;
    }
  }

  appendAudit(record) {
    this.state.auditLogs.push({
      entity_type: record.entityType,
      entity_id: record.entityId,
      action: record.action,
      operator: record.operator || "system",
      request_id: record.requestId || null,
      operate_time: nowIso(),
      reason: record.reason || null,
    });
  }

  runIdempotent(requestId, action, executor) {
    if (!requestId) {
      return executor();
    }
    const key = `${action}#${requestId}`;
    if (this.state.idempotencyLedger.has(key)) {
      return deepClone(this.state.idempotencyLedger.get(key));
    }
    const result = executor();
    this.state.idempotencyLedger.set(key, deepClone(result));
    return deepClone(result);
  }
}

function normalizeOrder(order) {
  return {
    orderNo: pick(order, ["orderNo", "order_no"]),
    orderType: pick(order, ["orderType", "order_type"]) || "production",
    dueDate: pick(order, ["dueDate", "due_date"]),
    urgent:
      Object.prototype.hasOwnProperty.call(order, "urgent_flag")
        ? Number(order.urgent_flag) === 1
        : !!order.urgent,
    frozen:
      Object.prototype.hasOwnProperty.call(order, "frozen_flag")
        ? Number(order.frozen_flag) === 1
        : !!order.frozen,
    lockFlag:
      Object.prototype.hasOwnProperty.call(order, "lock_flag")
        ? Number(order.lock_flag) === 1
        : !!order.lockFlag,
    status: pick(order, ["status", "order_status"]) || "OPEN",
    items: (order.items || []).map((item) => ({
      productCode: pick(item, ["productCode", "product_code"]),
      qty: Number(item.qty),
      completedQty: Number(pick(item, ["completedQty", "completed_qty"]) || 0),
    })),
  };
}

function deepClone(value) {
  return JSON.parse(JSON.stringify(value));
}

function nowIso() {
  return new Date().toISOString();
}

function pick(obj, keys, fallback = undefined) {
  if (!obj || typeof obj !== "object") {
    return fallback;
  }
  for (const key of keys) {
    if (Object.prototype.hasOwnProperty.call(obj, key) && obj[key] !== undefined) {
      return obj[key];
    }
  }
  return fallback;
}

function toOrderPoolItem(order) {
  const totalQty = order.items.reduce((sum, item) => sum + Number(item.qty || 0), 0);
  return {
    order_no: order.orderNo,
    order_type: order.orderType,
    line_no: "1",
    product_code: (order.items[0] && order.items[0].productCode) || "UNKNOWN",
    order_qty: totalQty,
    expected_due_date: toDateTime(order.dueDate || "2026-01-01", "D"),
    promised_due_date: toDateTime(order.dueDate || "2026-01-01", "D"),
    urgent_flag: order.urgent ? 1 : 0,
    frozen_flag: order.frozen ? 1 : 0,
    status: order.status,
  };
}

function countCapacityBlocked(unscheduled = []) {
  return (unscheduled || []).filter((row) => (row.reasons || []).includes("CAPACITY_LIMIT")).length;
}

function toDateTime(date, shiftCode) {
  if (!date) {
    return nowIso();
  }
  const startHour = shiftCode === "N" || shiftCode === "NIGHT" ? 20 : 8;
  const d = new Date(`${date}T00:00:00.000Z`);
  d.setUTCHours(startHour, 0, 0, 0);
  return d.toISOString();
}

function toShiftTimeWindow(date, shiftCode) {
  const start = new Date(toDateTime(date, shiftCode));
  const end = new Date(start.getTime() + 12 * 60 * 60 * 1000);
  return {
    plan_start_time: start.toISOString(),
    plan_finish_time: end.toISOString(),
  };
}

function deriveShiftCodeByTime(isoTime) {
  const hour = new Date(isoTime).getUTCHours();
  return hour >= 20 || hour < 8 ? "NIGHT" : "DAY";
}

function round2(value) {
  return Math.round(Number(value || 0) * 100) / 100;
}

module.exports = {
  MemoryStore,
};
