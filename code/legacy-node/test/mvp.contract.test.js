const test = require("node:test");
const assert = require("node:assert/strict");
const http = require("node:http");

const { createApp } = require("../src/app");

test("MVP contract: external ERP route should work with bearer auth", async () => {
  const { app } = createApp();
  const server = http.createServer(app);
  await listen(server);
  const baseUrl = `http://127.0.0.1:${server.address().port}`;

  try {
    const response = await fetch(`${baseUrl}/v1/erp/sales-order-lines?page=1&page_size=10`, {
      headers: {
        authorization: "Bearer test-token",
        "x-request-id": "contract-ext-1",
      },
    });
    const body = await response.json();
    assert.equal(response.status, 200);
    assert.equal(body.request_id, "contract-ext-1");
    assert.ok(Array.isArray(body.items));
    assert.ok(body.items.length > 0);
  } finally {
    await close(server);
  }
});

test("MVP contract: contract routes should require bearer auth", async () => {
  const { app } = createApp();
  const server = http.createServer(app);
  await listen(server);
  const baseUrl = `http://127.0.0.1:${server.address().port}`;

  try {
    const response = await fetch(`${baseUrl}/v1/erp/sales-order-lines`);
    const body = await response.json();
    assert.equal(response.status, 401);
    assert.equal(body.code, "UNAUTHORIZED");
  } finally {
    await close(server);
  }
});

test("MVP contract: auto replan should exclude locked orders", async () => {
  const { app } = createApp();
  const server = http.createServer(app);
  await listen(server);
  const baseUrl = `http://127.0.0.1:${server.address().port}`;

  try {
    await fetchJson(`${baseUrl}/api/orders/MO-CATH-001`, {
      method: "PATCH",
      headers: {
        "content-type": "application/json",
        "x-request-id": "contract-lock-1",
      },
      body: JSON.stringify({
        request_id: "contract-lock-1",
        lockFlag: true,
      }),
    });

    const base = await fetchJson(`${baseUrl}/api/schedules/generate`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-request-id": "contract-lock-2",
      },
      body: JSON.stringify({ request_id: "contract-lock-2" }),
    });

    await fetchJson(`${baseUrl}/internal/v1/internal/replan-jobs`, {
      method: "POST",
      headers: {
        authorization: "Bearer test-token",
        "content-type": "application/json",
      },
      body: JSON.stringify({
        request_id: "contract-lock-3",
        trigger_type: "PROGRESS_GAP",
        scope_type: "LOCAL",
        base_version_no: base.versionNo,
        reason: "test auto replan",
      }),
    });

    const latest = await fetchJson(`${baseUrl}/api/schedules/latest`);
    assert.equal(latest.metadata.autoReplan, true);
    assert.ok(Array.isArray(latest.metadata.excludedLockedOrders));
    assert.ok(latest.metadata.excludedLockedOrders.includes("MO-CATH-001"));
  } finally {
    await close(server);
  }
});

test("MVP contract: dispatch approval should change order lock", async () => {
  const { app } = createApp();
  const server = http.createServer(app);
  await listen(server);
  const baseUrl = `http://127.0.0.1:${server.address().port}`;

  try {
    const created = await fetchJson(`${baseUrl}/internal/v1/internal/dispatch-commands`, {
      method: "POST",
      headers: {
        authorization: "Bearer test-token",
        "content-type": "application/json",
      },
      body: JSON.stringify({
        request_id: "dispatch-1",
        command_type: "LOCK",
        target_order_no: "MO-BALLOON-001",
        target_order_type: "production",
        effective_time: new Date().toISOString(),
        reason: "lock for manual plan",
        created_by: "planner",
      }),
    });

    await fetchJson(`${baseUrl}/internal/v1/internal/dispatch-commands/${created.command_id}/approvals`, {
      method: "POST",
      headers: {
        authorization: "Bearer test-token",
        "content-type": "application/json",
      },
      body: JSON.stringify({
        request_id: "dispatch-2",
        approver: "manager",
        decision: "APPROVED",
        decision_time: new Date().toISOString(),
      }),
    });

    const orders = await fetchJson(`${baseUrl}/api/orders`);
    const order = orders.items.find((item) => item.orderNo === "MO-BALLOON-001");
    assert.equal(order.lockFlag, true);
  } finally {
    await close(server);
  }
});

function listen(server) {
  return new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", resolve);
  });
}

function close(server) {
  return new Promise((resolve) => {
    server.close(() => resolve());
  });
}

async function fetchJson(url, options) {
  const response = await fetch(url, options);
  const body = await response.json();
  if (!response.ok) {
    throw new Error(JSON.stringify(body));
  }
  return body;
}

