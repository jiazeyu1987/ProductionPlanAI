#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Validate ERP extract files for scheduling system integration.

Usage examples:
  python scripts/validate_erp_extract.py --input D:\\erp_export.xlsx
  python scripts/validate_erp_extract.py --input D:\\erp_export_dir --report-json D:\\report.json
  python scripts/validate_erp_extract.py --generate-template D:\\erp_extract_template.xlsx
"""

from __future__ import annotations

import argparse
import json
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Tuple

import pandas as pd


@dataclass
class DatasetSpec:
    required_cols: List[str]
    recommended_cols: List[str] = field(default_factory=list)
    key_cols: List[str] = field(default_factory=list)
    date_cols: List[str] = field(default_factory=list)
    numeric_cols: List[str] = field(default_factory=list)
    binary_cols: List[str] = field(default_factory=list)


SPECS: Dict[str, DatasetSpec] = {
    "sales_order_line": DatasetSpec(
        required_cols=[
            "sales_order_no",
            "line_no",
            "product_code",
            "order_qty",
            "order_date",
            "expected_due_date",
            "requested_ship_date",
            "urgent_flag",
            "order_status",
            "last_update_time",
        ],
        recommended_cols=[
            "customer_code",
            "customer_name",
            "product_name",
            "spec_model",
            "uom",
            "priority_code",
        ],
        key_cols=["sales_order_no", "line_no"],
        date_cols=["order_date", "expected_due_date", "requested_ship_date", "last_update_time"],
        numeric_cols=["order_qty"],
        binary_cols=["urgent_flag"],
    ),
    "production_order": DatasetSpec(
        required_cols=[
            "production_order_no",
            "source_sales_order_no",
            "source_line_no",
            "product_code",
            "plan_qty",
            "plan_start_date",
            "plan_finish_date",
            "production_status",
            "last_update_time",
        ],
        recommended_cols=["workshop_code", "line_code"],
        key_cols=["production_order_no"],
        date_cols=["plan_start_date", "plan_finish_date", "last_update_time"],
        numeric_cols=["plan_qty"],
    ),
    "schedule_control": DatasetSpec(
        required_cols=[
            "order_no",
            "order_type",
            "review_passed_flag",
            "promised_due_date",
            "frozen_flag",
            "schedulable_flag",
            "last_update_time",
        ],
        recommended_cols=["review_status", "scheduler_note"],
        key_cols=["order_no", "order_type"],
        date_cols=["promised_due_date", "last_update_time"],
        binary_cols=["review_passed_flag", "frozen_flag", "schedulable_flag"],
    ),
    "mrp_result_link": DatasetSpec(
        required_cols=[
            "order_no",
            "order_type",
            "mrp_run_id",
            "run_time",
        ],
        recommended_cols=["purchase_req_no", "outsource_req_no", "make_order_no"],
        key_cols=["order_no", "order_type", "mrp_run_id"],
        date_cols=["run_time"],
    ),
    "delivery_progress": DatasetSpec(
        required_cols=[
            "order_no",
            "order_type",
            "warehoused_qty",
            "shipped_qty",
            "delivery_status",
            "last_update_time",
        ],
        recommended_cols=["inspection_status", "warehousing_status"],
        key_cols=["order_no", "order_type"],
        numeric_cols=["warehoused_qty", "shipped_qty"],
        date_cols=["last_update_time"],
    ),
}


def _is_blank(v: Any) -> bool:
    if pd.isna(v):
        return True
    if isinstance(v, str) and v.strip() == "":
        return True
    return False


def _norm(v: Any) -> str:
    if pd.isna(v):
        return ""
    return str(v).strip()


def _to_bool_token(v: Any) -> str:
    if pd.isna(v):
        return ""
    s = str(v).strip().lower()
    if s in {"1", "true", "yes", "y", "t"}:
        return "1"
    if s in {"0", "false", "no", "n", "f"}:
        return "0"
    return s


def _load_from_xlsx(path: Path) -> Dict[str, pd.DataFrame]:
    book = pd.ExcelFile(path)
    out: Dict[str, pd.DataFrame] = {}
    for name in SPECS.keys():
        if name in book.sheet_names:
            out[name] = book.parse(name)
    return out


def _load_from_dir(path: Path) -> Dict[str, pd.DataFrame]:
    out: Dict[str, pd.DataFrame] = {}
    for name in SPECS.keys():
        csv_path = path / f"{name}.csv"
        xlsx_path = path / f"{name}.xlsx"
        if csv_path.exists():
            out[name] = pd.read_csv(csv_path, dtype=object)
        elif xlsx_path.exists():
            out[name] = pd.read_excel(xlsx_path, dtype=object)
    return out


def load_input(path: Path) -> Dict[str, pd.DataFrame]:
    if path.is_file() and path.suffix.lower() in {".xlsx", ".xls"}:
        return _load_from_xlsx(path)
    if path.is_dir():
        return _load_from_dir(path)
    raise FileNotFoundError(f"Input not found or unsupported: {path}")


def new_issue(level: str, dataset: str, rule: str, count: int, samples: List[Dict[str, Any]]) -> Dict[str, Any]:
    return {
        "level": level,
        "dataset": dataset,
        "rule": rule,
        "count": int(count),
        "samples": samples,
    }


def sample_rows(df: pd.DataFrame, n: int = 5) -> List[Dict[str, Any]]:
    if df.empty:
        return []
    rows = df.head(n).to_dict(orient="records")
    # JSON serializable cleanup
    cleaned: List[Dict[str, Any]] = []
    for r in rows:
        c = {}
        for k, v in r.items():
            if pd.isna(v):
                c[k] = None
            elif hasattr(v, "isoformat"):
                try:
                    c[k] = v.isoformat()
                except Exception:
                    c[k] = str(v)
            else:
                c[k] = v
        cleaned.append(c)
    return cleaned


def validate_dataset(name: str, df: pd.DataFrame, spec: DatasetSpec) -> Tuple[List[Dict[str, Any]], Dict[str, Any]]:
    issues: List[Dict[str, Any]] = []
    stats = {"rows": int(len(df)), "errors": 0, "warnings": 0}

    if len(df) == 0:
        issues.append(new_issue("warning", name, "dataset_has_zero_rows", 0, []))
        stats["warnings"] += 1

    # required columns
    missing_required = [c for c in spec.required_cols if c not in df.columns]
    if missing_required:
        issues.append(
            new_issue(
                "error",
                name,
                "missing_required_columns",
                len(missing_required),
                [{"column": c} for c in missing_required],
            )
        )
        stats["errors"] += 1
        return issues, stats

    # recommended columns
    missing_recommended = [c for c in spec.recommended_cols if c not in df.columns]
    if missing_recommended:
        issues.append(
            new_issue(
                "warning",
                name,
                "missing_recommended_columns",
                len(missing_recommended),
                [{"column": c} for c in missing_recommended],
            )
        )
        stats["warnings"] += 1

    # empty required values
    for c in spec.required_cols:
        bad = df[df[c].map(_is_blank)]
        if not bad.empty:
            issues.append(new_issue("error", name, f"blank_required:{c}", len(bad), sample_rows(bad[[c]])))
            stats["errors"] += 1

    # key duplicates
    if spec.key_cols and all(c in df.columns for c in spec.key_cols):
        dup = df[df.duplicated(subset=spec.key_cols, keep=False)]
        if not dup.empty:
            issues.append(new_issue("error", name, f"duplicate_key:{','.join(spec.key_cols)}", len(dup), sample_rows(dup[spec.key_cols])))
            stats["errors"] += 1

    # date parse
    for c in spec.date_cols:
        if c not in df.columns:
            continue
        parsed = pd.to_datetime(df[c], errors="coerce")
        bad_mask = (~df[c].map(_is_blank)) & parsed.isna()
        bad = df[bad_mask]
        if not bad.empty:
            issues.append(new_issue("error", name, f"invalid_datetime:{c}", len(bad), sample_rows(bad[[c]])))
            stats["errors"] += 1

    # numeric parse
    for c in spec.numeric_cols:
        if c not in df.columns:
            continue
        parsed = pd.to_numeric(df[c], errors="coerce")
        bad_mask = (~df[c].map(_is_blank)) & parsed.isna()
        bad = df[bad_mask]
        if not bad.empty:
            issues.append(new_issue("error", name, f"invalid_numeric:{c}", len(bad), sample_rows(bad[[c]])))
            stats["errors"] += 1

    # binary parse
    for c in spec.binary_cols:
        if c not in df.columns:
            continue
        bad = df[~df[c].map(_is_blank) & ~df[c].map(lambda v: _to_bool_token(v) in {"0", "1"})]
        if not bad.empty:
            issues.append(new_issue("error", name, f"invalid_binary:{c}", len(bad), sample_rows(bad[[c]])))
            stats["errors"] += 1

    return issues, stats


def validate_cross(data: Dict[str, pd.DataFrame]) -> List[Dict[str, Any]]:
    issues: List[Dict[str, Any]] = []

    sales = data.get("sales_order_line")
    prod = data.get("production_order")
    ctrl = data.get("schedule_control")
    mrp = data.get("mrp_result_link")
    dlv = data.get("delivery_progress")

    # production order -> source sales order referential integrity
    if sales is not None and prod is not None:
        needed_sales = {"sales_order_no", "line_no"}
        needed_prod = {"source_sales_order_no", "source_line_no"}
        if needed_sales.issubset(sales.columns) and needed_prod.issubset(prod.columns):
            sales_key = set(zip(sales["sales_order_no"].map(_norm), sales["line_no"].map(_norm)))
            missing_rows = prod[
                ~prod.apply(
                    lambda r: ( _norm(r["source_sales_order_no"]), _norm(r["source_line_no"]) ) in sales_key,
                    axis=1,
                )
            ]
            if not missing_rows.empty:
                issues.append(
                    new_issue(
                        "error",
                        "production_order",
                        "source_sales_order_not_found",
                        len(missing_rows),
                        sample_rows(missing_rows[["production_order_no", "source_sales_order_no", "source_line_no"]]),
                    )
                )

    # build order sets
    sales_orders = set()
    prod_orders = set()
    if sales is not None and "sales_order_no" in sales.columns:
        sales_orders = set(sales["sales_order_no"].map(_norm))
    if prod is not None and "production_order_no" in prod.columns:
        prod_orders = set(prod["production_order_no"].map(_norm))

    def check_order_ref(ds_name: str, df: pd.DataFrame | None) -> None:
        if df is None:
            return
        if "order_no" not in df.columns or "order_type" not in df.columns:
            return

        def exists(row: pd.Series) -> bool:
            otype = _norm(row["order_type"]).lower()
            ono = _norm(row["order_no"])
            if otype in {"sales", "sales_order"}:
                return ono in sales_orders
            if otype in {"production", "production_order", "mo"}:
                return ono in prod_orders
            return False

        bad = df[~df.apply(exists, axis=1)]
        if not bad.empty:
            issues.append(new_issue("error", ds_name, "order_reference_not_found", len(bad), sample_rows(bad[["order_no", "order_type"]])))

    check_order_ref("schedule_control", ctrl)
    check_order_ref("mrp_result_link", mrp)
    check_order_ref("delivery_progress", dlv)

    # schedule_control logic checks
    if ctrl is not None and {"review_passed_flag", "promised_due_date"}.issubset(ctrl.columns):
        passed = ctrl["review_passed_flag"].map(lambda v: _to_bool_token(v) == "1")
        bad = ctrl[passed & ctrl["promised_due_date"].map(_is_blank)]
        if not bad.empty:
            issues.append(
                new_issue(
                    "error",
                    "schedule_control",
                    "review_passed_but_no_promised_due_date",
                    len(bad),
                    sample_rows(bad[["order_no", "order_type", "review_passed_flag", "promised_due_date"]]),
                )
            )

    return issues


def summarize(issues: List[Dict[str, Any]]) -> Dict[str, int]:
    out = {"errors": 0, "warnings": 0}
    for i in issues:
        if i["level"] == "error":
            out["errors"] += 1
        elif i["level"] == "warning":
            out["warnings"] += 1
    return out


def generate_template(path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with pd.ExcelWriter(path, engine="openpyxl") as writer:
        for name, spec in SPECS.items():
            cols = spec.required_cols + [c for c in spec.recommended_cols if c not in spec.required_cols]
            pd.DataFrame(columns=cols).to_excel(writer, sheet_name=name, index=False)


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate ERP extract data for scheduling system.")
    parser.add_argument("--input", type=str, help="ERP extract path (.xlsx/.xls or a directory containing dataset csv/xlsx files)")
    parser.add_argument("--report-json", type=str, default="doc/plan/erp_extract_validation_report.json", help="Validation report output path")
    parser.add_argument("--generate-template", type=str, help="Generate a template Excel file and exit")
    args = parser.parse_args()

    if args.generate_template:
        template_path = Path(args.generate_template)
        generate_template(template_path)
        print(f"Template generated: {template_path}")
        return 0

    if not args.input:
        parser.error("--input is required unless --generate-template is used.")

    input_path = Path(args.input)
    data = load_input(input_path)

    issues: List[Dict[str, Any]] = []
    dataset_stats: Dict[str, Dict[str, Any]] = {}

    # missing datasets
    for name in SPECS.keys():
        if name not in data:
            issues.append(new_issue("error", name, "dataset_missing", 1, [{"dataset": name}]))
            dataset_stats[name] = {"rows": 0, "errors": 1, "warnings": 0}

    # validate each dataset
    for name, df in data.items():
        spec = SPECS.get(name)
        if not spec:
            continue
        local_issues, stats = validate_dataset(name, df, spec)
        issues.extend(local_issues)
        dataset_stats[name] = stats

    # cross-dataset checks
    issues.extend(validate_cross(data))

    summary = summarize(issues)
    report = {
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "input_path": str(input_path),
        "summary": {
            "datasets_expected": len(SPECS),
            "datasets_found": len(data),
            "errors": summary["errors"],
            "warnings": summary["warnings"],
        },
        "dataset_stats": dataset_stats,
        "issues": issues,
    }

    report_path = Path(args.report_json)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    print("Validation finished.")
    print(f"Input: {input_path}")
    print(f"Report: {report_path}")
    print(f"Errors: {summary['errors']}  Warnings: {summary['warnings']}")

    return 1 if summary["errors"] > 0 else 0


if __name__ == "__main__":
    raise SystemExit(main())
