package com.autoproduction.mvp.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.autoproduction.mvp.module.integration.erp.ErpDataManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service

abstract class MvpStoreDispatchDomain extends MvpStoreScheduleLoadDomain {
  protected MvpStoreDispatchDomain(ErpDataManager erpDataManager) {
    super(erpDataManager);
  }

  public Map<String, Object> retryOutboxMessage(String messageId, String requestId, String operator) {
    synchronized (lock) {
      return runIdempotent(requestId, "RETRY_OUTBOX#" + messageId, () -> {
        Map<String, Object> message = state.integrationOutbox.stream()
          .filter(row -> Objects.equals(row.get("message_id"), messageId))
          .findFirst()
          .orElseThrow(() -> notFound("Outbox message %s not found.".formatted(messageId)));
        int retryCount = (int) number(message, "retry_count", 0d) + 1;
        message.put("retry_count", retryCount);
        message.put("status", "SUCCESS");
        message.put("error_msg", "");
        message.put("updated_at", OffsetDateTime.now(ZoneOffset.UTC).toString());
        appendAudit("INTEGRATION_OUTBOX", messageId, "RETRY_OUTBOX", operator, requestId, null);
        return Map.of(
          "request_id", requestId,
          "success", true,
          "message", "Outbox message %s retried.".formatted(messageId)
        );
      });
    }
  }

  public Map<String, Object> createDispatchCommand(Map<String, Object> payload, String requestId, String operator) {
    synchronized (lock) {
      return runIdempotent(requestId, "CREATE_DISPATCH", () -> {
        String commandType = string(payload, "command_type", null);
        String targetOrderNo = string(payload, "target_order_no", null);
        if (commandType == null || targetOrderNo == null) {
          throw badRequest("dispatch command payload is invalid.");
        }
        String commandId = "CMD-%05d".formatted(dispatchSeq.incrementAndGet());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("command_id", commandId);
        row.put("command_type", commandType);
        row.put("target_order_no", targetOrderNo);
        row.put("target_order_type", string(payload, "target_order_type", "production"));
        row.put("effective_time", string(payload, "effective_time", OffsetDateTime.now(ZoneOffset.UTC).toString()));
        row.put("reason", string(payload, "reason", ""));
        row.put("created_by", string(payload, "created_by", operator));
        row.put("approved_flag", 0);
        row.put("created_at", OffsetDateTime.now(ZoneOffset.UTC).toString());
        state.dispatchCommands.add(row);
        appendAudit("DISPATCH_COMMAND", commandId, "CREATE_DISPATCH_COMMAND", operator, requestId, string(payload, "reason", null));
        return Map.of("request_id", requestId, "accepted", true, "message", "Dispatch command accepted.", "command_id", commandId);
      });
    }
  }

  public Map<String, Object> approveDispatchCommand(String commandId, Map<String, Object> payload, String requestId, String operator) {
    synchronized (lock) {
      return runIdempotent(requestId, "APPROVE_DISPATCH#" + commandId, () -> {
        Map<String, Object> command = state.dispatchCommands.stream()
          .filter(row -> Objects.equals(row.get("command_id"), commandId))
          .findFirst()
          .orElseThrow(() -> notFound("Dispatch command %s not found.".formatted(commandId)));
        String decision = string(payload, "decision", null);
        if (!"APPROVED".equals(decision) && !"REJECTED".equals(decision)) {
          throw badRequest("decision must be APPROVED or REJECTED.");
        }
        state.dispatchApprovals.add(Map.of(
          "approval_id", "APP-%05d".formatted(dispatchApprovalSeq.incrementAndGet()),
          "command_id", commandId,
          "approver", string(payload, "approver", operator),
          "decision", decision,
          "decision_reason", string(payload, "decision_reason", ""),
          "decision_time", string(payload, "decision_time", OffsetDateTime.now(ZoneOffset.UTC).toString()),
          "request_id", requestId
        ));
        if ("APPROVED".equals(decision)) {
          command.put("approved_flag", 1);
          applyDispatchCommand(command);
        }
        appendAudit(
          "DISPATCH_COMMAND",
          commandId,
          "APPROVED".equals(decision) ? "APPROVE_DISPATCH_COMMAND" : "REJECT_DISPATCH_COMMAND",
          operator,
          requestId,
          string(payload, "decision_reason", null)
        );
        return Map.of("request_id", requestId, "success", true, "message", "Dispatch command %s.".formatted(decision.toLowerCase()));
      });
    }
  }

  public List<Map<String, Object>> listDispatchCommands(Map<String, String> filters) {
    synchronized (lock) {
      return state.dispatchCommands.stream()
        .filter(row -> filters == null || !filters.containsKey("command_type")
          || Objects.equals(filters.get("command_type"), row.get("command_type")))
        .map(this::localizeRow)
        .toList();
    }
  }

}