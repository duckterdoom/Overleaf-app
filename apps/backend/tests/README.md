# Backend checks

From the repository root, with the backend development extras installed:

```sh
python -m pytest apps/backend/tests -q
```

`test_*.py` files are automated regression tests organized into domain subdirectories:

| Directory | Scope |
| --- | --- |
| `writing/` | IELTS Task 1 & 2 scoring, exam workflows, question taxonomy, guides, annotations, assignments |
| `documents/` | DOCX import/export, Word sync, roundtrip converter, multi-document workspace |
| `collaboration/` | Realtime collaboration, presence, review/comments, shared files, TLS, parity |
| `storage/` | Persistence, MongoDB doubles, Google OAuth, server storage, accounts, sharing |
| `compilation/` | LaTeX compile reuse, recovery, TeX runtime, build performance |
| `system/` | Local web API, workspace isolation, template library, Windows environment bootstrap |

`conftest.py` injects isolated MongoDB doubles and private OAuth configuration, and registers all domain subdirectories in `sys.path`. They do not need production Atlas credentials.

`support/` contains fixture servers used by frontend browser tests and the PDF geometry generator. They are not product entrypoints. The Atlas namespace fixture is explicitly opt-in and requires its guard; do not use it for routine tests.

`manual/` contains seven native-tool checks and benchmarks. Read each file's arguments before running; use a fresh scratch directory. These tools are separate from automatic pytest discovery. For example:

```sh
python apps/backend/tests/manual/check_real_docx_roundtrip.py --help
python apps/backend/tests/manual/benchmark_build_word.py --help
```

Do not delete regression coverage merely to make the directory shorter. Remove a check only when its behavior is obsolete or covered equivalently elsewhere.
