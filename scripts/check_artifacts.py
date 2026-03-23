#!/usr/bin/env python
"""P0 artifact quality gate checker.

Checks:
1. OpenAPI file is parseable and contains required paths/schemas.
2. Mock payload directory contains required files and valid JSON.
3. DB migration scripts follow naming and V/R pairing rules.
"""

from __future__ import annotations

import json
import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import yaml


ROOT_DIR = Path(__file__).resolve().parents[1]
OPENAPI_PATH = ROOT_DIR / "openapi" / "scheduling-v1.yaml"
INTERNAL_OPENAPI_PATH = ROOT_DIR / "openapi" / "scheduling-internal-v1.yaml"
MOCK_DIR = ROOT_DIR / "mock" / "scheduling-v1"
MIGRATION_DIR = ROOT_DIR / "db" / "migration"
PLAN_DIR = ROOT_DIR / "doc" / "plan"
TEMPLATE_DIR = PLAN_DIR / "templates"
TESTDATA_DIR = ROOT_DIR / "testdata"

REQUIRED_PATHS = {
    "/erp/sales-order-lines",
    "/erp/plan-orders",
    "/erp/production-orders",
    "/erp/schedule-controls",
    "/erp/mrp-links",
    "/erp/delivery-progress",
    "/erp/material-availability",
    "/mes/equipments",
    "/mes/process-routes",
    "/mes/reportings",
    "/mes/equipment-process-capabilities",
    "/mes/employee-skills",
    "/mes/shift-calendar",
    "/erp/schedule-results",
    "/erp/schedule-status",
    "/internal/wip-lots",
    "/internal/replan-jobs",
}

REQUIRED_INTERNAL_PATHS = {
    "/internal/order-pool",
    "/internal/schedule-versions",
    "/internal/schedule-versions/{version_no}/tasks",
    "/internal/dispatch-commands",
    "/internal/dispatch-commands/{command_id}/approvals",
    "/internal/schedule-versions/{version_no}/publish",
    "/internal/schedule-versions/{version_no}/rollback",
    "/internal/alerts",
    "/internal/replan-jobs",
    "/internal/audit-logs",
}

REQUIRED_SCHEMAS = {
    "ErrorResponse",
    "BaseListResponse",
    "SalesOrderLine",
    "PlanOrder",
    "ProductionOrder",
    "ScheduleControl",
    "MrpLink",
    "DeliveryProgress",
    "MaterialAvailability",
    "Equipment",
    "ProcessRoute",
    "ReportingFact",
    "EquipmentProcessCapability",
    "EmployeeSkill",
    "ShiftCalendar",
    "SalesOrderLineListResponse",
    "PlanOrderListResponse",
    "ProductionOrderListResponse",
    "ScheduleControlListResponse",
    "MrpLinkListResponse",
    "DeliveryProgressListResponse",
    "MaterialAvailabilityListResponse",
    "EquipmentListResponse",
    "ProcessRouteListResponse",
    "ReportingFactListResponse",
    "EquipmentProcessCapabilityListResponse",
    "EmployeeSkillListResponse",
    "ShiftCalendarListResponse",
    "ScheduleResultsWriteRequest",
    "ScheduleStatusWriteRequest",
    "WipLotEventRequest",
    "ReplanJobRequest",
}

LIST_RESPONSE_SCHEMAS = {
    "SalesOrderLineListResponse": "SalesOrderLine",
    "PlanOrderListResponse": "PlanOrder",
    "ProductionOrderListResponse": "ProductionOrder",
    "ScheduleControlListResponse": "ScheduleControl",
    "MrpLinkListResponse": "MrpLink",
    "DeliveryProgressListResponse": "DeliveryProgress",
    "MaterialAvailabilityListResponse": "MaterialAvailability",
    "EquipmentListResponse": "Equipment",
    "ProcessRouteListResponse": "ProcessRoute",
    "ReportingFactListResponse": "ReportingFact",
    "EquipmentProcessCapabilityListResponse": "EquipmentProcessCapability",
    "EmployeeSkillListResponse": "EmployeeSkill",
    "ShiftCalendarListResponse": "ShiftCalendar",
}

EXPECTED_MOCK_JSON_FILES = {
    "common_error_400.json",
    "erp_delivery-progress_200.json",
    "erp_material-availability_200.json",
    "erp_mrp-links_200.json",
    "erp_plan-orders_200.json",
    "erp_production-orders_200.json",
    "erp_sales-order-lines_200.json",
    "erp_schedule-controls_200.json",
    "erp_schedule-results_200.json",
    "erp_schedule-results_request.json",
    "erp_schedule-status_200.json",
    "erp_schedule-status_request.json",
    "internal_replan-jobs_202.json",
    "internal_replan-jobs_request.json",
    "internal_wip-lots_202.json",
    "internal_wip-lots_request.json",
    "mes_employee-skills_200.json",
    "mes_equipment-process-capabilities_200.json",
    "mes_equipments_200.json",
    "mes_process-routes_200.json",
    "mes_reportings_200.json",
    "mes_shift-calendar_200.json",
}

REQUIRED_PLAN_FILES = {
    "13_K3_MES字段映射签字版_P0.md",
}

REQUIRED_TEMPLATE_FILES = {
    "K3_MES字段映射签字表_P0.csv",
    "K3_MES枚举映射签字表_P0.csv",
    "K3_MES字段映射签署页_P0.md",
    "UAT签署单_P0.md",
    "发布检查清单_P0.md",
    "变更审批单_P0.md",
    "发布包清单_P0.md",
}

REQUIRED_TESTDATA_FILES = {
    "p0_baseline_pack/manifest.json",
    "p0_baseline_pack/expected/schedule_version_summary.json",
    "p0_baseline_pack/expected/key_assertions.md",
    "p0_negative_pack/manifest.json",
    "p0_negative_pack/expected/validation_expectations.json",
    "p0_negative_pack/expected/key_assertions.md",
    "p0_cutover_inprogress_pack/manifest.json",
    "p0_cutover_inprogress_pack/expected/cutover_assertions.md",
}

MIGRATION_PATTERN = re.compile(r"^(?P<prefix>[VR])(?P<ts>\d{12})__(?P<desc>[a-z0-9_]+)\.sql$")


@dataclass
class CheckResult:
    errors: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)

    def error(self, message: str) -> None:
        self.errors.append(message)

    def warn(self, message: str) -> None:
        self.warnings.append(message)


def _get_items_ref(list_schema: dict[str, Any]) -> str:
    all_of = list_schema.get("allOf")
    if not isinstance(all_of, list):
        return ""
    for part in all_of:
        if not isinstance(part, dict):
            continue
        props = part.get("properties")
        if not isinstance(props, dict):
            continue
        items_node = props.get("items")
        if not isinstance(items_node, dict):
            continue
        array_items = items_node.get("items")
        if not isinstance(array_items, dict):
            continue
        ref = array_items.get("$ref")
        if isinstance(ref, str):
            return ref
    return ""


def check_openapi(result: CheckResult) -> None:
    if not OPENAPI_PATH.exists():
        result.error(f"OpenAPI file not found: {OPENAPI_PATH}")
        return

    try:
        spec = yaml.safe_load(OPENAPI_PATH.read_text(encoding="utf-8-sig"))
    except Exception as exc:  # pragma: no cover - defensive path
        result.error(f"OpenAPI parse failed: {exc}")
        return

    if not isinstance(spec, dict):
        result.error("OpenAPI root is not an object.")
        return

    paths = spec.get("paths")
    if not isinstance(paths, dict):
        result.error("OpenAPI 'paths' is missing or invalid.")
        return

    missing_paths = sorted(REQUIRED_PATHS - set(paths.keys()))
    for path in missing_paths:
        result.error(f"Missing OpenAPI path: {path}")

    extra_paths = sorted(set(paths.keys()) - REQUIRED_PATHS)
    for path in extra_paths:
        result.warn(f"Unexpected OpenAPI path (review if intentional): {path}")

    schemas = ((spec.get("components") or {}).get("schemas") or {})
    if not isinstance(schemas, dict):
        result.error("OpenAPI 'components.schemas' is missing or invalid.")
        return

    missing_schemas = sorted(REQUIRED_SCHEMAS - set(schemas.keys()))
    for schema in missing_schemas:
        result.error(f"Missing OpenAPI schema: {schema}")

    for list_schema_name, item_schema_name in LIST_RESPONSE_SCHEMAS.items():
        list_schema = schemas.get(list_schema_name)
        if not isinstance(list_schema, dict):
            continue
        item_ref = _get_items_ref(list_schema)
        expected_ref = f"#/components/schemas/{item_schema_name}"
        if item_ref != expected_ref:
            result.error(
                f"List schema '{list_schema_name}' items ref mismatch: "
                f"expected '{expected_ref}', got '{item_ref or '<empty>'}'"
            )


def check_internal_openapi(result: CheckResult) -> None:
    if not INTERNAL_OPENAPI_PATH.exists():
        result.error(f"Internal OpenAPI file not found: {INTERNAL_OPENAPI_PATH}")
        return

    try:
        spec = yaml.safe_load(INTERNAL_OPENAPI_PATH.read_text(encoding="utf-8-sig"))
    except Exception as exc:  # pragma: no cover - defensive path
        result.error(f"Internal OpenAPI parse failed: {exc}")
        return

    if not isinstance(spec, dict):
        result.error("Internal OpenAPI root is not an object.")
        return

    paths = spec.get("paths")
    if not isinstance(paths, dict):
        result.error("Internal OpenAPI 'paths' is missing or invalid.")
        return

    missing_paths = sorted(REQUIRED_INTERNAL_PATHS - set(paths.keys()))
    for path in missing_paths:
        result.error(f"Missing internal OpenAPI path: {path}")


def check_mock_payloads(result: CheckResult) -> None:
    if not MOCK_DIR.exists():
        result.error(f"Mock directory not found: {MOCK_DIR}")
        return

    json_files = sorted(p.name for p in MOCK_DIR.glob("*.json"))
    json_set = set(json_files)

    missing = sorted(EXPECTED_MOCK_JSON_FILES - json_set)
    for name in missing:
        result.error(f"Missing mock payload file: mock/scheduling-v1/{name}")

    extras = sorted(json_set - EXPECTED_MOCK_JSON_FILES)
    for name in extras:
        result.warn(f"Unexpected mock payload file (review if intentional): mock/scheduling-v1/{name}")

    for name in sorted(json_set):
        path = MOCK_DIR / name
        try:
            json.loads(path.read_text(encoding="utf-8-sig"))
        except Exception as exc:  # pragma: no cover - defensive path
            result.error(f"Invalid JSON: mock/scheduling-v1/{name}: {exc}")


def check_migrations(result: CheckResult) -> None:
    if not MIGRATION_DIR.exists():
        result.error(f"Migration directory not found: {MIGRATION_DIR}")
        return

    sql_files = sorted(p.name for p in MIGRATION_DIR.glob("*.sql"))
    if not sql_files:
        result.error("No SQL migration files found in db/migration.")
        return

    forward: set[tuple[str, str]] = set()
    rollback: set[tuple[str, str]] = set()

    for name in sql_files:
        match = MIGRATION_PATTERN.match(name)
        if not match:
            result.error(
                "Invalid migration filename format: "
                f"db/migration/{name} (expected V|R + 12-digit timestamp + '__desc.sql')"
            )
            continue
        key = (match.group("ts"), match.group("desc"))
        prefix = match.group("prefix")
        if prefix == "V":
            if key in forward:
                result.error(f"Duplicate forward migration key: {key[0]}__{key[1]}")
            forward.add(key)
        else:
            if key in rollback:
                result.error(f"Duplicate rollback migration key: {key[0]}__{key[1]}")
            rollback.add(key)

    missing_rollback = sorted(forward - rollback)
    for ts, desc in missing_rollback:
        result.error(f"Missing rollback migration for: V{ts}__{desc}.sql")

    missing_forward = sorted(rollback - forward)
    for ts, desc in missing_forward:
        result.error(f"Missing forward migration for: R{ts}__{desc}.sql")

    required_pairs = {
        ("202603221400", "p0_core_schema"),
        ("202603221410", "p0_indexes"),
        ("202603221420", "p0_domain_and_fact_schema"),
    }
    for key in sorted(required_pairs):
        if key not in forward:
            result.error(f"Required forward migration missing: V{key[0]}__{key[1]}.sql")
        if key not in rollback:
            result.error(f"Required rollback migration missing: R{key[0]}__{key[1]}.sql")


def check_plan_and_testdata(result: CheckResult) -> None:
    for name in sorted(REQUIRED_PLAN_FILES):
        path = PLAN_DIR / name
        if not path.exists():
            result.error(f"Missing plan file: doc/plan/{name}")

    for name in sorted(REQUIRED_TEMPLATE_FILES):
        path = TEMPLATE_DIR / name
        if not path.exists():
            result.error(f"Missing plan template file: doc/plan/templates/{name}")

    for rel_path in sorted(REQUIRED_TESTDATA_FILES):
        path = TESTDATA_DIR / rel_path
        if not path.exists():
            result.error(f"Missing testdata artifact: testdata/{rel_path}")


def main() -> int:
    result = CheckResult()

    check_openapi(result)
    check_internal_openapi(result)
    check_mock_payloads(result)
    check_migrations(result)
    check_plan_and_testdata(result)

    print("P0 artifact check finished.")
    print(f"Errors: {len(result.errors)}  Warnings: {len(result.warnings)}")

    if result.errors:
        print("\n[ERRORS]")
        for msg in result.errors:
            print(f"- {msg}")

    if result.warnings:
        print("\n[WARNINGS]")
        for msg in result.warnings:
            print(f"- {msg}")

    return 1 if result.errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
