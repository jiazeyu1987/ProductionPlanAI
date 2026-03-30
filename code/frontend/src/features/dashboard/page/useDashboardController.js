import { useEffect, useMemo, useState } from "react";
import { fetchDashboardSnapshot } from "..";
import {
  buildShiftEquipmentRows,
  summarizeProcessLoad,
  summarizeShiftDemand,
  summarizeShiftLoad,
  todayDateKey
} from "../dashboardFormatters";

export function useDashboardController() {
  const [stats, setStats] = useState({
    orderPool: 0,
    openAlerts: 0,
    versions: 0,
    outboxFailed: 0
  });
  const [mustHandle, setMustHandle] = useState([]);
  const [selectedDateKey, setSelectedDateKey] = useState(todayDateKey());
  const [shiftProcessLoads, setShiftProcessLoads] = useState([]);
  const [equipments, setEquipments] = useState([]);
  const [selectedShiftForEquipments, setSelectedShiftForEquipments] = useState("");
  const [todayProcessMeta, setTodayProcessMeta] = useState({
    versionNo: "",
    versionStatus: ""
  });

  const selectedShiftRows = useMemo(
    () => summarizeShiftLoad(shiftProcessLoads, selectedDateKey),
    [shiftProcessLoads, selectedDateKey]
  );
  const todayProcessRows = useMemo(
    () => summarizeProcessLoad(shiftProcessLoads, selectedDateKey),
    [shiftProcessLoads, selectedDateKey]
  );
  const shiftDemandRows = useMemo(() => summarizeShiftDemand(selectedShiftRows), [selectedShiftRows]);
  const selectedShiftDemand = useMemo(
    () => shiftDemandRows.find((row) => row.shift_code === selectedShiftForEquipments) || null,
    [shiftDemandRows, selectedShiftForEquipments]
  );
  const shiftEquipmentRows = useMemo(
    () => buildShiftEquipmentRows(selectedShiftForEquipments, selectedShiftRows, equipments),
    [selectedShiftForEquipments, selectedShiftRows, equipments]
  );

  useEffect(() => {
    if (shiftDemandRows.length === 0) {
      setSelectedShiftForEquipments("");
      return;
    }
    const exists = shiftDemandRows.some((row) => row.shift_code === selectedShiftForEquipments);
    if (!exists) {
      setSelectedShiftForEquipments(shiftDemandRows[0].shift_code);
    }
  }, [shiftDemandRows, selectedShiftForEquipments]);

  useEffect(() => {
    let active = true;
    async function load() {
      const snapshot = await fetchDashboardSnapshot();
      if (!active) {
        return;
      }
      setStats(snapshot.stats);
      setMustHandle(snapshot.mustHandle);
      setShiftProcessLoads(snapshot.shiftProcessLoads);
      setEquipments(snapshot.equipments);
      setTodayProcessMeta(snapshot.todayProcessMeta);
    }
    load().catch(() => {});
    const timer = setInterval(() => load().catch(() => {}), 60000);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, []);

  return {
    stats,
    mustHandle,
    selectedDateKey,
    setSelectedDateKey,
    shiftProcessLoads,
    equipments,
    selectedShiftForEquipments,
    setSelectedShiftForEquipments,
    todayProcessMeta,

    selectedShiftRows,
    todayProcessRows,
    shiftDemandRows,
    selectedShiftDemand,
    shiftEquipmentRows
  };
}

