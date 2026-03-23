#!/usr/bin/env python
"""Proxy to repository-level artifact checker.

Keeps the execution entrypoint available under code/scripts for MVP docs.
"""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path


def main() -> int:
    code_root = Path(__file__).resolve().parents[1]
    repo_root = code_root.parent
    target = repo_root / "scripts" / "check_artifacts.py"
    if not target.exists():
        print(f"[ERROR] Missing root artifact checker: {target}")
        return 1
    cmd = [sys.executable, str(target)]
    return subprocess.call(cmd, cwd=str(repo_root))


if __name__ == "__main__":
    raise SystemExit(main())
