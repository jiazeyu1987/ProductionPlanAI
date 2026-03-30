import { useEffect, useState } from "react";
import { normalizeLiteScenario } from "../../utils/liteSchedulerEngine";
import {
  loadLiteScenario,
  saveLiteScenario,
} from "./liteSchedulerPageControllerStorage";

export function useLiteSchedulerScenarioState() {
  const [scenario, setScenario] = useState(loadLiteScenario);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    saveLiteScenario(scenario);
  }, [scenario]);

  function applyScenario(mutator, successMessage = "") {
    setError("");
    setMessage("");
    try {
      setScenario((prev) =>
        normalizeLiteScenario(mutator(normalizeLiteScenario(prev))),
      );
      if (successMessage) {
        setMessage(successMessage);
      }
    } catch (e) {
      setError(e.message || "操作失败");
    }
  }

  return {
    scenario,
    setScenario,
    message,
    setMessage,
    error,
    setError,
    applyScenario,
  };
}

