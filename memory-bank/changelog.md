# Changelog

All notable changes to this project are documented here.
Format loosely follows [Keep a Changelog](https://keepachangelog.com/).

## [1.0.0] - Initial prototype

### Added (Greenfield)
- Notification submission API (`POST /api/v1/notifications`) with Bean
  Validation, idempotency handling, routing, and asynchronous delivery
  kickoff.
- Notification status API (`GET /api/v1/notifications/{id}`) returning
  overall status, per recipient/channel delivery status, and timestamps.
- Audit history API (`GET /api/v1/notifications/{id}/audit`).
- Channel routing policy (requested channels + recipient preference +
  severity escalation).
- Async delivery pipeline: `ThreadPoolTaskExecutor`-backed orchestrator,
  atomic claim-based dedup at the delivery layer, append-only attempt log.
- Bounded exponential-backoff retry with failure classification (transient,
  permanent rejection, invalid recipient, rate-limited, timeout, auth error).
- Submission-level idempotency/dedup with configurable retention.
- Audit trail for accept/reject, routing decisions, queue/attempt/success/
  failure, retry scheduling and exhaustion.
- Providers: Email, SMS, Push (simulated, deterministic failure injection
  by recipient-id convention for testability).

### Added (Brownfield)
- Extracted shared provider failure-simulation logic into
  `AbstractSimulatedProvider`, removing duplication across Email/SMS/Push.
- Added `WEBHOOK` channel (`WebhookProvider`) with no changes to the
  delivery orchestrator, routing service, or controller.

### Documentation
- `memory-bank/` set (project brief, architecture, decisions, state model,
  scenarios, tech context, progress).
- `README.md` with setup instructions, testing approach, limitations, and
  trade-offs.

### Testing
- 22 automated tests: unit tests for routing, retry backoff math, provider
  failure classification, deduplication; end-to-end MockMvc tests covering
  successful delivery, duplicate suppression, terminal failure, retry-then-
  succeed, and the new WEBHOOK channel.
