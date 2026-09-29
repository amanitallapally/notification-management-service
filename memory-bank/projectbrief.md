# Project Brief

## What this is
A prototype **Notification Management Service** built in Java 17 / Spring Boot 3,
developed as an AI-assisted software engineering assignment. It receives
notification requests from upstream systems, routes them to one or more
recipients over one or more channels (Email, SMS, Push, Webhook), delivers
them asynchronously with bounded retry, and exposes status/audit APIs.

## Why it exists
Demonstrate engineer-led, AI-accelerated execution across three scenarios:
1. **Greenfield** - build the initial capability from scratch.
2. **Brownfield** - extend/refactor the existing system (new channel +
   provider-logic refactor) without breaking existing behavior.
3. **Ambiguous requirement** - make and document defensible judgment calls
   where the spec was silent or contradictory.

## Source requirements
See `memory-bank/scenarios.md` and `memory-bank/decisions.md` for how the
functional requirements (submission, status, routing, dedup/idempotency,
retry/failure handling, audit history) were interpreted and implemented.
Note: the source assignment PDF is itself missing requirement sections
4.6-4.8 (a gap in the source document, confirmed by page-by-page extraction,
not a transcription error). Everything referenced from 4.1-4.5 and 4.9 is
implemented.

## Top-level goals
- Runnable end-to-end with zero external infrastructure (H2 in-memory DB,
  in-process async pipeline).
- Modular, testable, defensible design - each requirement maps to a
  small number of clearly-named classes.
- Every non-obvious decision is written down, not just implemented.
