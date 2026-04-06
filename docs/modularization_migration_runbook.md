# Shale Modularization Migration Runbook (Operator Guide)

**Last updated:** 2026-04-06  
**Scope:** Statuses, PartyRoles, PartySides, Priorities, PracticeAreas modularization support migrations.  
**Non-goals in this runbook:** destructive cleanup, fallback removal, constraint tightening.

---

## 1) Purpose

This runbook defines a **safe, repeatable execution order** for modularization migrations and provides verification checkpoints between steps.

Use this runbook with:

- `docs/sql/2026-04-06_modularization_gating_checks.sql` (read-only diagnostics)
- `docs/modularization_release_checklist.md` (pass/fail checklist artifact)
- Existing migration scripts under `docs/sql/`

---

## 2) Migration inventory and classification

### A. Statuses

1. `2026-04-02_statuses_lifecycle_key_phase1.sql`  
   - **Type:** prep-only / additive  
   - **Purpose:** add/backfill `Statuses.LifecycleKey`.

2. `2026-04-03_statuses_shaleclientid_nullable_phase2.sql`  
   - **Type:** activation prerequisite  
   - **Purpose:** allow `Statuses.ShaleClientId` to be `NULL` for global rows.

3. `2026-04-03_statuses_system_key_phase1.sql`  
   - **Type:** activation-capable  
   - **Purpose:** add/backfill `Statuses.SystemKey`; seed global rows (`ShaleClientId IS NULL`) after nullable prerequisite.

### B. PartyRoles

4. `2026-04-06_partyroles_system_key_phase1.sql`  
   - **Type:** prep + conditional activation  
   - **Purpose:** add/backfill `PartyRoles.SystemKey`; seed global rows only if nullable is already allowed.

5. `2026-04-06_partyroles_global_activation_phase2.sql`  
   - **Type:** activation step (PartyRoles-specific)  
   - **Purpose:** make `PartyRoles.ShaleClientId` nullable if needed and seed explicit global built-ins (`caller`, `party`, `counsel`) while retaining tenant rows.

### C. PartySides

6. `2026-04-06_partysides_system_key_phase1.sql`  
   - **Type:** prep + conditional activation  
   - **Purpose:** ensure table/column exists, backfill built-ins, seed tenant 7 built-ins if needed, conditionally seed global rows.

7. `2026-04-06_partysides_global_activation_phase2.sql`  
   - **Type:** activation step (PartySides-specific)  
   - **Purpose:** make `PartySides.ShaleClientId` nullable if needed and seed explicit global built-ins (`represented`, `opposing`, `neutral`) while retaining tenant rows.

### D. Priorities

8. `2026-04-06_priorities_system_key_phase1.sql`  
   - **Type:** prep + conditional activation  
   - **Purpose:** add/backfill `Priorities.SystemKey` (`normal` semantics), conditionally seed global row.

9. `2026-04-06_priorities_global_activation_phase3.sql`
   - **Type:** activation step (Priorities-specific)
   - **Purpose:** make `Priorities.ShaleClientId` nullable if needed and seed explicit global built-ins (`low`, `normal`, `high`) while retaining tenant rows and existing task history.

### E. PracticeAreas

10. `2026-04-06_practiceareas_system_key_phase1.sql`
   - **Type:** prep + conditional activation  
   - **Purpose:** add/normalize `SystemKey`, conservative backfill, conditionally seed global rows.

11. `2026-04-06_practiceareas_system_key_phase2_builtin_mapping.sql`
   - **Type:** prep-only follow-up  
   - **Purpose:** explicit tenant-7 mapping for built-ins without moving tenant rows to global.

### F. Integrity hardening (post-rollout)

12. `2026-04-06_modularized_unique_systemkey_indexes_phase1.sql`
   - **Type:** post-rollout hardening / activation safety  
   - **Purpose:** add filtered unique indexes on `(ShaleClientId, SystemKey)` where `SystemKey IS NOT NULL` for modularized tables, with fail-fast duplicate prechecks.

---

## 3) Recommended execution order

## Phase 0 — Baseline diagnostics (read-only)

1. Run `docs/sql/2026-04-06_modularization_gating_checks.sql` and save output snapshots.
2. Confirm target tenant(s) and capture duplicate-key and missing-key reports.

**Abort criteria:** if diagnostics reveal severe unknown schema drift (missing core tables/columns expected by app), stop and resolve drift first.

---

## Phase 1 — Safe additive prep (pre-rollout safe)

Run in order:

1. `2026-04-02_statuses_lifecycle_key_phase1.sql`
2. `2026-04-06_partyroles_system_key_phase1.sql`
3. `2026-04-06_partysides_system_key_phase1.sql`
4. `2026-04-06_priorities_system_key_phase1.sql`
5. `2026-04-06_practiceareas_system_key_phase1.sql`
6. `2026-04-06_practiceareas_system_key_phase2_builtin_mapping.sql`

Then rerun `2026-04-06_modularization_gating_checks.sql`.

**Expected outcome:**
- `SystemKey` columns present where intended.
- Tenant 7 built-ins populated/mapped.
- Conditional global seeds may or may not appear depending on nullability.

---

## Phase 2 — Global overlay activation prerequisites (careful)

Run:

1. `2026-04-06_partyroles_global_activation_phase2.sql`
2. `2026-04-06_partysides_global_activation_phase2.sql`
3. `2026-04-03_statuses_shaleclientid_nullable_phase2.sql`
4. `2026-04-03_statuses_system_key_phase1.sql`

Then rerun `2026-04-06_modularization_gating_checks.sql`.

**Expected outcome:**
- Statuses now supports explicit global rows (`ShaleClientId IS NULL`).
- PartyRoles now supports explicit global rows (`ShaleClientId IS NULL`) with separate global built-ins (`caller`, `party`, `counsel`).
- PartySides now supports explicit global rows (`ShaleClientId IS NULL`) with separate global built-ins (`represented`, `opposing`, `neutral`).
- Runtime overlay resolves by `SystemKey` with tenant override behavior.

---

## Phase 3 — Post-rollout integrity hardening (optional but recommended)

Run:

1. `2026-04-06_priorities_global_activation_phase3.sql`
2. `2026-04-06_modularized_unique_systemkey_indexes_phase1.sql`

Then rerun `2026-04-06_modularization_gating_checks.sql`.

**Expected outcome:**
- Priorities now supports explicit global rows (`ShaleClientId IS NULL`) with separate global built-ins (`low`, `normal`, `high`) while tenant rows remain active.
- Optional filtered unique indexes present for eligible modularized tables.
- New accidental duplicate keyed rows per scope are blocked at write-time.

---

## 4) Verification checklist after each migration step

After each script:

1. Re-run gating checks SQL and verify:
   - `SystemKey` column existence (target table)
   - `ShaleClientId` nullability state
   - duplicate `(ShaleClientId, SystemKey)` report
   - expected tenant 7 built-in rows report
   - global built-in rows report (where applicable)
2. Capture row counts before/after for affected table.
3. Confirm no FK history rewrites occurred (`Cases.PracticeAreaId`, `CaseStatuses`, etc. should remain stable by Id history).

---

## 5) Rollback / abort notes

These scripts are mostly additive and data-normalizing; they are **not** packaged as fully reversible down-migrations.

If a step fails:

1. Stop sequence immediately.
2. Restore from backup/snapshot if data was partially modified and correctness cannot be proven quickly.
3. Re-run gating checks to identify partial state.
4. Do **not** proceed to later activation steps until preconditions are green.

---

## 6) Explicit warnings (do not do in this pass)

1. **Do not deduplicate tenant/global rows by deleting one side** just because `SystemKey` matches. Overlay needs both until a formal consolidation phase.
2. **Do not remove legacy runtime fallbacks yet** (name/system-key compatibility paths) until all environments are fully migrated and validated.
3. **Do not assume global overlay is active** unless nullability checks are green for that table.
4. **Do not add strict unique constraints/indexes in this pass**; gather diagnostics first and schedule separately.

---

## 7) Pre-rollout vs post-rollout guidance

### Safe before full user rollout

- Baseline diagnostics and all prep/additive scripts.
- Explicit tenant 7 practice area built-in mapping.
- Conditional seeding where schema safely allows it.

### Prefer after all users are on new app version

- Removing compatibility fallbacks.
- Data consolidation/dedup of tenant/global built-ins.
- Constraint/index hardening (`(ShaleClientId, SystemKey)` uniqueness for non-null keys) after collision cleanup.

---

## 8) Operator quick-start

1. Run diagnostics: `2026-04-06_modularization_gating_checks.sql`
2. Execute Phase 1 scripts (in listed order).
3. Re-run diagnostics and review diffs.
4. Execute Phase 2 status activation scripts.
5. Re-run diagnostics and archive final verification outputs.
