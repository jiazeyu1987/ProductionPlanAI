const test = require("node:test");
const assert = require("node:assert/strict");
const http = require("node:http");

const { createApp } = require("../src/app");

test("API: generate and validate schedule", async () => {
  const { app } = createApp();
  const server = http.createServer(app);

  await listen(server);
  const baseUrl = `http://127.0.0.1:${server.address().port}`;

  try {
    const health = await fetchJson(`${baseUrl}/api/health`);
    assert.equal(health.status, "ok");

    const generated = await fetchJson(`${baseUrl}/api/schedules/generate`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-request-id": "api-test-1",
      },
      body: JSON.stringify({ request_id: "api-test-1" }),
    });

    assert.ok(generated.versionNo);
    assert.ok(Array.isArray(generated.allocations));
    assert.ok(generated.allocations.length > 0);

    const validation = await fetchJson(`${baseUrl}/api/schedules/latest/validation`);
    assert.equal(validation.passed, true);
    assert.equal(validation.violationCount, 0);
  } finally {
    await close(server);
  }
});

test("API: order quantity decrease should affect next scheduling", async () => {
  const { app } = createApp();
  const server = http.createServer(app);

  await listen(server);
  const baseUrl = `http://127.0.0.1:${server.address().port}`;

  try {
    await fetchJson(`${baseUrl}/api/orders/MO-CATH-001`, {
      method: "PATCH",
      headers: {
        "content-type": "application/json",
        "x-request-id": "api-test-2-patch",
      },
      body: JSON.stringify({
        request_id: "api-test-2-patch",
        items: [{ productCode: "PROD_CATH", qty: 200 }],
      }),
    });

    const generated = await fetchJson(`${baseUrl}/api/schedules/generate`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-request-id": "api-test-2",
      },
      body: JSON.stringify({ request_id: "api-test-2" }),
    });

    const cathStep0 = generated.tasks.find(
      (task) => task.orderNo === "MO-CATH-001" && task.stepIndex === 0
    );

    assert.ok(cathStep0);
    assert.equal(cathStep0.targetQty, 200);

    const validation = await fetchJson(`${baseUrl}/api/schedules/latest/validation`);
    assert.equal(validation.passed, true);
  } finally {
    await close(server);
  }
});

test("API: write endpoints should require request_id", async () => {
  const { app } = createApp();
  const server = http.createServer(app);

  await listen(server);
  const baseUrl = `http://127.0.0.1:${server.address().port}`;

  try {
    const response = await fetch(`${baseUrl}/api/orders/MO-CATH-001`, {
      method: "PATCH",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ urgent: true }),
    });

    const body = await response.json();
    assert.equal(response.status, 400);
    assert.equal(body.error.code, "REQUEST_ID_REQUIRED");
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
