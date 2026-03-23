# Test Data Packs (P0)

## Required packs
- `p0_baseline_pack.zip`: normal end-to-end scheduling scenarios.
- `p0_negative_pack.zip`: invalid/edge/error scenarios.
- `p0_cutover_inprogress_pack.zip`: in-progress orders for cutover initialization.

Current repository baseline directories:
- `testdata/p0_baseline_pack/`
- `testdata/p0_negative_pack/`
- `testdata/p0_cutover_inprogress_pack/`

## Suggested pack structure
```
{pack_name}.zip
  manifest.json
  source/
    erp/
      sales_order_line.csv
      production_order.csv
      schedule_control.csv
      mrp_result_link.csv
      delivery_progress.csv
    mes/
      equipments.csv
      process_route.csv
      employee_skill.csv
      reporting_fact.csv
      shift_calendar.csv
  expected/
    schedule_version_summary.json
    key_assertions.md
```

## Manifest minimum fields
- `pack_name`
- `pack_version`
- `created_at`
- `owner`
- `seed`
- `scenario_tags`
- `notes`

## Validation
1. Validate ERP source extracts with `scripts/validate_erp_extract.py`.
2. Validate contract and mock assets with `scripts/check_artifacts.py`.
3. Keep pack version and validation report in release evidence.
