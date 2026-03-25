package com.autoproduction.mvp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class MvpApiTest {
  private static final DataFormatter DATA_FORMATTER = new DataFormatter();

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @BeforeEach
  void reset() throws Exception {
    mockMvc.perform(post("/api/reset")).andExpect(status().isOk());
  }

  @Test
  void healthEndpointWorks() throws Exception {
    mockMvc.perform(get("/api/health"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("ok"));
  }

  @Test
  void contractWriteRequiresAuthAndRequestId() throws Exception {
    mockMvc.perform(post("/v1/erp/schedule-results").contentType(MediaType.APPLICATION_JSON).content("{}"))
      .andExpect(status().isUnauthorized());

    mockMvc.perform(
        post("/v1/erp/schedule-results")
          .header("Authorization", "Bearer test")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{}")
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("REQUEST_ID_REQUIRED"));
  }

  @Test
  void schedulingAndPublishFlowWorks() throws Exception {
    String requestId = "req-test-generate";
    mockMvc.perform(
        post("/api/schedules/generate")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of("request_id", requestId)))
      )
      .andExpect(status().isCreated())
      .andExpect(header().string("x-request-id", requestId))
      .andExpect(jsonPath("$.version_no").value("V001"));

    mockMvc.perform(
        post("/internal/v1/internal/schedule-versions/V001/publish")
          .header("Authorization", "Bearer test")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of(
            "request_id", "req-test-publish",
            "operator", "publisher01",
            "reason", "test publish"
          )))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true))
      .andExpect(jsonPath("$.version_no").value("V001"));
  }

  @Test
  void scheduleAlgorithmExplainEndpointWorks() throws Exception {
    mockMvc.perform(
        post("/api/schedules/generate")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of("request_id", "req-test-algo-generate")))
      )
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.version_no").value("V001"));

    mockMvc.perform(
        get("/internal/v1/internal/schedule-versions/V001/algorithm")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.version_no").value("V001"))
      .andExpect(jsonPath("$.strategy_code").isString())
      .andExpect(jsonPath("$.strategy_name_cn").isString())
      .andExpect(jsonPath("$.summary.task_count").isNumber())
      .andExpect(jsonPath("$.summary.schedule_generate_duration_ms").isNumber())
      .andExpect(jsonPath("$.summary.schedule_generate_phase_duration_ms").isMap())
      .andExpect(jsonPath("$.summary.unscheduled_reason_distribution").isMap())
      .andExpect(jsonPath("$.summary.locked_or_frozen_impact_count").isNumber())
      .andExpect(jsonPath("$.summary.publish_rollback_count").isNumber())
      .andExpect(jsonPath("$.summary.replan_failure_rate").isNumber())
      .andExpect(jsonPath("$.summary.api_error_rate").isNumber())
      .andExpect(jsonPath("$.logic").isArray())
      .andExpect(jsonPath("$.process_summary").isArray())
      .andExpect(jsonPath("$.process_summary[0].max_allocation_qty").isNumber())
      .andExpect(jsonPath("$.process_summary[0].max_allocation_order_no").isString())
      .andExpect(jsonPath("$.process_summary[0].max_allocation_date").isString())
      .andExpect(jsonPath("$.process_summary[0].max_allocation_shift_code").isString())
      .andExpect(jsonPath("$.process_summary[0].max_allocation_explain_cn").isString())
      .andExpect(jsonPath("$.process_summary[0].target_qty").isNumber())
      .andExpect(jsonPath("$.process_summary[0].unscheduled_qty").isNumber())
      .andExpect(jsonPath("$.process_summary[0].schedule_rate").isNumber())
      .andExpect(jsonPath("$.unscheduled_reason_summary").isArray());
  }

  @Test
  void generateScheduleSupportsStrategyOption() throws Exception {
    mockMvc.perform(
        post("/api/schedules/generate")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of(
            "request_id", "req-test-strategy-generate",
            "strategy_code", "MAX_CAPACITY_FIRST"
          )))
      )
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.version_no").value("V001"))
      .andExpect(jsonPath("$.metadata.schedule_strategy_code").value("MAX_CAPACITY_FIRST"));

    mockMvc.perform(
        get("/internal/v1/internal/schedule-versions/V001/algorithm")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.strategy_code").value("MAX_CAPACITY_FIRST"));
  }

  @Test
  void scheduleTasksEndpointExposesDependencyAndBlockReasonFields() throws Exception {
    mockMvc.perform(
        post("/api/schedules/generate")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of("request_id", "req-test-task-fields-generate")))
      )
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.version_no").value("V001"));

    MvcResult tasksResult = mockMvc.perform(
        get("/internal/v1/internal/schedule-versions/V001/tasks")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andReturn();

    Map<String, Object> tasksBody = objectMapper.readValue(tasksResult.getResponse().getContentAsString(), Map.class);
    List<Map<String, Object>> items = (List<Map<String, Object>>) tasksBody.get("items");
    assertTrue(!items.isEmpty(), "schedule tasks should not be empty");
    assertTrue(items.stream().allMatch(row -> row.containsKey("dependency_status")));
    assertTrue(items.stream().allMatch(row -> row.containsKey("last_block_reason")));
    assertTrue(items.stream().allMatch(row -> row.containsKey("task_status")));
  }

  @Test
  void scheduleDetailUnscheduledContainsStructuredReasonFields() throws Exception {
    mockMvc.perform(
        patch("/api/orders/MO-STENT-001")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of(
            "request_id", "req-test-unscheduled-structured-patch",
            "frozen_flag", 1
          )))
      )
      .andExpect(status().isOk());

    mockMvc.perform(
        post("/api/schedules/generate")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of("request_id", "req-test-unscheduled-structured-generate")))
      )
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.version_no").value("V001"));

    MvcResult latestResult = mockMvc.perform(get("/api/schedules/latest"))
      .andExpect(status().isOk())
      .andReturn();

    Map<String, Object> body = objectMapper.readValue(latestResult.getResponse().getContentAsString(), Map.class);
    List<Map<String, Object>> unscheduled = (List<Map<String, Object>>) body.get("unscheduled");
    assertTrue(!unscheduled.isEmpty(), "unscheduled list should not be empty");
    assertTrue(unscheduled.stream().allMatch(row -> row.containsKey("reason_code")));
    assertTrue(unscheduled.stream().allMatch(row -> row.containsKey("dependency_status")));
    assertTrue(unscheduled.stream().allMatch(row -> row.containsKey("task_status")));
    assertTrue(unscheduled.stream().allMatch(row -> row.containsKey("last_block_reason")));
    assertTrue(unscheduled.stream().allMatch(row -> row.containsKey("reasons")));
    assertTrue(unscheduled.stream().noneMatch(row -> "CAPACITY_LIMIT".equals(String.valueOf(row.get("reason_code")))));
  }

  @Test
  void dispatchApprovalChangesOrderLockStatus() throws Exception {
    mockMvc.perform(
        post("/internal/v1/internal/dispatch-commands")
          .header("Authorization", "Bearer test")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of(
            "request_id", "req-test-cmd",
            "command_type", "LOCK",
            "target_order_no", "MO-CATH-001",
            "target_order_type", "production",
            "effective_time", "2026-03-22T08:00:00Z",
            "reason", "test",
            "created_by", "dispatcher01"
          )))
      )
      .andExpect(status().isAccepted())
      .andExpect(jsonPath("$.command_id").isNotEmpty());

    mockMvc.perform(
        post("/internal/v1/internal/dispatch-commands/CMD-00001/approvals")
          .header("Authorization", "Bearer test")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of(
            "request_id", "req-test-approve",
            "approver", "manager01",
            "decision", "APPROVED",
            "decision_reason", "ok",
            "decision_time", "2026-03-22T08:01:00Z"
          )))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true));

    mockMvc.perform(get("/api/orders"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items[0].lock_flag").value(1));
  }

  @Test
  void dispatchApprovalCanCancelPriority() throws Exception {
    MvcResult createPriorityResult = mockMvc.perform(
        post("/internal/v1/internal/dispatch-commands")
          .header("Authorization", "Bearer test")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of(
            "request_id", "req-test-priority-create",
            "command_type", "PRIORITY",
            "target_order_no", "MO-BALLOON-001",
            "target_order_type", "production",
            "effective_time", "2026-03-22T08:10:00Z",
            "reason", "test priority",
            "created_by", "dispatcher01"
          )))
      )
      .andExpect(status().isAccepted())
      .andReturn();
    String priorityCommandId = String.valueOf(
      objectMapper.readValue(createPriorityResult.getResponse().getContentAsString(), Map.class).get("command_id")
    );

    mockMvc.perform(
        post("/internal/v1/internal/dispatch-commands/" + priorityCommandId + "/approvals")
          .header("Authorization", "Bearer test")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of(
            "request_id", "req-test-priority-approve",
            "approver", "manager01",
            "decision", "APPROVED",
            "decision_reason", "ok",
            "decision_time", "2026-03-22T08:11:00Z"
          )))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true));

    MvcResult ordersAfterPriority = mockMvc.perform(get("/api/orders"))
      .andExpect(status().isOk())
      .andReturn();
    Map<String, Object> ordersAfterPriorityBody = objectMapper.readValue(
      ordersAfterPriority.getResponse().getContentAsString(),
      Map.class
    );
    List<Map<String, Object>> ordersAfterPriorityItems = (List<Map<String, Object>>) ordersAfterPriorityBody.get("items");
    Map<String, Object> balloonAfterPriority = ordersAfterPriorityItems.stream()
      .filter(item -> "MO-BALLOON-001".equals(String.valueOf(item.get("order_no"))))
      .findFirst()
      .orElseThrow();
    assertEquals(1, ((Number) balloonAfterPriority.get("urgent_flag")).intValue());

    MvcResult createUnpriorityResult = mockMvc.perform(
        post("/internal/v1/internal/dispatch-commands")
          .header("Authorization", "Bearer test")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of(
            "request_id", "req-test-unpriority-create",
            "command_type", "UNPRIORITY",
            "target_order_no", "MO-BALLOON-001",
            "target_order_type", "production",
            "effective_time", "2026-03-22T08:12:00Z",
            "reason", "test unpriority",
            "created_by", "dispatcher01"
          )))
      )
      .andExpect(status().isAccepted())
      .andReturn();
    String unpriorityCommandId = String.valueOf(
      objectMapper.readValue(createUnpriorityResult.getResponse().getContentAsString(), Map.class).get("command_id")
    );

    mockMvc.perform(
        post("/internal/v1/internal/dispatch-commands/" + unpriorityCommandId + "/approvals")
          .header("Authorization", "Bearer test")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of(
            "request_id", "req-test-unpriority-approve",
            "approver", "manager01",
            "decision", "APPROVED",
            "decision_reason", "ok",
            "decision_time", "2026-03-22T08:13:00Z"
          )))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true));

    MvcResult ordersAfterUnpriority = mockMvc.perform(get("/api/orders"))
      .andExpect(status().isOk())
      .andReturn();
    Map<String, Object> ordersAfterUnpriorityBody = objectMapper.readValue(
      ordersAfterUnpriority.getResponse().getContentAsString(),
      Map.class
    );
    List<Map<String, Object>> ordersAfterUnpriorityItems = (List<Map<String, Object>>) ordersAfterUnpriorityBody.get("items");
    Map<String, Object> balloonAfterUnpriority = ordersAfterUnpriorityItems.stream()
      .filter(item -> "MO-BALLOON-001".equals(String.valueOf(item.get("order_no"))))
      .findFirst()
      .orElseThrow();
    assertEquals(0, ((Number) balloonAfterUnpriority.get("urgent_flag")).intValue());
  }

  @Test
  void lockedOrderWithoutBaseVersionShouldExposeLockedPreservedReason() throws Exception {
    mockMvc.perform(
        patch("/api/orders/MO-CATH-001")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of(
            "request_id", "req-test-lock-patch",
            "lock_flag", 1
          )))
      )
      .andExpect(status().isOk());

    mockMvc.perform(
        post("/api/schedules/generate")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of("request_id", "req-test-lock-generate")))
      )
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.version_no").value("V001"));

    mockMvc.perform(
        get("/internal/v1/internal/schedule-versions/V001/algorithm")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.unscheduled_reason_summary[?(@.reason_code=='LOCKED_PRESERVED')].count").isNotEmpty());
  }

  @Test
  void frozenOrderShouldExposeFrozenPolicyReason() throws Exception {
    mockMvc.perform(
        patch("/api/orders/MO-STENT-001")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of(
            "request_id", "req-test-frozen-patch",
            "frozen_flag", 1
          )))
      )
      .andExpect(status().isOk());

    mockMvc.perform(
        post("/api/schedules/generate")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of("request_id", "req-test-frozen-generate")))
      )
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.version_no").value("V001"));

    mockMvc.perform(
        get("/internal/v1/internal/schedule-versions/V001/algorithm")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.unscheduled_reason_summary[?(@.reason_code=='FROZEN_BY_POLICY')].count").isNotEmpty());
  }

  @Test
  void simulationRunRequiresRequestId() throws Exception {
    mockMvc.perform(
        post("/internal/v1/internal/simulation/run")
          .header("Authorization", "Bearer test")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{}")
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("REQUEST_ID_REQUIRED"));
  }

  @Test
  void manualSimulationActionsRequireRequestId() throws Exception {
    mockMvc.perform(
        post("/internal/v1/internal/simulation/manual/add-production-order")
          .header("Authorization", "Bearer test")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{}")
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("REQUEST_ID_REQUIRED"));

    mockMvc.perform(
        post("/internal/v1/internal/simulation/manual/advance-day")
          .header("Authorization", "Bearer test")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{}")
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("REQUEST_ID_REQUIRED"));

    mockMvc.perform(
        post("/internal/v1/internal/simulation/manual/reset")
          .header("Authorization", "Bearer test")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{}")
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("REQUEST_ID_REQUIRED"));
  }

  @Test
  void simulationRunGeneratesOrdersAndVersions() throws Exception {
    mockMvc.perform(
        post("/internal/v1/internal/simulation/run")
          .header("Authorization", "Bearer test")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of(
            "request_id", "req-test-sim-run",
            "days", 2,
            "daily_sales_order_count", 3,
            "scenario", "STABLE",
            "seed", 20260322
          )))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.new_sales_orders").value(6))
      .andExpect(jsonPath("$.new_production_orders").value(6))
      .andExpect(jsonPath("$.generated_versions").value(2));

    mockMvc.perform(
        get("/internal/v1/internal/order-pool")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.total").value(9))
      .andExpect(jsonPath("$.items[0].lock_flag").isNumber())
      .andExpect(jsonPath("$.items[0].completed_qty").isNumber())
      .andExpect(jsonPath("$.items[0].remaining_qty").isNumber())
      .andExpect(jsonPath("$.items[0].progress_rate").isNumber());

    mockMvc.perform(
        get("/internal/v1/internal/simulation/state")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.sales_order_total").value(6))
      .andExpect(jsonPath("$.latest_version_no").isNotEmpty());
  }

  @Test
  void manualSimulationCanAddOrderAdvanceAndReset() throws Exception {
    MvcResult initialStateResult = mockMvc.perform(
        get("/internal/v1/internal/simulation/state")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andReturn();
    Map<String, Object> initialState = objectMapper.readValue(initialStateResult.getResponse().getContentAsString(), Map.class);
    int initialProductionTotal = ((Number) initialState.get("production_order_total")).intValue();
    String initialDate = String.valueOf(initialState.get("current_sim_date"));

    MvcResult addResult = mockMvc.perform(
        post("/internal/v1/internal/simulation/manual/add-production-order")
          .header("Authorization", "Bearer test")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of("request_id", "req-test-manual-add")))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.production_order_no").isNotEmpty())
      .andExpect(jsonPath("$.state.manual_session_active").value(true))
      .andReturn();
    Map<String, Object> addBody = objectMapper.readValue(addResult.getResponse().getContentAsString(), Map.class);
    String newOrderNo = String.valueOf(addBody.get("production_order_no"));

    mockMvc.perform(
        get("/internal/v1/internal/simulation/state")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.production_order_total").value(initialProductionTotal + 1));

    mockMvc.perform(
        get("/internal/v1/internal/order-pool")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items[?(@.order_no=='" + newOrderNo + "')]").isArray());

    mockMvc.perform(
        post("/api/schedules/generate")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of("request_id", "req-test-manual-generate")))
      )
      .andExpect(status().isCreated());

    mockMvc.perform(
        get("/internal/v1/internal/schedule-versions/V001/tasks")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items[?(@.order_no=='" + newOrderNo + "')]").isArray());

    mockMvc.perform(
        post("/internal/v1/internal/simulation/manual/advance-day")
          .header("Authorization", "Bearer test")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of("request_id", "req-test-manual-advance")))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.days").value(1))
      .andExpect(jsonPath("$.generated_versions").value(1))
      .andExpect(jsonPath("$.reporting_count").isNumber())
      .andExpect(jsonPath("$.manual_advance_duration_ms").isNumber())
      .andExpect(jsonPath("$.manual_advance_phase_duration_ms").isMap())
      .andExpect(jsonPath("$.schedule_generate_duration_ms").isNumber())
      .andExpect(jsonPath("$.unscheduled_reason_distribution").isMap())
      .andExpect(jsonPath("$.state.latest_version_no").isNotEmpty());

    mockMvc.perform(
        post("/internal/v1/internal/simulation/manual/reset")
          .header("Authorization", "Bearer test")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of("request_id", "req-test-manual-reset")))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.production_order_total").value(initialProductionTotal))
      .andExpect(jsonPath("$.current_sim_date").value(initialDate))
      .andExpect(jsonPath("$.manual_session_active").value(false));
  }

  @Test
  void patchOrderRequiresRequestId() throws Exception {
    mockMvc.perform(
        patch("/api/orders/MO-CATH-001")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{}")
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.error.code").value("REQUEST_ID_REQUIRED"));
  }

  @Test
  void preflightRequestWorksWithoutAuth() throws Exception {
    mockMvc.perform(
        options("/internal/v1/internal/simulation/state")
          .header("Origin", "http://localhost:5932")
          .header("Access-Control-Request-Method", "GET")
          .header("Access-Control-Request-Headers", "Authorization,Content-Type")
      )
      .andExpect(status().isOk())
      .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5932"));
  }

  @Test
  void legacyApiPreflightWorksWithoutAuth() throws Exception {
    mockMvc.perform(
        options("/api/schedules/generate")
          .header("Origin", "http://localhost:5932")
          .header("Access-Control-Request-Method", "POST")
          .header("Access-Control-Request-Headers", "Content-Type")
      )
      .andExpect(status().isOk())
      .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5932"));
  }

  @Test
  void contractGetIncludesCorsHeader() throws Exception {
    mockMvc.perform(
        get("/internal/v1/internal/simulation/state")
          .header("Origin", "http://localhost:5932")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5932"));
  }

  @Test
  void apiReturnsChineseNameFields() throws Exception {
    mockMvc.perform(
        get("/v1/mes/process-routes")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items[0].route_name_cn").isNotEmpty())
      .andExpect(jsonPath("$.items[0].product_name_cn").isNotEmpty())
      .andExpect(jsonPath("$.items[0].process_name_cn").isNotEmpty());

    mockMvc.perform(
        post("/api/schedules/generate")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of("request_id", "req-test-cn-audit")))
      )
      .andExpect(status().isCreated());

    mockMvc.perform(
        get("/internal/v1/internal/audit-logs")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items[0].action_name_cn").isNotEmpty())
      .andExpect(jsonPath("$.items[0].perf_context.phase").value("schedule_generate"));

    mockMvc.perform(
        get("/internal/v1/internal/simulation/state")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.scenario_name_cn").isNotEmpty());
  }

  @Test
  void reportEndpointsProvideGoldStandardColumns() throws Exception {
    mockMvc.perform(
        get("/v1/reports/workshop-weekly-plan")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items[0].production_order_no").isNotEmpty())
      .andExpect(jsonPath("$.items[0].customer_remark").isNotEmpty())
      .andExpect(jsonPath("$.items[0].product_name").isNotEmpty())
      .andExpect(jsonPath("$.items[0].spec_model").isNotEmpty())
      .andExpect(jsonPath("$.items[0].production_batch_no").isNotEmpty())
      .andExpect(jsonPath("$.items[0].order_qty").isNumber())
      .andExpect(jsonPath("$.items[0].packaging_form").isNotEmpty())
      .andExpect(jsonPath("$.items[0].sales_order_no").isNotEmpty())
      .andExpect(jsonPath("$.items[0].workshop_outer_packaging_date").isNotEmpty())
      .andExpect(jsonPath("$.items[0].process_schedule_remark").isNotEmpty())
      .andExpect(jsonPath("$.items[0].schedule_version_no").isString());

    mockMvc.perform(
        get("/v1/reports/workshop-monthly-plan")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items[0].order_date").exists())
      .andExpect(jsonPath("$.items[0].planned_finish_date_1").isNotEmpty())
      .andExpect(jsonPath("$.items[0].planned_finish_date_2").isNotEmpty())
      .andExpect(jsonPath("$.items[0].semi_finished_code").isNotEmpty())
      .andExpect(jsonPath("$.items[0].semi_finished_inventory").isNumber())
      .andExpect(jsonPath("$.items[0].semi_finished_demand").isNumber())
      .andExpect(jsonPath("$.items[0].semi_finished_wip").isNumber())
      .andExpect(jsonPath("$.items[0].need_order_qty").isNumber())
      .andExpect(jsonPath("$.items[0].pending_inbound_qty").isNumber())
      .andExpect(jsonPath("$.items[0].workshop_completed_qty").isNumber())
      .andExpect(jsonPath("$.items[0].outer_completed_qty").isNumber())
      .andExpect(jsonPath("$.items[0].match_status").isNotEmpty())
      .andExpect(jsonPath("$.items[0].schedule_version_no").isString());
  }

  @Test
  void reportEndpointsCanBindToSpecifiedScheduleVersion() throws Exception {
    mockMvc.perform(
        post("/api/schedules/generate")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of("request_id", "req-test-report-version-generate")))
      )
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.version_no").value("V001"));

    mockMvc.perform(
        get("/v1/reports/workshop-weekly-plan")
          .header("Authorization", "Bearer test")
          .param("version_no", "V001")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items[0].schedule_version_no").value("V001"));

    mockMvc.perform(
        get("/v1/reports/workshop-monthly-plan")
          .header("Authorization", "Bearer test")
          .param("version_no", "V001")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items[0].schedule_version_no").value("V001"));
  }

  @Test
  void exportEndpointsProvideGoldStandardTemplateHeaders() throws Exception {
    MvcResult weekly = mockMvc.perform(
        get("/v1/reports/workshop-weekly-plan/export")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andReturn();

    assertEquals(
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      weekly.getResponse().getContentType()
    );
    try (
      XSSFWorkbook actual = new XSSFWorkbook(new ByteArrayInputStream(weekly.getResponse().getContentAsByteArray()));
      XSSFWorkbook expected = new XSSFWorkbook(Files.newInputStream(locateOfficialResource("周计划", ".xlsx")))
    ) {
      assertEquals(expected.getNumberOfSheets(), actual.getNumberOfSheets());
      for (int i = 0; i < expected.getNumberOfSheets(); i += 1) {
        assertEquals(expected.getSheetName(i), actual.getSheetName(i));
      }

      Sheet expectedSheet = expected.getSheetAt(0);
      Sheet actualSheet = actual.getSheetAt(0);
      assertRowEquals(expectedSheet, actualSheet, 0, 10);
      assertRowEquals(expectedSheet, actualSheet, 1, 10);
      assertEquals(mergedRanges(expectedSheet), mergedRanges(actualSheet));
    }

    MvcResult monthly = mockMvc.perform(
        get("/v1/reports/workshop-monthly-plan/export")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andReturn();

    assertEquals("application/vnd.ms-excel", monthly.getResponse().getContentType());
    try (
      HSSFWorkbook actual = new HSSFWorkbook(new ByteArrayInputStream(monthly.getResponse().getContentAsByteArray()));
      HSSFWorkbook expected = new HSSFWorkbook(Files.newInputStream(locateOfficialResource("月计划", ".xls")))
    ) {
      Sheet expectedSheet = expected.getSheet("生产计划");
      Sheet actualSheet = actual.getSheet("生产计划");
      assertNotNull(expectedSheet);
      assertNotNull(actualSheet);
      assertRowEquals(expectedSheet, actualSheet, 0, 30);
      assertRowEquals(expectedSheet, actualSheet, 1, 30);
      assertRowEquals(expectedSheet, actualSheet, 2, 30);
      assertEquals(mergedRanges(expectedSheet), mergedRanges(actualSheet));
    }
  }

  @Test
  void integrationMessageIncludesSyncFlowCn() throws Exception {
    mockMvc.perform(
        post("/api/reportings")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of(
            "request_id", "req-test-sync-flow",
            "order_no", "MO-CATH-001",
            "product_code", "PROD_CATH",
            "process_code", "PROC_TUBE",
            "report_qty", 50
          )))
      )
      .andExpect(status().isCreated());

    mockMvc.perform(
        get("/internal/v1/internal/integration/inbox")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items[0].sync_flow_cn").isNotEmpty())
      .andExpect(jsonPath("$.items[0].topic_name_cn").isNotEmpty());
  }

  @Test
  void mesReportingsSupportsTimeFiltering() throws Exception {
    mockMvc.perform(
        post("/api/reportings")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of(
            "request_id", "req-test-time-filter",
            "order_no", "MO-CATH-001",
            "product_code", "PROD_CATH",
            "process_code", "PROC_STERILE",
            "report_qty", 50
          )))
      )
      .andExpect(status().isCreated());

    mockMvc.perform(
        get("/v1/mes/reportings")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.total").value(1));

    mockMvc.perform(
        get("/v1/mes/reportings?start_time=2099-01-01T00:00:00Z")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.total").value(0))
      .andExpect(jsonPath("$.items").isArray());
  }

  @Test
  void onlyFinalProcessReportingCountsAsFinishedQtyAndKeepsRemainingSchedulable() throws Exception {
    mockMvc.perform(
        post("/api/reportings")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of(
            "request_id", "req-test-final-1",
            "order_no", "MO-CATH-001",
            "product_code", "PROD_CATH",
            "process_code", "PROC_TUBE",
            "report_qty", 263
          )))
      )
      .andExpect(status().isCreated());

    mockMvc.perform(
        post("/api/reportings")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of(
            "request_id", "req-test-final-2",
            "order_no", "MO-CATH-001",
            "product_code", "PROD_CATH",
            "process_code", "PROC_ASSEMBLY",
            "report_qty", 281
          )))
      )
      .andExpect(status().isCreated());

    MvcResult beforeFinalResult = mockMvc.perform(get("/api/orders"))
      .andExpect(status().isOk())
      .andReturn();
    Map<String, Object> beforeFinalBody = objectMapper.readValue(beforeFinalResult.getResponse().getContentAsString(), Map.class);
    List<Map<String, Object>> beforeFinalOrders = (List<Map<String, Object>>) beforeFinalBody.get("items");
    Map<String, Object> beforeFinalCath = beforeFinalOrders.stream()
      .filter(row -> "MO-CATH-001".equals(row.get("order_no")))
      .findFirst()
      .orElseThrow();
    List<Map<String, Object>> beforeFinalItems = (List<Map<String, Object>>) beforeFinalCath.get("items");
    assertEquals(0d, ((Number) beforeFinalItems.get(0).get("completed_qty")).doubleValue());

    mockMvc.perform(
        post("/api/reportings")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of(
            "request_id", "req-test-final-3",
            "order_no", "MO-CATH-001",
            "product_code", "PROD_CATH",
            "process_code", "PROC_STERILE",
            "report_qty", 276
          )))
      )
      .andExpect(status().isCreated());

    MvcResult afterFinalResult = mockMvc.perform(get("/api/orders"))
      .andExpect(status().isOk())
      .andReturn();
    Map<String, Object> afterFinalBody = objectMapper.readValue(afterFinalResult.getResponse().getContentAsString(), Map.class);
    List<Map<String, Object>> afterFinalOrders = (List<Map<String, Object>>) afterFinalBody.get("items");
    Map<String, Object> afterFinalCath = afterFinalOrders.stream()
      .filter(row -> "MO-CATH-001".equals(row.get("order_no")))
      .findFirst()
      .orElseThrow();
    List<Map<String, Object>> afterFinalItems = (List<Map<String, Object>>) afterFinalCath.get("items");
    assertEquals(276d, ((Number) afterFinalItems.get(0).get("completed_qty")).doubleValue());

    mockMvc.perform(
        post("/api/schedules/generate")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of("request_id", "req-test-final-generate")))
      )
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.version_no").value("V001"));

    MvcResult tasksResult = mockMvc.perform(
        get("/internal/v1/internal/schedule-versions/V001/tasks")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andReturn();
    Map<String, Object> tasksBody = objectMapper.readValue(tasksResult.getResponse().getContentAsString(), Map.class);
    List<Map<String, Object>> tasks = (List<Map<String, Object>>) tasksBody.get("items");
    double sterilePlanQty = tasks.stream()
      .filter(row -> "MO-CATH-001".equals(row.get("order_no")))
      .filter(row -> "PROC_STERILE".equals(row.get("process_code")))
      .mapToDouble(row -> ((Number) row.get("plan_qty")).doubleValue())
      .sum();
    assertEquals(24d, sterilePlanQty);
  }

  @Test
  void manualAdvanceDayShouldKeepIncreasingFinalFinishedQtyWhenOrderIsNotDone() throws Exception {
    mockMvc.perform(
        post("/internal/v1/internal/simulation/manual/advance-day")
          .header("Authorization", "Bearer test")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of("request_id", "req-test-manual-progress-1")))
      )
      .andExpect(status().isOk());

    double firstFinishedQty = sumFinalProcessReportQty("MO-CATH-001");
    if (firstFinishedQty >= 300d) {
      return;
    }

    mockMvc.perform(
        post("/internal/v1/internal/simulation/manual/advance-day")
          .header("Authorization", "Bearer test")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of("request_id", "req-test-manual-progress-2")))
      )
      .andExpect(status().isOk());

    double secondFinishedQty = sumFinalProcessReportQty("MO-CATH-001");
    assertTrue(
      secondFinishedQty > firstFinishedQty,
      "manual advance should continue increasing final finished qty before order is fully done"
    );
  }

  @Test
  void routeReportingShouldBeMonotonicAndConvergeToOrderQty() throws Exception {
    double tube = 0d;
    double assembly = 0d;
    double sterile = 0d;

    for (int day = 1; day <= 10; day += 1) {
      mockMvc.perform(
          post("/internal/v1/internal/simulation/manual/advance-day")
            .header("Authorization", "Bearer test")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("request_id", "req-test-route-converge-" + day)))
        )
        .andExpect(status().isOk());

      tube = sumProcessReportQty("MO-CATH-001", "PROC_TUBE");
      assembly = sumProcessReportQty("MO-CATH-001", "PROC_ASSEMBLY");
      sterile = sumProcessReportQty("MO-CATH-001", "PROC_STERILE");

      assertTrue(tube >= assembly, "tube cumulative qty should be >= assembly cumulative qty");
      assertTrue(assembly >= sterile, "assembly cumulative qty should be >= sterile cumulative qty");
      assertTrue(tube <= 300d && assembly <= 300d && sterile <= 300d, "each process cumulative qty should not exceed order qty");

      if (sterile >= 300d) {
        break;
      }
    }

    assertEquals(300d, tube);
    assertEquals(300d, assembly);
    assertEquals(300d, sterile);
  }

  @Test
  void erpRawSplitEndpointsAreAvailable() throws Exception {
    mockMvc.perform(
        get("/v1/erp/sales-order-headers-raw")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items").isArray());

    mockMvc.perform(
        get("/v1/erp/sales-order-lines-raw")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items").isArray());

    mockMvc.perform(
        get("/v1/erp/production-order-headers-raw")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items").isArray());

    mockMvc.perform(
        get("/v1/erp/production-order-lines-raw")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items").isArray());
  }

  private static Path locateOfficialResource(String keyword, String extension) throws IOException {
    Path dir = Paths.get("..", "..", "doc", "offical_resource").toAbsolutePath().normalize();
    try (var stream = Files.list(dir)) {
      return stream
        .filter(path -> {
          String name = path.getFileName().toString();
          return !name.startsWith("~$") && name.contains(keyword) && name.endsWith(extension);
        })
        .findFirst()
        .orElseThrow(() -> new IOException("Official resource not found: " + keyword + extension));
    }
  }

  private static void assertRowEquals(Sheet expected, Sheet actual, int rowIndex, int columnCount) {
    assertEquals(rowValues(expected.getRow(rowIndex), columnCount), rowValues(actual.getRow(rowIndex), columnCount));
  }

  private static List<String> rowValues(Row row, int columnCount) {
    List<String> values = new ArrayList<>();
    for (int i = 0; i < columnCount; i += 1) {
      if (row == null) {
        values.add("");
        continue;
      }
      var cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
      values.add(cell == null ? "" : DATA_FORMATTER.formatCellValue(cell));
    }
    return values;
  }

  private static List<String> mergedRanges(Sheet sheet) {
    List<String> merged = new ArrayList<>();
    for (int i = 0; i < sheet.getNumMergedRegions(); i += 1) {
      merged.add(sheet.getMergedRegion(i).formatAsString());
    }
    merged.sort(Comparator.naturalOrder());
    return merged;
  }

  @SuppressWarnings("unchecked")
  private double sumFinalProcessReportQty(String orderNo) throws Exception {
    MvcResult reportingsResult = mockMvc.perform(
        get("/v1/mes/reportings")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andReturn();
    Map<String, Object> reportingsBody = objectMapper.readValue(reportingsResult.getResponse().getContentAsString(), Map.class);
    List<Map<String, Object>> reportings = (List<Map<String, Object>>) reportingsBody.get("items");
    return reportings.stream()
      .filter(row -> orderNo.equals(row.get("order_no")))
      .filter(row -> "PROC_STERILE".equals(row.get("process_code")))
      .mapToDouble(row -> ((Number) row.get("report_qty")).doubleValue())
      .sum();
  }

  @SuppressWarnings("unchecked")
  private double sumProcessReportQty(String orderNo, String processCode) throws Exception {
    MvcResult reportingsResult = mockMvc.perform(
        get("/v1/mes/reportings")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andReturn();
    Map<String, Object> reportingsBody = objectMapper.readValue(reportingsResult.getResponse().getContentAsString(), Map.class);
    List<Map<String, Object>> reportings = (List<Map<String, Object>>) reportingsBody.get("items");
    return reportings.stream()
      .filter(row -> orderNo.equals(row.get("order_no")))
      .filter(row -> processCode.equals(row.get("process_code")))
      .mapToDouble(row -> ((Number) row.get("report_qty")).doubleValue())
      .sum();
  }
}
