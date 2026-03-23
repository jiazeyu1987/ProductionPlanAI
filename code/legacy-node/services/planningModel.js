const DEFAULT_SHIFT_CODES = ["D", "N"];

function toNumber(value, fallback = 0) {
  const n = Number(value);
  return Number.isFinite(n) ? n : fallback;
}

function buildShiftSlots({ startDate, horizonDays = 7, shiftsPerDay = 1, shiftCalendar = [] }) {
  if (!startDate) {
    throw new Error("startDate is required.");
  }

  const slots = [];
  const calendarIndex = new Map();
  for (const row of shiftCalendar) {
    const key = `${row.date}#${row.shiftCode}`;
    calendarIndex.set(key, row);
  }

  for (let i = 0; i < horizonDays; i += 1) {
    const date = addDays(startDate, i);
    for (let shiftIdx = 0; shiftIdx < shiftsPerDay; shiftIdx += 1) {
      const shiftCode = DEFAULT_SHIFT_CODES[shiftIdx] || `S${shiftIdx + 1}`;
      const key = `${date}#${shiftCode}`;
      const row = calendarIndex.get(key);
      const isOpen = row ? row.open !== false : true;
      if (!isOpen) {
        continue;
      }
      slots.push({
        index: slots.length,
        date,
        shiftCode,
        shiftId: key,
      });
    }
  }

  if (slots.length === 0) {
    throw new Error("No open shifts found in planning horizon.");
  }

  return slots;
}

function buildResourcePoolIndex(resourceRows = [], fieldName, defaultValue = 0) {
  const index = new Map();
  for (const row of resourceRows) {
    const key = `${row.date}#${row.shiftCode}#${row.processCode}`;
    index.set(key, toNumber(row[fieldName], defaultValue));
  }
  return index;
}

function buildMaterialPoolIndex(materialRows = []) {
  const index = new Map();
  for (const row of materialRows) {
    const key = `${row.date}#${row.shiftCode}#${row.productCode}#${row.processCode}`;
    index.set(key, toNumber(row.availableQty, 0));
  }
  return index;
}

function getPoolValue(poolIndex, shiftId, processCode) {
  return toNumber(poolIndex.get(`${shiftId}#${processCode}`), 0);
}

function getMaterialValue(materialIndex, shiftId, productCode, processCode) {
  return toNumber(materialIndex.get(`${shiftId}#${productCode}#${processCode}`), 0);
}

function addDays(dateString, offsetDays) {
  const [year, month, day] = dateString.split("-").map((item) => Number(item));
  const date = new Date(Date.UTC(year, month - 1, day + offsetDays));
  return date.toISOString().slice(0, 10);
}

module.exports = {
  buildShiftSlots,
  buildResourcePoolIndex,
  buildMaterialPoolIndex,
  getPoolValue,
  getMaterialValue,
  toNumber,
};
