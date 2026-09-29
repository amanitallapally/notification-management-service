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

## [1.0.1] - Requirement gap fixes after PDF re-verification

### Fixed
- `GET /notifications/{id}` now returns a top-level `selectedChannels` field
  (distinct channels across all recipients), explicitly required by 4.2
  and previously only recoverable by scanning `recipientChannels`.
- Submission rejections (bean-validation failures, e.g. missing required
  fields) now record a `NOTIFICATION_REJECTED` audit event under a
  standalone reference id returned as `rejectionReference` in the `400`
  response body - previously `AuditAction.NOTIFICATION_REJECTED` existed but
  was never actually recorded, silently failing part of requirement 4.9.
  See `decisions.md#D7b`.


### Testing
- 22 automated tests: unit tests for routing, retry backoff math, provider
  failure classification, deduplication; end-to-end MockMvc tests covering
  successful delivery, duplicate suppression, terminal failure, retry-then-
  succeed, and the new WEBHOOK channel.
