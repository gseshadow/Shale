# Reusable field confirmation

## Phase 2A: persistence and read contract (2026-09-23)

Phase 2A adds only schema and read models. It does not enable a policy, create a pending queue,
confirm a value, change a Case Date write, add UI controls, badges, or notifications. Existing Case
Dates receive a positive `ValueRevision` for future concurrency identity but receive no confirmation
requirement row; reads therefore report `NOT_REQUIRED`, not `PENDING`.

`IsRequired` remains the form's input-presence rule. `RequiresConfirmation` is an independent,
versioned `FieldConfirmationPolicies` rule. Policies use stable
`(ShaleClientId, FormKey, FieldKey)` identities registered by `FormFieldPolicyKeys`, rather than the
generated IDs replaced by `FormConfigurationDao.replace`. A current policy may select any active,
nondeleted same-tenant `FirmWideRoleDefinitions` row. Case Team definitions and assignments are not
part of policy or eligibility resolution.

Policy rows are immutable versions. A saved value enters the workflow by receiving a
`SavedValueConfirmationRequirements` snapshot of policy ID/revision, stable form/field keys, and the
required firm-wide role. Later policy changes do not update that snapshot or a
`SavedValueConfirmations` fact. Thus a confirmed value stays confirmed, while only a newly created or
subsequently business-edited value uses the then-current policy.

The reusable requirement is attached through a typed table. Phase 2A permits only `CASE_DATE` and
`CaseDateConfirmationTargets` has composite same-tenant foreign keys to both the requirement and
Case Date. More target types can be added later as their own constrained typed tables. For Case
Dates, the confirmed business value consists precisely of `CaseDateTypeId`, `StartsAt`, `EndsAt`,
and `AllDay`. `Title`, `Notes`, deletion/restoration metadata, audit timestamps/actors, and SQL
`RowVer` are not constituents. `CaseDates.ValueRevision` is the business revision and must not be
inferred from `RowVer`.

SOL and TCN applicability must be resolved through the protected `STATUTE_OF_LIMITATIONS` and
`TORT_NOTICE_DEADLINE` rows in `CaseDateTypeSemanticRoleMappings`, including tenant-effective
mapping rules. Labels, numeric type IDs, and the New Intake controller are not semantic authority.
The Phase 2B writer must perform that resolution inside every Case Date create/edit transaction, no
matter which application entry point invoked it.

Reads join only a requirement targeting the Case Date's current `ValueRevision`: no row is
`NOT_REQUIRED`, a requirement without a confirmation is `PENDING`, and a requirement with its
immutable fact is `CONFIRMED`. The saved date remains authoritative and visible in all states.
Confirmation status/role metadata is not PHI and adds no read-audit event; existing Case Date reads
retain their established sensitive-read treatment. Phase 2A exposes no mutation, so it adds no
entity-action vocabulary. Phase 2B must audit meaningful policy and confirmation mutations on their
owning transaction without serializing dates, DTOs, row versions, or other sensitive values.

## Migration order and next phase

1. Apply the reconciled `2026-09-23_firm_wide_roles_foundation_phase1a.sql` if it is not deployed,
   then run its unchanged, read-only all-tenant verification.
2. Apply `2026-09-23_firm_wide_roles_audit_allowlist_phase1b.sql`, then run its read-only verification.
3. Apply `2026-09-23_field_confirmation_foundation_phase2a.sql` manually.
4. Run `2026-09-23_field_confirmation_foundation_phase2a_verify.sql` unchanged on the approved admin
   connection with null application session context.
5. Deploy the Phase 2A read-contract application binaries.

The precise next write-integration phase is **Phase 2B: transactional policy administration and
all-path Case Date enrollment/revision/confirmation writes**. It must maintain stable field-key
registration during form replacement, validate active firm-wide roles and actor eligibility,
resolve protected SOL/TCN semantics, increment `ValueRevision` only for the four defined business
properties, snapshot the current policy on new/edited values, and atomically append allowlisted
entity-action audit events. It must not backfill existing dates.
