# Phase 13G Windows logged-out automatic-update architecture reevaluation

**Decision on 2026-10-01: `UNSUPPORTED`. Phase 13G is complete; no prototype or production executor was created.**

Phase 13F installed acceptance closed authoritative registration discovery: the 64-bit HKLM record was unique,
schema 1, owner/root exact, ACL-protected, and classified `VALID` by the production reader. Installed schema-1
`1.0.129` / `PRODUCTION` metadata and the updater were present. Repair and the `1.0.128` to `1.0.129` major upgrade
preserved the UUID; exact uninstall removed it. This evidence does not turn an owner-writable updater into privileged
execution authority. Production Authenticode is intentionally NOT RUN for unsigned developer artifacts and remains a
separate release prerequisite.

## Candidate comparison

| Model | Discovery and principal | Credentials/network | Per-user update and privileged trust | Result |
| --- | --- | --- | --- | --- |
| Owner task, interactive token | Resolve each protected `VALID` UUID; run as its SID | No stored credential, but requires an interactive logon | Correct owner context; cannot cover logged-out case | Reject; Phase 13C already covers the safer session case |
| Owner task, password logon | Resolve one registered owner/install per task; run as owner | Task Scheduler retains credentials | Could have owner/network access, but violates the no-raw/stored-credential invariant | Reject |
| Owner task, S4U | Resolve one registered owner/install per task; noninteractive owner token | No password; network/EFS limitations | LocalAppData/TLS/update behavior is unproved and cannot satisfy the existing network-dependent flow | Reject |
| SYSTEM task | Enumerate protected registrations; independently arbitrate each UUID | No credentials; machine network identity | Can reach LocalAppData, public policy, and HKLM, but current updater/install are owner-writable; verify/launch has a substitution race and SYSTEM writes change the security boundary | Reject current implementation |
| Protected broker/helper/service | Installer-owned SYSTEM component with bounded commands and one validated UUID per operation | No user credential required | Potentially feasible only with protected staging, publisher/version verification, reparse-safe handles, hardened IPC/task ACL, and transactional lifecycle | Defer to a separately approved privileged-component project |

Do not assume SYSTEM is correct. Expected-publisher Authenticode is necessary for any elevated/SYSTEM path, but it is
not sufficient: publisher trust does not prevent swapping after path validation, executing an old vulnerable signed
binary, or redirecting an owner-writable destination. No ordinary-user-selected path or broad writable ProgramData
location may become authority.

## Reassessed execution contract

* Enumerate protected registrations, never profiles. Zero, duplicate, invalid, stale, owner-mismatched, or
  reparse-unsafe records defer. Multiple valid owners are independent; never choose the first one.
* Read only public authoritative policy without a Shale bearer. Offline/unavailable/invalid policy defers; no cached
  or manifest-only privileged fallback is allowed.
* Use protected registration for owner SID, install root, support root, updater, version metadata, Phase 12 store,
  evidence log, and Phase 13B lock. Never use executor `%LOCALAPPDATA%` or guess a profile.
* Policy selects the target; the manifest, SHA-256, archive validation, and expected publisher authenticate package
  inputs. Owner-writable installed metadata is descriptive, not privileged authority.
* Enumerate the exact registered `Shale.exe` across all Windows sessions. Any match or enumeration uncertainty,
  including locked/disconnected sessions, defers. Never force-close or attempt cross-session cooperative shutdown.
* Acquire the existing per-owner update lock before handoff. Only a real updater handoff creates Phase 12 state under
  that owner's support root; SYSTEM's profile is never used.
* Missing consent, policy/package failure, no update, Shale running, lock busy, ambiguous registration, insufficient
  privilege, or tampering exits without an attempt and uses bounded daily retry/backoff—no tight loop.
* Never wake, reboot, display UAC, store passwords/tokens, or put tenant/user/session/JTI/PHI in registration/task
  definitions. One owner's uninstall or failure must not touch another owner's state.

Phase 13B idle timestamps exist only in a running process. Logged-out architecture therefore cannot infer safety from
elapsed clock time; closed Shale plus all-session absence is only a prerequisite, not permission to bypass policy,
lock, signature, path, or package checks. Phase 13C behavior is unchanged.

## Remaining gates before support can change

An operator must first approve ownership of a new privileged Windows component. A successor must then prove, on real
Windows systems, all of the following before changing `LoggedOutAutomaticUpdateSupport`:

1. protected task/broker binaries outside all owner-writable roots, with strict task/service/file ACLs;
2. expected-publisher, timestamp, version/rollback, and package validation at a race-free execution boundary;
3. canonical and reparse-safe source/destination validation using handles, not path strings alone;
4. deterministic all-session process detection that fails closed and never kills Shale;
5. owner-correct lock, attempt/result, logs, file ownership, and ACL behavior under the chosen principal;
6. independent multi-owner scheduling/arbitration with no arbitrary-first-install behavior;
7. public-policy outage and offline deferral, plus bounded retry with no wake/reboot;
8. MSI-owned idempotent creation, upgrade preservation, rollback, tamper recovery, and exact uninstall cleanup;
9. signed-production acceptance for task/broker/updater/package and adversarial swap, downgrade, ACL, and reparse tests;
10. locked, disconnected, owner-logged-out, other-user-logged-in, no-user, running-app, lock-contention, and later
    Phase 12 reconciliation acceptance.

There are no Phase 13G Windows acceptance commands because no unsafe prototype was produced. The applicable check is
that no `\Shale\` task or new service/helper exists and `LoggedOutAutomaticUpdateSupport.current()` remains
`UNSUPPORTED`. Production signing must still be validated separately with the Phase 13F runbook before any release.

## Security and audit result

This phase adds no task, service, helper, credential, API, SQL, schema, audit event, telemetry, force-kill, updater
redesign, or macOS behavior. It reads or mutates no tenant/domain data; Phase 12 remains the bounded operational
record only after real handoff. Therefore no new audit event or migration is appropriate.

**Next recommended phase:** Phase 13H, a privileged Windows component go/no-go and threat model, only if the operator
accepts that long-lived security-product ownership. Otherwise stop this roadmap branch and retain Phase 13C.
