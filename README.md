# Latex

A browser-based workspace combining **Task Writing** for teaching and assessment with **LaTeX Studio** for collaborative documents.

The backend uses **Python / FastAPI / PyMongo**. The frontend uses **Next.js / React / TypeScript**, CodeMirror, PDF.js and SuperDoc. MongoDB is the primary database; TeX compilation and PDF-to-Word conversion run on the backend.

## Requirements

- Python **3.12+** with virtual-environment support.
- Node.js **22.13+** and npm to build the frontend.
- A reachable MongoDB server or Atlas cluster.
- Internet access for initial dependency and TeX downloads.
- Optional **Pandoc** for Word/Markdown imports and legacy text-based Word conversion. Install it on the backend's PATH or set `OVERLEAF_PANDOC`. PDF-to-DOCX export uses the Python converter separately.

A website user only needs a browser. Python, TeX and conversion tools belong on the machine serving the application.

## Local setup

Run commands from the repository root.

### Configure MongoDB

If no local configuration exists, copy the example:

```powershell
Copy-Item config/mongodb.local.example.json config/mongodb.local.json
```

On macOS/Linux use `cp` instead of `Copy-Item`. Edit the local file with your own connection information:

```json
{
  "uri": "mongodb+srv://USERNAME:PASSWORD@YOUR_CLUSTER/",
  "database": "latex"
}
```

Configure the database user and Atlas IP access list for the backend machine. Alternatively, use `LATEX_MONGO_URI` and `LATEX_MONGO_DB`. Do not commit credentials or overwrite an existing local configuration. MongoDB failures do not silently switch to SQLite.

### Build and start

Windows:

```powershell
.\apps\backend\tools\run-python-web.bat --build
.\apps\backend\tools\run-python-web.bat
```

macOS/Linux:

```sh
sh apps/backend/tools/run-python-web.sh --build
sh apps/backend/tools/run-python-web.sh
```

`--build` prepares dependencies and TeX, builds the frontend, then exits. The second command starts **http://127.0.0.1:8000**. Keep the terminal open; Ctrl+C stops the server. Rebuild after frontend changes.

If port 8000 is occupied, add `--port 8001`. Add `--no-browser` to disable automatic browser opening. Set `LATEX_DATA_DIR` or pass `--home` to configure the server working directory. The existing default `~/OverleafDesktopPython` remains for compatibility.

The launcher prepares a managed TeX runtime. Repair/check it with `apps\backend\tools\setup-tex.bat` or `sh apps/backend/tools/setup-tex.sh`. The former root `scripts/` folder has been replaced by `apps/backend/tools/`.

### Optional Google login

Email/password authentication works without Google configuration. For Google sign-in, create a Google Web OAuth client and configure its credentials privately using `config/google.local.example.json` as the starting point for `config/google.local.json`.

For the default local URL, authorize this exact redirect URI:

```text
http://127.0.0.1:8000/api/auth/google/callback
```

Hostname/port changes require a matching Google callback. Google apps in Testing must include developers as test users. Check local configuration with:

```powershell
.\apps\backend\tools\setup-google-auth.bat --check
```

Never place the client secret in frontend code or Git. Registration supports Tutor/Student role selection; access is checked by the backend.

## Main features

| Task Writing | LaTeX Studio |
| --- | --- |
| Courses and student enrollment | Personal and shared document projects |
| Private/public question library | Blank projects, templates and imports |
| IELTS Task and Research assessment types; Report under Research | Multi-file editor, file tree and outline |
| Direct question text and image uploads | pdfLaTeX, XeLaTeX and LuaLaTeX compilation |
| Practice, assignments and timed/password-protected exams | Scrollable PDF preview and internal links |
| Custom assessment categories, weights and total score scales | PDF-to-DOCX export and editable Word preview |
| Draft autosave, sentence feedback and movable Checklist score | Named presence, collaborative editing and sharing |
| Finish score review, publishing and student results | History comparisons, tags, archive and downloads |
| Writing guides and sample answers | Validated PDF export; stale/failed builds are blocked |

Export LaTeX document creates a document project containing `question.tex`, `answer.tex`, `feedback.tex` and an aggregate `main.tex`. Word-to-LaTeX synchronization supports mapped text edits; arbitrary Word layout changes are not a lossless LaTeX round trip.

## Project structure

```text
apps/
  backend/
    src/latex_backend/
      entrypoints/       Executable web/compatibility adapters
      core/              Configuration and application composition
      auth/              Accounts, sessions and Google OAuth
      http/              HTTP routes and middleware
      storage/           MongoDB and shared project files
      collaboration/     Presence and synchronization
      compilation/       TeX setup and build jobs
      documents/         Imports, templates, history and Word sync
      pdf_export/        PDF-to-DOCX conversion engine
      writing/           Courses, questions, guides and assessments
      resources/         Packaged templates, guides and manifests
    tools/               Startup, setup and maintenance commands
    tests/               Automated tests, support fixtures and manual checks
    pyproject.toml       Python package/dependency definitions
    uv.lock              Locked Python dependencies
  frontend/
    app/                 Page composition and styles
    components/          auth, workspace, projects, editor and writing UI
    lib/                 API contracts, hooks and pure helpers
    public/              Static assets
    scripts/             Required build-time worker preparation
    tests/               Unit and browser regression suites
config/                  Examples and ignored local credentials
deploy/                  Deployment-related configuration
docs/AI_Handoff.md       Implementation history and verification notes
design-system/           UI design references
integrations/            Integration provenance
examples/                Sample inputs
target/                  Ignored local runtimes and generated artifacts
```

Python owns business rules, permissions, persistence, compilation and conversion. React owns browser interaction. User documents and credentials must not be committed as source.

## Tests

Using your active backend Python environment:

```sh
python -m pip install -e "apps/backend[web-local,dev]"
python -m pytest apps/backend/tests -q
```

From `apps/frontend`:

```sh
npm ci
npm run typecheck
npm test
npm run build
npm run test:browser -- --list
```

Browser scenarios use fresh scratch directories and isolated test fixtures. Some native conversion checks require Pandoc/TeX. Mocked MongoDB/OAuth tests do not prove live Atlas connectivity or real Google consent on a different machine.

## Generated files and cleanup

`target/`, `.venv/`, `node_modules/`, `.next/`, `out/` and Python/test caches are ignored generated content. **Do not delete all of `target/`: this installation uses it for Python, TeX and Pandoc.** Recovery backups and repaired document fixtures may also be stored there.

The cleanup tool selects known old UI test folders and caches, preserving runtime installations and backups. Preview its list first:

```powershell
.\apps\backend\tools\maintenance\clean-generated.ps1
```

To delete exactly the eligible entries printed by the tool:

```powershell
.\apps\backend\tools\maintenance\clean-generated.ps1 -Apply
```

Keep runtime directories such as `official-python-web-env`, `python-runtimes`, `TinyTeX`, `tex-runtime`, `pandoc` and `uv-python-migration`, along with recovery backups. They do not belong in Git, but removing them can break the current local installation.

## Sharing, deployment and publishing changes

MongoDB stores application data, metadata and shared file manifests/chunks. Each backend uses a local working directory for compilation/conversion. Back up persistent data before migrations; migration/publication tools are explicit operations, not routine startup steps.

The local web launcher intentionally binds to loopback. Public hosting needs HTTPS, an appropriate ASGI/reverse-proxy setup, persistent storage, OAuth configuration and a provisioned TeX runtime. `deploy/python/compose.yaml` describes the compatibility TCP/TLS server, not the main browser application.

Before another developer clones, commit and push all intended files, moves and dependency locks. Untracked files do not arrive in a clone. Never commit environments, generated output or local credentials. Windows Application Control restrictions require administrator approval of the runtime; startup tools do not bypass them.

See [the implementation handoff](docs/AI_Handoff.md), [backend tools](apps/backend/tools/README.md), [frontend tests](apps/frontend/tests/README.md) and [template licensing](LICENSE-TEMPLATES.md) for supporting information.
