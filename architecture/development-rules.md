Development Rules

- Reuse existing card factories.
- Reuse existing mini cards.
- Do not create duplicate navigation systems.
- Follow existing DAO/service/controller patterns.
- Use service ports rather than bypassing architecture.
- Preserve tenant isolation.
- Prefer extending existing components rather than creating new ones.
- Unless specifically requested, use existing card designs.
## Logging Rules

- Normal desktop application runs default to `INFO` for the root logger, Shale packages, and HikariCP.
- Enable diagnostic Shale logging explicitly with `-DSHALE_LOG_LEVEL=DEBUG`; enable Hikari diagnostics only when needed with `-DSHALE_HIKARI_LOG_LEVEL=DEBUG`.
- Use `TRACE` only for narrow troubleshooting of a specific logger/package, and sanitize any payload before logging it.
- Logs must never include credentials, tokens, signed URLs, Azure Function keys, authorization headers, database credentials, PHI, raw sensitive payloads, or unnecessary user identity details such as raw email addresses.
- Performance logging thresholds are: under 1,000 ms at `DEBUG`, 1,000-1,999 ms at `INFO`, 2,000 ms or greater at `WARN`, and failed operations at `ERROR` with the cause retained.
- New production diagnostics should use the established logger abstraction (`SLF4J` where available) instead of direct `System.out` or `System.err` printing. Keep direct console output only for intentional CLI/test-harness/user-facing output.
