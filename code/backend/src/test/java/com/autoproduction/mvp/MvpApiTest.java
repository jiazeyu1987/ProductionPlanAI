package com.autoproduction.mvp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.time.LocalDate;
import java.time.ZoneId;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
  "mvp.erp.use-real-orders=false",
  "mvp.erp.refresh.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MvpApiTest {
  private static final DataFormatter DATA_FORMATTER = new DataFormatter();

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private JdbcTemplate jdbcTemplate;

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
  void internalListResponseSupportsRequestIdHeaderAndPagination() throws Exception {
    String requestId = "req-test-list-page";
    mockMvc.perform(
        get("/internal/v1/internal/order-pool")
          .header("Authorization", "Bearer test")
          .header("x-request-id", requestId)
          .param("page", "1")
          .param("page_size", "1")
      )
      .andExpect(status().isOk())
      .andExpect(header().string("x-request-id", requestId))
      .andExpect(jsonPath("$.request_id").value(requestId))
      .andExpect(jsonPath("$.page").value(1))
      .andExpect(jsonPath("$.page_size").value(1))
      .andExpect(jsonPath("$.items.length()").value(1));
  }

  @Test
  @SuppressWarnings("unchecked")
  void publishUsesDefaultOperatorWhenMissingInPayload() throws Exception {
    mockMvc.perform(
        post("/api/schedules/generate")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of("request_id", "req-test-default-op-generate")))
      )
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.version_no").value("V001"));

    String publishRequestId = "req-test-default-op-publish";
    mockMvc.perform(
        post("/internal/v1/internal/schedule-versions/V001/publish")
          .header("Authorization", "Bearer test")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of(
            "request_id", publishRequestId,
            "reason", "publish without operator"
          )))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true));

    MvcResult auditResult = mockMvc.perform(
        get("/internal/v1/internal/audit-logs")
          .header("Authorization", "Bearer test")
          .param("request_id", publishRequestId)
      )
      .andExpect(status().isOk())
      .andReturn();

    Map<String, Object> auditBody = objectMapper.readValue(auditResult.getResponse().getContentAsString(), Map.class);
    List<Map<String, Object>> items = (List<Map<String, Object>>) auditBody.get("items");
    assertTrue(
      items.stream().anyMatch(row ->
        "PUBLISH_VERSION".equals(String.valueOf(row.get("action"))) &&
        publishRequestId.equals(String.valueOf(row.get("request_id"))) &&
        "publisher".equals(String.valueOf(row.get("operator")))
      ),
      "Expected publish audit row with default operator=publisher"
    );
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

    mockMvc.perform(get("/api/schedules/latest"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.version_no").value("V001"))
      .andExpect(jsonPath("$.status").value("PUBLISHED"));
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
  void scheduleProjectionSnapshotColumnsShouldBePersisted() throws Exception {
    mockMvc.perform(
        post("/api/schedules/generate")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of("request_id", "req-test-snapshot-columns")))
      )
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.version_no").value("V001"));

    Map<String, Object> row = jdbcTemplate.queryForMap(
      "select snapshot_json, algorithm_json from schedule_version where version_no = ?",
      "V001"
    );
    Map<String, Object> snapshotJson = parsePersistedJsonColumn(row.get("snapshot_json"));
    Map<String, Object> algorithmJson = parsePersistedJsonColumn(row.get("algorithm_json"));
    assertTrue(snapshotJson.containsKey("unscheduled"), "snapshot_json should contain unscheduled data");
    assertTrue(algorithmJson.containsKey("strategy_code"), "algorithm_json should contain strategy_code");
  }

  @Test
  void unifiedQueryScheduleSnapshotEndpointReturnsProjectionSnapshot() throws Exception {
    mockMvc.perform(
        post("/api/schedules/generate")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of("request_id", "req-test-query-schedule-snapshot")))
      )
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.version_no").value("V001"));

    mockMvc.perform(get("/queries/schedules/V001"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.version_no").value("V001"))
      .andExpect(jsonPath("$.allocations").isArray())
      .andExpect(jsonPath("$.unscheduled").isArray());
  }

  @Test
  void unifiedQueryScheduleReadEndpointsExposeAlgorithmAndDiff() throws Exception {
    mockMvc.perform(
        post("/api/schedules/generate")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of("request_id", "req-test-query-algo-diff-v1")))
      )
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.version_no").value("V001"));

    mockMvc.perform(
        patch("/api/orders/MO-STENT-001")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of(
            "request_id", "req-test-query-algo-diff-patch",
            "frozen_flag", 1
          )))
      )
      .andExpect(status().isOk());

    mockMvc.perform(
        post("/api/schedules/generate")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of(
            "request_id", "req-test-query-algo-diff-v2",
            "base_version_no", "V001"
          )))
      )
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.version_no").value("V002"));

    mockMvc.perform(get("/queries/schedules/V002/algorithm"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.version_no").value("V002"))
      .andExpect(jsonPath("$.strategy_code").isString());

    mockMvc.perform(get("/queries/schedules/V002/diff").param("compare_with", "V001"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.base_version_no").value("V001"))
      .andExpect(jsonPath("$.compare_version_no").value("V002"))
      .andExpect(jsonPath("$.changed_task_count").isNumber());
  }

  @Test
  void unifiedQueryOperationalReadEndpointsWork() throws Exception {
    mockMvc.perform(get("/queries/order-pool"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items").isArray());

    mockMvc.perform(get("/queries/alerts").param("status", "OPEN"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items").isArray());

    mockMvc.perform(get("/queries/dispatch-commands"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items").isArray());

    mockMvc.perform(get("/queries/audit-logs"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items").isArray());

    mockMvc.perform(get("/queries/simulation/state"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.current_sim_date").isString());

    mockMvc.perform(get("/queries/simulation/events").param("page", "1").param("page_size", "10"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items").isArray());
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
  void scheduleVersionDiffEndpointWorksFromProjection() throws Exception {
    mockMvc.perform(
        post("/api/schedules/generate")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of("request_id", "req-test-diff-v1")))
      )
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.version_no").value("V001"));

    mockMvc.perform(
        patch("/api/orders/MO-STENT-001")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of(
            "request_id", "req-test-diff-patch",
            "frozen_flag", 1
          )))
      )
      .andExpect(status().isOk());

    mockMvc.perform(
        post("/api/schedules/generate")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of(
            "request_id", "req-test-diff-v2",
            "base_version_no", "V001"
          )))
      )
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.version_no").value("V002"));

    mockMvc.perform(
        get("/internal/v1/internal/schedule-versions/V002/diff")
          .header("Authorization", "Bearer test")
          .param("compare_with", "V001")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.base_version_no").value("V001"))
      .andExpect(jsonPath("$.compare_version_no").value("V002"))
      .andExpect(jsonPath("$.changed_order_count").isNumber())
      .andExpect(jsonPath("$.changed_task_count").isNumber())
      .andExpect(jsonPath("$.delayed_order_delta").isNumber())
      .andExpect(jsonPath("$.overloaded_process_delta").isNumber());
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

    mockMvc.perform(
        get("/internal/v1/internal/order-pool")
          .header("Authorization", "Bearer test")
      )
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

    MvcResult ordersAfterPriority = mockMvc.perform(
        get("/internal/v1/internal/order-pool")
          .header("Authorization", "Bearer test")
      )
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

    MvcResult ordersAfterUnpriority = mockMvc.perform(
        get("/internal/v1/internal/order-pool")
          .header("Authorization", "Bearer test")
      )
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
    MvcResult runAccepted = mockMvc.perform(
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
      .andExpect(jsonPath("$.accepted").value(true))
      .andExpect(jsonPath("$.status").value("ACCEPTED"))
      .andExpect(jsonPath("$.job_id").isNotEmpty())
      .andReturn();

    Map<String, Object> runAcceptedBody = objectMapper.readValue(runAccepted.getResponse().getContentAsString(), Map.class);
    Map<String, Object> job = waitForJobCompletion(String.valueOf(runAcceptedBody.get("job_id")));
    assertEquals("SUCCEEDED", String.valueOf(job.get("status")));

    Map<String, Object> simulationState = getSimulationStateBody();
    Map<String, Object> lastRunSummary = mapValue(simulationState, "last_run_summary");
    assertEquals(6, ((Number) lastRunSummary.get("new_sales_orders")).intValue());
    assertEquals(6, ((Number) lastRunSummary.get("new_production_orders")).intValue());
    assertEquals(2, ((Number) lastRunSummary.get("generated_versions")).intValue());

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

    MvcResult advanceAccepted = mockMvc.perform(
        post("/internal/v1/internal/simulation/manual/advance-day")
          .header("Authorization", "Bearer test")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of("request_id", "req-test-manual-advance")))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.accepted").value(true))
      .andExpect(jsonPath("$.status").value("ACCEPTED"))
      .andExpect(jsonPath("$.job_id").isNotEmpty())
      .andReturn();

    Map<String, Object> advanceAcceptedBody = objectMapper.readValue(advanceAccepted.getResponse().getContentAsString(), Map.class);
    Map<String, Object> advanceJob = waitForJobCompletion(String.valueOf(advanceAcceptedBody.get("job_id")));
    assertEquals("SUCCEEDED", String.valueOf(advanceJob.get("status")));

    Map<String, Object> advancedState = getSimulationStateBody();
    Map<String, Object> lastRunSummary = mapValue(advancedState, "last_run_summary");
    assertEquals(1, ((Number) lastRunSummary.get("days")).intValue());
    assertEquals(1, ((Number) lastRunSummary.get("generated_versions")).intValue());
    assertTrue(lastRunSummary.get("reporting_count") instanceof Number);
    assertTrue(lastRunSummary.get("manual_advance_duration_ms") instanceof Number);
    assertTrue(lastRunSummary.get("manual_advance_phase_duration_ms") instanceof Map<?, ?>);
    assertTrue(lastRunSummary.get("schedule_generate_duration_ms") instanceof Number);
    assertTrue(lastRunSummary.get("unscheduled_reason_distribution") instanceof Map<?, ?>);
    assertTrue(advancedState.get("latest_version_no") instanceof String);

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
  void simulationStateCurrentDateShouldAlignToToday() throws Exception {
    String today = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
    mockMvc.perform(
        get("/internal/v1/internal/simulation/state")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.current_sim_date").value(today));
  }

  @Test
  void manualAdvanceDayUsesClientDateNextDayAsMinimumBusinessDate() throws Exception {
    MvcResult initialStateResult = mockMvc.perform(
        get("/internal/v1/internal/simulation/state")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andReturn();
    Map<String, Object> initialState = objectMapper.readValue(initialStateResult.getResponse().getContentAsString(), Map.class);
    LocalDate currentSimDate = LocalDate.parse(String.valueOf(initialState.get("current_sim_date")));
    String clientDate = currentSimDate.toString();
    String expectedStartDate = currentSimDate.plusDays(1).toString();
    String expectedNextCurrentDate = currentSimDate.plusDays(2).toString();

    MvcResult advanceAccepted = mockMvc.perform(
        post("/internal/v1/internal/simulation/manual/advance-day")
          .header("Authorization", "Bearer test")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of(
            "request_id", "req-test-manual-client-date",
            "client_date", clientDate
          )))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.accepted").value(true))
      .andExpect(jsonPath("$.job_id").isNotEmpty())
      .andReturn();

    Map<String, Object> advanceAcceptedBody = objectMapper.readValue(advanceAccepted.getResponse().getContentAsString(), Map.class);
    Map<String, Object> advanceJob = waitForJobCompletion(String.valueOf(advanceAcceptedBody.get("job_id")));
    assertEquals("SUCCEEDED", String.valueOf(advanceJob.get("status")));

    Map<String, Object> updatedState = getSimulationStateBody();
    Map<String, Object> lastRunSummary = mapValue(updatedState, "last_run_summary");
    assertEquals(expectedStartDate, String.valueOf(lastRunSummary.get("start_date")));
    assertEquals(expectedNextCurrentDate, String.valueOf(updatedState.get("current_sim_date")));
  }

  @Test
  void manualAdvanceDayAcceptsSlashClientDateFormat() throws Exception {
    MvcResult initialStateResult = mockMvc.perform(
        get("/internal/v1/internal/simulation/state")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andReturn();
    Map<String, Object> initialState = objectMapper.readValue(initialStateResult.getResponse().getContentAsString(), Map.class);
    LocalDate currentSimDate = LocalDate.parse(String.valueOf(initialState.get("current_sim_date")));
    LocalDate nextDayFromClientDate = LocalDate.of(2026, 3, 27);
    LocalDate expectedStartDate = currentSimDate.isBefore(nextDayFromClientDate) ? nextDayFromClientDate : currentSimDate;
    LocalDate expectedNextCurrentDate = expectedStartDate.plusDays(1);

    MvcResult advanceAccepted = mockMvc.perform(
        post("/internal/v1/internal/simulation/manual/advance-day")
          .header("Authorization", "Bearer test")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of(
            "request_id", "req-test-manual-client-date-slash",
            "client_date", "2026/3/26"
          )))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.accepted").value(true))
      .andExpect(jsonPath("$.job_id").isNotEmpty())
      .andReturn();

    Map<String, Object> advanceAcceptedBody = objectMapper.readValue(advanceAccepted.getResponse().getContentAsString(), Map.class);
    Map<String, Object> advanceJob = waitForJobCompletion(String.valueOf(advanceAcceptedBody.get("job_id")));
    assertEquals("SUCCEEDED", String.valueOf(advanceJob.get("status")));

    Map<String, Object> updatedState = getSimulationStateBody();
    Map<String, Object> lastRunSummary = mapValue(updatedState, "last_run_summary");
    assertEquals(expectedStartDate.toString(), String.valueOf(lastRunSummary.get("start_date")));
    assertEquals(expectedNextCurrentDate.toString(), String.valueOf(updatedState.get("current_sim_date")));
  }

  @Test
  void manualAdvanceDayWithoutClientDateUsesServerDateNextDayAsMinimumBusinessDate() throws Exception {
    MvcResult initialStateResult = mockMvc.perform(
        get("/internal/v1/internal/simulation/state")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andReturn();
    Map<String, Object> initialState = objectMapper.readValue(initialStateResult.getResponse().getContentAsString(), Map.class);
    LocalDate currentSimDate = LocalDate.parse(String.valueOf(initialState.get("current_sim_date")));
    LocalDate nextDayFromServerDate = LocalDate.now().plusDays(1);
    LocalDate expectedStartDate = currentSimDate.isBefore(nextDayFromServerDate) ? nextDayFromServerDate : currentSimDate;
    LocalDate expectedNextCurrentDate = expectedStartDate.plusDays(1);

    MvcResult advanceAccepted = mockMvc.perform(
        post("/internal/v1/internal/simulation/manual/advance-day")
          .header("Authorization", "Bearer test")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of(
            "request_id", "req-test-manual-server-date-fallback"
          )))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.accepted").value(true))
      .andExpect(jsonPath("$.job_id").isNotEmpty())
      .andReturn();

    Map<String, Object> advanceAcceptedBody = objectMapper.readValue(advanceAccepted.getResponse().getContentAsString(), Map.class);
    Map<String, Object> advanceJob = waitForJobCompletion(String.valueOf(advanceAcceptedBody.get("job_id")));
    assertEquals("SUCCEEDED", String.valueOf(advanceJob.get("status")));

    Map<String, Object> updatedState = getSimulationStateBody();
    Map<String, Object> lastRunSummary = mapValue(updatedState, "last_run_summary");
    assertEquals(expectedStartDate.toString(), String.valueOf(lastRunSummary.get("start_date")));
    assertEquals(expectedNextCurrentDate.toString(), String.valueOf(updatedState.get("current_sim_date")));
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
  @SuppressWarnings("unchecked")
  void processRoutesContainAngioCathRouteInSeedData() throws Exception {
    MvcResult result = mockMvc.perform(
        get("/v1/mes/process-routes")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andReturn();

    Map<String, Object> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
    List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");

    List<Map<String, Object>> angioRows = items.stream()
      .filter(row -> "PROD_ANGIO_CATH".equals(String.valueOf(row.get("product_code"))))
      .toList();

    assertTrue(!angioRows.isEmpty(), "Expected seeded route rows for PROD_ANGIO_CATH");
    assertTrue(
      angioRows.stream().anyMatch(row -> "Z470".equals(String.valueOf(row.get("process_code")))),
      "Expected process Z470 in angio cath route"
    );
    assertTrue(
      angioRows.stream().anyMatch(row -> "W030".equals(String.valueOf(row.get("process_code")))),
      "Expected process W030 in angio cath route"
    );
    assertTrue(
      angioRows.stream().allMatch(row -> "造影导管".equals(String.valueOf(row.get("product_name_cn")))),
      "Expected product_name_cn to be 造影导管 for angio cath rows"
    );
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
  @SuppressWarnings("unchecked")
  void mesReportingsShouldKeepRuntimeRowsWhenProjectionIsPartial() throws Exception {
    MvcResult firstCreate = mockMvc.perform(
        post("/api/reportings")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of(
            "request_id", "req-test-reporting-merge-1",
            "order_no", "MO-CATH-001",
            "product_code", "PROD_CATH",
            "process_code", "PROC_TUBE",
            "report_qty", 50
          )))
      )
      .andExpect(status().isCreated())
      .andReturn();

    mockMvc.perform(
        post("/api/reportings")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of(
            "request_id", "req-test-reporting-merge-2",
            "order_no", "MO-CATH-001",
            "product_code", "PROD_CATH",
            "process_code", "PROC_ASSEMBLY",
            "report_qty", 60
          )))
      )
      .andExpect(status().isCreated());

    Map<String, Object> firstBody = objectMapper.readValue(firstCreate.getResponse().getContentAsString(), Map.class);
    jdbcTemplate.update("delete from reporting where reporting_id = ?", String.valueOf(firstBody.get("reporting_id")));

    MvcResult reportingsResult = mockMvc.perform(
        get("/v1/mes/reportings")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.total").value(2))
      .andReturn();

    Map<String, Object> reportingsBody = objectMapper.readValue(reportingsResult.getResponse().getContentAsString(), Map.class);
    List<Map<String, Object>> items = (List<Map<String, Object>>) reportingsBody.get("items");
    assertEquals(2, items.size());
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

    MvcResult beforeFinalResult = mockMvc.perform(
        get("/internal/v1/internal/order-pool")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andReturn();
    Map<String, Object> beforeFinalBody = objectMapper.readValue(beforeFinalResult.getResponse().getContentAsString(), Map.class);
    List<Map<String, Object>> beforeFinalOrders = (List<Map<String, Object>>) beforeFinalBody.get("items");
    Map<String, Object> beforeFinalCath = beforeFinalOrders.stream()
      .filter(row -> "MO-CATH-001".equals(row.get("order_no")))
      .findFirst()
      .orElseThrow();
    assertEquals(0d, ((Number) beforeFinalCath.get("completed_qty")).doubleValue());

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

    MvcResult afterFinalResult = mockMvc.perform(
        get("/internal/v1/internal/order-pool")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andReturn();
    Map<String, Object> afterFinalBody = objectMapper.readValue(afterFinalResult.getResponse().getContentAsString(), Map.class);
    List<Map<String, Object>> afterFinalOrders = (List<Map<String, Object>>) afterFinalBody.get("items");
    Map<String, Object> afterFinalCath = afterFinalOrders.stream()
      .filter(row -> "MO-CATH-001".equals(row.get("order_no")))
      .findFirst()
      .orElseThrow();
    assertEquals(276d, ((Number) afterFinalCath.get("completed_qty")).doubleValue());

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

  @Test
  void erpPurchaseOrdersEndpointIsAvailable() throws Exception {
    mockMvc.perform(
        get("/v1/erp/purchase-orders")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items").isArray());
  }

  @Test
  void internalErpRefreshEndpointSupportsManualTriggerAndStatusQuery() throws Exception {
    mockMvc.perform(
        post("/internal/v1/internal/integration/erp/refresh")
          .header("Authorization", "Bearer test")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{}")
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("REQUEST_ID_REQUIRED"));

    mockMvc.perform(
        post("/internal/v1/internal/integration/erp/refresh")
          .header("Authorization", "Bearer test")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of(
            "request_id", "req-test-erp-manual-refresh",
            "operator", "integration-admin",
            "reason", "manual test"
          )))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").isString())
      .andExpect(jsonPath("$.refresh_state").isMap())
      .andExpect(jsonPath("$.data_counts").isMap());

    mockMvc.perform(
        get("/internal/v1/internal/integration/erp/refresh-status")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.module").value("ERP_DATA_MANAGER"))
      .andExpect(jsonPath("$.erp_connection").isMap())
      .andExpect(jsonPath("$.refresh_state").isMap())
      .andExpect(jsonPath("$.data_counts").isMap());
  }

  @Test
  @SuppressWarnings("unchecked")
  void masterdataConfigSupportsCreateAndDeleteForCrudTabs() throws Exception {
    MvcResult configResult = mockMvc.perform(
        get("/internal/v1/internal/masterdata/config")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andReturn();
    Map<String, Object> configBody = objectMapper.readValue(configResult.getResponse().getContentAsString(), Map.class);
    List<Map<String, Object>> processRows = (List<Map<String, Object>>) configBody.get("process_configs");
    assertTrue(!processRows.isEmpty(), "process_configs should not be empty");
    assertFalse(configBody.containsKey("resource_pool"), "resource_pool should be removed from masterdata/config");
    assertFalse(configBody.containsKey("section_leader_bindings"), "section_leader_bindings should be removed from masterdata/config");
    assertFalse(configBody.containsKey("horizon_start_date"), "horizon_start_date should be moved to schedule-calendar/rules");
    assertFalse(configBody.containsKey("skip_statutory_holidays"), "skip_statutory_holidays should be moved to schedule-calendar/rules");

    Map<String, Object> keepProcess = processRows.get(0);
    String keepProcessCode = String.valueOf(keepProcess.get("process_code"));
    double keepCapacityPerShift = ((Number) keepProcess.get("capacity_per_shift")).doubleValue();
    int keepRequiredWorkers = ((Number) keepProcess.get("required_workers")).intValue();
    int keepRequiredMachines = ((Number) keepProcess.get("required_machines")).intValue();

    MvcResult rulesResult = mockMvc.perform(
        get("/internal/v1/internal/schedule-calendar/rules")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andReturn();
    Map<String, Object> rulesBody = objectMapper.readValue(rulesResult.getResponse().getContentAsString(), Map.class);
    String horizonStartDate = String.valueOf(rulesBody.get("horizon_start_date"));
    assertTrue(horizonStartDate != null && !horizonStartDate.isBlank(), "horizon_start_date should not be blank");

    MvcResult routesResult = mockMvc.perform(
        get("/v1/mes/process-routes")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andReturn();
    Map<String, Object> routesBody = objectMapper.readValue(routesResult.getResponse().getContentAsString(), Map.class);
    List<Map<String, Object>> routeItems = (List<Map<String, Object>>) routesBody.get("items");
    assertTrue(!routeItems.isEmpty(), "process routes should not be empty");
    String productCode = String.valueOf(routeItems.get(0).get("product_code"));

    String addedProcessCode = "PROC_CRUD_ADD";
    Map<String, Object> createPayload = Map.of(
      "request_id", "req-test-masterdata-crud-create",
      "process_configs", List.of(
        Map.of(
          "process_code", keepProcessCode,
          "capacity_per_shift", keepCapacityPerShift,
          "required_workers", keepRequiredWorkers,
          "required_machines", keepRequiredMachines
        ),
        Map.of(
          "process_code", addedProcessCode,
          "capacity_per_shift", 88.5,
          "required_workers", 3,
          "required_machines", 2
        )
      ),
      "initial_carryover_occupancy", List.of(
        Map.of(
          "calendar_date", horizonStartDate,
          "shift_code", "DAY",
          "process_code", addedProcessCode,
          "occupied_workers", 2,
          "occupied_machines", 1
        )
      ),
      "material_availability", List.of(
        Map.of(
          "calendar_date", horizonStartDate,
          "shift_code", "DAY",
          "product_code", productCode,
          "process_code", addedProcessCode,
          "available_qty", 123.45
        )
      )
    );

    MvcResult createResult = mockMvc.perform(
        post("/internal/v1/internal/masterdata/config")
          .header("Authorization", "Bearer test")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(createPayload))
      )
      .andExpect(status().isOk())
      .andReturn();
    Map<String, Object> createBody = objectMapper.readValue(createResult.getResponse().getContentAsString(), Map.class);

    List<Map<String, Object>> createdProcessRows = (List<Map<String, Object>>) createBody.get("process_configs");
    List<Map<String, Object>> createdCarryoverRows = (List<Map<String, Object>>) createBody.get("initial_carryover_occupancy");
    List<Map<String, Object>> createdMaterialRows = (List<Map<String, Object>>) createBody.get("material_availability");

    assertTrue(
      createdProcessRows.stream().anyMatch(row -> addedProcessCode.equals(String.valueOf(row.get("process_code")))),
      "added process should exist after create"
    );
    assertEquals(1, createdCarryoverRows.size(), "initial_carryover_occupancy should only contain created row");
    assertEquals(1, createdMaterialRows.size(), "material_availability should only contain created row");

    Map<String, Object> deletePayload = Map.of(
      "request_id", "req-test-masterdata-crud-delete",
      "process_configs", List.of(
        Map.of(
          "process_code", keepProcessCode,
          "capacity_per_shift", keepCapacityPerShift,
          "required_workers", keepRequiredWorkers,
          "required_machines", keepRequiredMachines
        )
      ),
      "initial_carryover_occupancy", List.of(),
      "material_availability", List.of()
    );

    MvcResult deleteResult = mockMvc.perform(
        post("/internal/v1/internal/masterdata/config")
          .header("Authorization", "Bearer test")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(deletePayload))
      )
      .andExpect(status().isOk())
      .andReturn();
    Map<String, Object> deleteBody = objectMapper.readValue(deleteResult.getResponse().getContentAsString(), Map.class);

    List<Map<String, Object>> deletedProcessRows = (List<Map<String, Object>>) deleteBody.get("process_configs");
    List<Map<String, Object>> deletedCarryoverRows = (List<Map<String, Object>>) deleteBody.get("initial_carryover_occupancy");
    List<Map<String, Object>> deletedMaterialRows = (List<Map<String, Object>>) deleteBody.get("material_availability");

    assertTrue(
      deletedProcessRows.stream().noneMatch(row -> addedProcessCode.equals(String.valueOf(row.get("process_code")))),
      "added process should be removed after delete"
    );
    assertEquals(0, deletedCarryoverRows.size(), "initial_carryover_occupancy should be empty after delete");
    assertEquals(0, deletedMaterialRows.size(), "material_availability should be empty after delete");

    mockMvc.perform(
        post("/internal/v1/internal/masterdata/config")
          .header("Authorization", "Bearer test")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of(
            "request_id", "req-test-masterdata-crud-deprecated",
            "resource_pool", List.of()
          )))
      )
      .andExpect(status().isBadRequest());
  }

  @Test
  @SuppressWarnings("unchecked")
  void scheduleCalendarRulesEndpointSupportsReadAndWrite() throws Exception {
    MvcResult getResult = mockMvc.perform(
        get("/internal/v1/internal/schedule-calendar/rules")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andReturn();
    Map<String, Object> getBody = objectMapper.readValue(getResult.getResponse().getContentAsString(), Map.class);
    assertTrue(getBody.containsKey("horizon_start_date"), "rules should expose horizon_start_date");
    assertTrue(getBody.containsKey("horizon_days"), "rules should expose horizon_days");
    assertTrue(getBody.containsKey("date_shift_mode_by_date"), "rules should expose date_shift_mode_by_date");

    Map<String, Object> payload = Map.of(
      "request_id", "req-test-calendar-rules-write",
      "horizon_start_date", "2026-03-25",
      "horizon_days", 6,
      "skip_statutory_holidays", true,
      "weekend_rest_mode", "SINGLE",
      "date_shift_mode_by_date", Map.of("2026-03-27", "REST")
    );
    MvcResult postResult = mockMvc.perform(
        post("/internal/v1/internal/schedule-calendar/rules")
          .header("Authorization", "Bearer test")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(payload))
      )
      .andExpect(status().isOk())
      .andReturn();
    Map<String, Object> postBody = objectMapper.readValue(postResult.getResponse().getContentAsString(), Map.class);
    assertEquals("2026-03-25", String.valueOf(postBody.get("horizon_start_date")));
    assertEquals(6, ((Number) postBody.get("horizon_days")).intValue());
    assertTrue(Boolean.TRUE.equals(postBody.get("skip_statutory_holidays")));
    assertEquals("SINGLE", String.valueOf(postBody.get("weekend_rest_mode")));
    Map<String, Object> manualModeMap = (Map<String, Object>) postBody.get("date_shift_mode_by_date");
    assertEquals("REST", String.valueOf(manualModeMap.get("2026-03-27")));
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

  @SuppressWarnings("unchecked")
  private Map<String, Object> getSimulationStateBody() throws Exception {
    MvcResult stateResult = mockMvc.perform(
        get("/internal/v1/internal/simulation/state")
          .header("Authorization", "Bearer test")
      )
      .andExpect(status().isOk())
      .andReturn();
    return objectMapper.readValue(stateResult.getResponse().getContentAsString(), Map.class);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> waitForJobCompletion(String jobId) throws Exception {
    for (int attempt = 0; attempt < 50; attempt += 1) {
      MvcResult jobResult = mockMvc.perform(get("/queries/jobs/" + jobId))
        .andExpect(status().isOk())
        .andReturn();
      Map<String, Object> jobBody = objectMapper.readValue(jobResult.getResponse().getContentAsString(), Map.class);
      String statusValue = String.valueOf(jobBody.get("status"));
      if ("SUCCEEDED".equals(statusValue) || "FAILED".equals(statusValue) || "CANCELLED".equals(statusValue)) {
        return jobBody;
      }
      try {
        Thread.sleep(100L);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new AssertionError("Interrupted while waiting for job completion: " + jobId, ex);
      }
    }
    throw new AssertionError("Job did not complete in time: " + jobId);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> mapValue(Map<String, Object> body, String key) {
    Object value = body.get(key);
    assertTrue(value instanceof Map<?, ?>, key + " should be an object");
    return (Map<String, Object>) value;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> parsePersistedJsonColumn(Object raw) throws Exception {
    assertNotNull(raw, "persisted json column should not be null");
    String json = raw instanceof byte[] bytes ? new String(bytes) : String.valueOf(raw);
    var node = objectMapper.readTree(json);
    if (node.isObject()) {
      return objectMapper.convertValue(node, Map.class);
    }
    if (node.isTextual()) {
      return objectMapper.readValue(node.asText(), Map.class);
    }
    return Map.of();
  }
}
