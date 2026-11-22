# Frontend tests

Run from `apps/frontend` after `npm ci`:

```powershell
npm test
npm run typecheck
npm run test:browser -- --list
npm run test:browser -- ui-redesign ../../target/official-python-web-env/Scripts/python.exe ../../target/ui-audit-fresh
```

- `unit/`: checklist scoring, outline parsing, editor snippets, guide Markdown rendering, history diffs, navigation loading and list filters. `npm test` runs seven suites without external fixtures; PDF coordinate mapping has a separate fixture-dependent command.
- `browser/`: tests against the real app, including auth, sharing, writing, PDF/Word, files and performance scenarios. Each suite keeps its original runtime/fixture requirements. The runner lists available names; it does not claim every legacy scenario is a current release gate.
- `run.mjs`: one entry point that selects a suite and sets the working directory correctly. Browser arguments are forwarded unchanged. Use a fresh isolated test home, never a real account's data directory.

PDF geometry requires a four-page geometry fixture: `npm run test:pdf-geometry -- <fixture.pdf>`. Browser suites may require Edge, Python development extras, Mongo mocks or an explicitly supplied test factory, and a TeX runtime for actual compilation. No production Atlas credentials are needed for `ui-redesign`, `auth-role-selection` or `question-library`.

Tests formerly lived in `scripts/test-*.mjs`; they now live here. Tests targeting the retired static prototype were removed with that prototype, not replaced by passing stubs. Functional tests were retained. Build utilities stay in `../scripts/`.

Component source paths follow the feature folders in `../components/`: `auth`, `workspace`, `projects`, `editor` and `writing`. Backend fixture servers are in `../../backend/tests/support/`; native manual checks are in `../../backend/tests/manual/`.
