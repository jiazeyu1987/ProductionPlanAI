# Ops Artifacts (P0)

## Purpose
This directory contains executable ops artifacts aligned with:
- `doc/develop/17_运维阈值与事件响应SLA_P0.md`
- `doc/develop/09_上线回滚与运行手册.md`

## Files
- `alerts/p0_alert_rules.yaml`: threshold and routing baseline for P0.
- `dashboards/p0_dashboard_spec.json`: dashboard widget and metric mapping baseline.

## Usage
1. Map metrics in these files to your monitoring platform metric names.
2. Import alert rules and verify notification channels.
3. Build dashboard from spec and validate threshold coloring.
4. Run a monthly drill and keep artifacts in release package.

## Notes
- Timezone baseline is `Asia/Shanghai`.
- Threshold values must stay consistent with `doc/develop/17`.
