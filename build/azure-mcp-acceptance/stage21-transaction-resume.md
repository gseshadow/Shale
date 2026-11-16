# Stage 21 v1 diagnostic procedure: superseded

The v1 CATCH-based instrumentation produced unscoped all-absent records. Those
records cannot identify the historical failing property or establish whether
rollback occurred. `ProofDiagnosticsValid` in v1 meant protocol validity only.

Use [the pre-THROW investigation and current evidence-bound recovery procedure](stage21-prethrow-resume.md)
for the latest failed instrumented attempt. The v1 renderer is retained solely to
validate existing journal/receipt evidence. Current rendering instruments the
original assertion before THROW, tags INSTALL/SEAL/STANDALONE and gates standalone
on validated installer success. Existing journals/selections remain unchanged.
