const { MemoryStore } = require("../src/services/memoryStore");

function main() {
  const store = new MemoryStore();
  const schedule = store.generateSchedule({ requestId: "cli-validate" });
  const validation = store.validateSchedule(schedule.versionNo);

  const summary = {
    versionNo: schedule.versionNo,
    generatedAt: schedule.generatedAt,
    scheduledQty: schedule.metrics.scheduledQty,
    targetQty: schedule.metrics.targetQty,
    completionRate: schedule.metrics.scheduleCompletionRate,
    violationCount: validation.violationCount,
    passed: validation.passed,
  };

  // eslint-disable-next-line no-console
  console.log(JSON.stringify(summary, null, 2));

  if (!validation.passed) {
    // eslint-disable-next-line no-console
    console.error(JSON.stringify(validation.violations, null, 2));
    process.exitCode = 1;
  }
}

main();
