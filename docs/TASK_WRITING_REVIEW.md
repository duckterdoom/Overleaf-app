# Task Writing review and requirements

Date: 2026-11-13. Replaces the earlier requirement to hide classroom management.

## Review findings

- Required — Writing entry depended on a LaTeX project and the assignment form required a source attachment. Allow direct questions and an independent Task Writing entry, with LaTeX as optional import/export.
- Required — New assignments copied a project-wide catalog, with no assignment editor. Preserve a private rubric snapshot per question/assignment, editable before learners start.
- Required — No reusable question library with ownership/visibility. Public questions are reusable by authenticated tutors; private questions remain owner-only. Public here means the application's tutor community; no faculty directory exists yet.
- Required — Course membership was hidden, and project-wide visibility bypassed membership. Restore explicit Course/Group membership and enforce it in list, detail and asset access.
- Required — Partial writing had no explicit scope. Support whole, introduction, body and conclusion; partial exercises use their selected checklist, without reporting a full IELTS band.
- Required — Mixed UI language. Use English interface copy; learner answers, tutor questions and document sources retain their original language.
- Existing changes — api.py/service.py contained unfinished writingScope/purpose/rubric work, including an import of missing questions.py. Complete those changes without resetting the working tree.

## Acceptance

1. Home explains Task Writing, paper development and LaTeX publishing; Task Writing opens without creating a document project.
2. Tutor creates a question directly or imports a project source, chooses writing scope and edits its rubric independently.
3. Private/public question CRUD, ownership and revisions; another tutor reuses public content without editing its owner.
4. Course/Group CRUD, explicit student membership/join; published practice available to enrolled students, homework/exams assigned to a course/group. Existing project-shared assignments remain supported.
5. Learner writes only the assigned section or whole paper; Tutor reviews immutable submission alongside checklist. Checklist percentage for partial work, IELTS bands only for whole IELTS tasks.
6. Existing timed/password exams, project ACL, snapshots and LaTeX/PDF exports continue to work. No real database migration or automatic project creation for routine writing.

## Verification

Results and remaining limitations are recorded in AI_Handoff.md after implementation.

Production Next.js/TypeScript build passed. Backend suite passed 266 tests; targeted assessment regressions and a private/public-guard mutation check passed. The Edge browser workflow uses isolated mock MongoDB and verifies course enrollment, private/public questions, exercise-specific criteria, sectioned learner writing, checklist publishing, mobile layout, an actual compiled PDF report, and importing a compiled LaTeX source into independent Writing. Artifacts: `target/task-writing-browser-20261113-03/report.json` and screenshots/PDF in that directory. No live database migration or user configuration change was performed.

## API and data ownership

- `GET/POST /api/writing/questions`, `PUT/DELETE /api/writing/questions/{id}`: author-owned, revision checked, paginated 50 per page; private or public to authenticated tutors. Only selected rubric profiles are stored. Attachments are copied on reuse so deleting the source question does not break delivered exercises.
- `POST /api/writing/courses`, `PUT/DELETE /api/writing/courses/{id}`, `POST /api/writing/courses/{id}/members`, `DELETE /api/writing/courses/{id}/members/{student}`; join through existing `classes/join`. Overview returns courses/groups. Empty courses can be deleted; courses with active exercises are retained for access to work. In project scope, editable Tutor collaborators can manage courses without transferring configuration to a different account.
- Assignment fields: `writingScope=whole|intro|body|conclusion`, `purpose=practice|homework|exam`, `rubricCatalog` snapshot, `classId` for a course/group. Existing `kind=assignment|exam` remains the timed-exam discriminator. Submitted/started exercises cannot silently change questions or criteria.
- `POST /api/writing/prompt-import`: optional `sourceProjectId` for independent Writing, checked against source project ACL. Copies an immutable PDF/image/text question into the independent writing scope. This does not enroll students into the source project.
- Draft `blocks` keeps ordered headings/text alongside canonical answers. Server rejects mismatched section text, freezes sections on submission and emits escaped LaTeX subsections. No student-authored LaTeX is executed in the tutor's view.
- Existing project-scoped records retain their document ACL. New courses use `enrollment=explicit`; old implicit project classes retain legacy access, preventing a breaking migration. Account-level courses use tutor ownership/enrollment.
- Collections: existing `writing_classes`, `writing_memberships`, `writing_assignments`, `writing_drafts`, `writing_submissions`, plus `writing_questions`. No new database or external library is needed.

## Scoring boundaries

For partial exercises: met=1, partial=0.5, missing=0, not-applicable excluded; percentage is the mean over unique evaluated criteria. Repeated criteria use the weakest rating. Unreviewed selected criteria block publishing. This is a teaching checklist percentage, not an IELTS band. Whole IELTS tasks preserve existing four-code band assessment; full Report/Research retain feedback without bands.

## Current limits

Public library access is application-wide for tutors, not faculty-scoped. Faculty directories, moderation and organizational tenancy need a separate identity model. Course/group management currently uses flat groups, not a semester/department hierarchy. Whole-text and sectioned views share one canonical answer; editing whole text clears the prior section organization. Existing user-authored questions/criteria in Vietnamese are not translated or overwritten. The current Windows-local verification does not certify Internet production security or multi-worker presence.

## Review outcome

- Correctness: tested private/public ownership, revision conflicts, course membership, immutable per-exercise rubrics, canonical section text, partial scoring and existing password exams. Final backend run: 266 passed.
- Readability/architecture: reused the existing Writing service and editor toolchain; extracted question ownership, enrollment and rubric policies into focused files. No new product module or dependency.
- Security: source-project ACL before import; attachment access through a permitted question or assigned exercise; public reuse copies bytes; learner answers remain private; forged criterion IDs rejected. Inverting the private/public guard caused the regression test to fail, then the original guard was restored.
- Performance: only selected rubric profiles are snapshotted, question library is paginated/indexed, and course rosters use batched user queries. No production load benchmark is claimed.
- Local verification outcome: ready to test with the launcher; existing user data and configuration were not migrated or reset. No commit, merge or deployment performed.
