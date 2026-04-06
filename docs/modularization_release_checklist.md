# Modularization Release Checklist (Operator Quick Artifact)

Use this checklist with:

- `docs/modularization_migration_runbook.md`
- `docs/sql/2026-04-06_modularization_gating_checks.sql`

This checklist is for **read-only readiness assessment** before rollout and before post-rollout activation/consolidation work.

---

## 1) Pre-rollout checklist (must pass)

1. Run diagnostics SQL and save output snapshot.
2. For each target table (`Statuses`, `PartyRoles`, `PartySides`, `Priorities`, `PracticeAreas`):
   - `TableExists = 1`
   - `HasSystemKey = 1` (after intended prep migrations)
3. Confirm tenant 7 built-in rows appear in readiness summary:
   - Statuses: `intake`, `accepted`, `denied`, `closed`
   - PartyRoles: `caller`, `party`, `counsel`
   - PartySides: `represented`, `opposing`, `neutral`
   - Priorities: `normal`
   - PracticeAreas: `medical_malpractice`, `personal_injury`, `sexual_assault`
4. Confirm no severe unexpected schema drift in diagnostics output.

**Block rollout if any fail.**

---

## 2) Findings expected today (not blockers by themselves)

1. Global built-in rows may be missing for a table when `ShaleClientId` is still NOT NULL.
2. PracticeAreas global rows may be intentionally deferred in current phase.
3. Coexisting tenant/global rows by `SystemKey` can be valid (overlay model).

---

## 3) Post-rollout activation checklist (must pass before activation/consolidation)

1. For any table targeted for global overlay activation:
   - `ShaleClientIdIsNullable = 1`
2. Duplicate-key report by `(ShaleClientId, SystemKey)` is clean for rows intended to be unique by scope.
3. Global built-in rows are present where activation policy requires them.
4. No unresolved readiness-summary hints indicating missing prep prerequisites.

**Block post-rollout activation if any fail.**

---

## 4) Explicit do-not-do reminders

1. Do **not** remove compatibility fallback behavior yet.
2. Do **not** deduplicate tenant/global rows blindly.
3. Do **not** add constraints/indexes as part of this checklist step.

---

## 5) Evidence to archive per release

- Timestamped diagnostics SQL output (before + after migration execution).
- Operator decision note: `ROLL_FORWARD` / `ABORT` with reasons.
- List of migrations executed and execution order.

