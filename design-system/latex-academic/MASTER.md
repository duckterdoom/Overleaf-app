# Latex Academic Workspace — interface system

Scope: all application surfaces, Teacher/Student, light/dark, desktop/mobile. Date: 2026-11-16.

The ui-ux-pro-max university/SaaS search matched Swiss Modernism 2.0, institutional navy, blue research accents, modular grids and strong hierarchy. The initial education query suggested children's claymorphism; rejected for this academic product. The search's marketing portfolio layout and all-serif typography are not appropriate for dense authoring; adapt its grid/hierarchy to an application dashboard, with locally available system sans-serif UI and document typography kept separate.

## Visual contract

- Canvas #F3F5FA; card #FFFFFF; ink #17233B; secondary text #52627B; line #DDE4EF.
- Primary/action #3454D1 on white; navy navigation #15233D with #DCE5F5 text.
- Writing #7147C6, editor #2563EB, courses #A16207, positive #117B68, warning #A16207, destructive #B42335. Colored surfaces are pale and text remains dark; status always has text/icons too.
- Dark canvas #101827, card #1B273B, text #E8EDF7; use brighter accents and independent readable muted text. White document/PDF pages remain document content.
- System sans-serif UI, 14–15px normal, 12px labels, 28–38px page titles; readable text contrast, 1.5 body line height. Source/editor fonts and document layout are not restyled globally.
- 4/8px rhythm, 12–16px card corners, restrained borders/shadows, 160ms hover/focus, no animated layout shifts. Visible keyboard rings and reduced-motion support.
- Product navigation separates Dashboard, Courses, Task Writing, LaTeX Studio. Document tools are not added back to Writing; editor retains its three existing tools.
- Dashboard counts and recent items come from actual authorized data; no fabricated charts, learner counts, deadlines, or activity.

## Surface coverage and verification

Authentication/role selection/profile/settings; dashboard/projects/templates/import/upload; Writing overview/courses/questions/forms/rubrics/practice/exams/submissions/assessment/results; LaTeX source/file tree/outline/toolbars/logs/PDF/Word; sharing/comments/chat/history; empty/loading/error states. Shared tokens cover existing components without duplicating business logic. New dashboard is a presentational component with a single cancellable overview fetch.

Verify application build, existing functional browser workflows, Teacher/Student, account selection, light/dark, 375/768/1024/1440 widths, landscape, reduced motion, larger text and horizontal overflow. Record screenshots and honest coverage/limits in docs/UI_REDESIGN.md and AI_Handoff.md. No backend schema, role rules, converter/compiler, collaboration protocol, new dependency, or live database change is authorized by this visual refresh.


Navigation refinement (2026-11-17): one shared 64px identity header, brand as Home button, no global module tabs. Two equal home portals open Task Writing and LaTeX Studio. Courses stay within Writing; document library/create live in LaTeX Studio. Project tools use a separate context row. Full-width outer dashboard owns scrolling; centered content never owns an inset scrollbar.


Shared-navigation refinement (2026-11-18): Dashboard, Task Writing and LaTeX Studio now share one role-aware sidebar. Dashboard combines learning/assessment and recent documents; LaTeX All projects / Shared with you / Template library live in a blue-accent group beneath violet-accent Task Writing. Library search/list controls remain in content, without duplicate module tabs. Mobile uses the same grouped sidebar as a horizontally scrollable strip.
