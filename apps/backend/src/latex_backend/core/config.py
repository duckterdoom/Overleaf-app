"""Operator-controlled paths shared by launchers and backend services."""
import os
from pathlib import Path

PACKAGE_ROOT = Path(__file__).resolve().parents[1]
REPO = PACKAGE_ROOT.parents[3]
RESOURCES = PACKAGE_ROOT / "resources"


def data_directory() -> Path:
    # Retain the former default to avoid silently losing existing projects.
    return Path(
        os.getenv("LATEX_DATA_DIR")
        or os.getenv("OVERLEAF_WEB_HOME")
        or os.getenv("OVERLEAF_PYTHON_HOME")
        or "~/OverleafDesktopPython"
    ).expanduser()
