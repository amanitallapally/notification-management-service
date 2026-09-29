# Active Context

## Current state
Initial prototype complete: all functional requirements implemented, 22/22
automated tests passing, memory bank and README documentation complete.

## Recent focus
- Fixed a Spring AOP self-invocation bug where `@Transactional` on
  `DeliveryOrchestrator.process` was silently skipped because it was called
  from `processAsync` on the same bean. Resolved by extracting the
  transactional core into `DeliveryAttemptExecutor` (see
  `decisions.md#D9`, `systemPatterns.md`).

## Open questions / follow-ups for a real reviewer
- Confirm whether idempotency keys should ever be caller-supplied across
  source systems (current assumption: yes, `sourceSystem:eventId` is only a
  fallback).
- Confirm the CRITICAL-severity escalation-overrides-preference behavior
  matches real notification-ops expectations, or whether escalation should
  instead *add* channels rather than *replace* the preference filter.
- Sections 4.6-4.8 are missing from the source assignment PDF; flag to the
  reviewer in case a version with the missing pages exists and additional
  requirements need to be retrofitted.
