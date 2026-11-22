# Backend operational tools

Run normal commands from the repository root. Windows uses `.bat` launchers; macOS/Linux uses the matching `.sh` launchers where supplied.

| Command | Purpose |
| --- | --- |
| `run-python-web` | Main browser application; `--build` prepares/builds and exits |
| `setup-tex` | Provision/check the backend TeX runtime |
| `setup-google-auth.bat` | Configure/check private Google OAuth settings |
| `ensure-python.ps1`, `python-runtime.ps1` | Python discovery/setup used by Windows launchers |
| `setup-installed-python-web.ps1` | Prepare an environment from an installed Python |
| `migrate-mongodb.bat` | Explicit database migration operation |
| `publish-shared-projects.bat` | Explicit publication of existing project files to shared storage |
| `run-python-desktop`, `run-python-console`, `run-python-server` | Compatibility desktop/console/TCP entrypoints |

`maintenance/` contains optional resource-generation and template-specific TeX tools, not everyday startup commands. Windows launcher regression tests are in `../tests/test_windows_python_setup.py`. Migration/publication commands can change persistent data; follow their documentation and back up first.

From the repository root, start with `apps\backend\tools\run-python-web.bat --build`, then `apps\backend\tools\run-python-web.bat`. On macOS/Linux use `sh apps/backend/tools/run-python-web.sh`. The former root `scripts/` directory has been removed; use these paths. Shell launchers are platform adapters, not disposable test scripts.
