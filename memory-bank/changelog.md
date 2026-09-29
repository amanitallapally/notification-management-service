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

## [1.0.2] - GitHub Copilot PR review fixes

Ten defects found by an automated Copilot PR review were fixed; full
rationale in `decisions.md` (D0, D10, D11, D12, and updates to D2/D3).

### Fixed
- **Rejection audit gap**: malformed JSON / invalid enum values (e.g. an
  unknown `severity`) raised `HttpMessageNotReadableException`, which bypassed
  both the `NOTIFICATION_REJECTED` audit event and `rejectionReference` in
  the error response. A dedicated handler now covers this case without
  echoing raw parser details.
- **Idempotency retention bug**: `NotificationEntity.idempotencyKey` had a
  permanent unique DB constraint that made the documented 24h retention
  window ineffective (reuse after expiry hit a constraint violation).
  Removed; enforcement now lives solely in the expiry-aware
  `IdempotencyRecordEntity`.
- **Idempotency reservation atomicity**: the key reservation, notification
  persistence, and routing/queuing now happen in a single transaction
  (`NotificationPersistenceService.submitTransactionally`), so a failure
  partway through no longer strands a reservation pointing at a notification
  that was never fully created.
- **Idempotency cross-tenant collision**: caller-supplied idempotency keys
  are now namespaced by `sourceSystem`, matching the derived-key fallback,
  so two source systems can no longer suppress or leak each other's
  notifications by reusing the same key value.
- **Bounded-executor rejection handling**: submitting a queued delivery to
  the async executor is now wrapped so a full queue doesn't surface as a
  `500`; the row stays `QUEUED` and is recovered by a new stale-queue sweep
  in `RetryScheduler`.
- **Retry scheduler duplicate dispatch**: switched from `processAsync` to
  the synchronous, atomically-claiming `process` method so the same
  still-in-flight row is never resubmitted to the bounded executor on every
  poll tick.
- **Status rollup race**: `NotificationStatusAggregator` now takes a
  pessimistic write lock on the parent notification row
  (`findByIdForUpdate`) so concurrent sibling completions can no longer
  leave the overall status permanently stale.
- **H2 console exposure**: disabled by default (blank-password `sa` account,
  no auth layer); re-enabled only via the new `dev` Spring profile
  (`application-dev.yml`), documented as local-development-only.
- **Blank-string validation**: `sourceSystem` and `eventId` now require
  `@NotBlank` (previously `@NotNull` alone allowed empty/whitespace values,
  which could collapse unrelated requests onto the same derived
  idempotency key) plus length bounds.
- **Dead configuration**: `notification.routing.default-channel-order` is
  now actually read and applied by `RoutingService` as the channel ordering
  used when a recipient has no stored preference, instead of being a
  documented-but-inert property.

### Testing
- Added `malformedJsonWithInvalidEnumIsRejectedAndAudited` and
  `sameExplicitKeyFromDifferentSourceSystemsDoesNotCollide` integration
  tests, and `appliesDefaultChannelOrderWhenNoPreferenceExists` unit test.
  Full suite: 26/26 passing.


### Testing
- 22 automated tests: unit tests for routing, retry backoff math, provider
  failure classification, deduplication; end-to-end MockMvc tests covering
  successful delivery, duplicate suppression, terminal failure, retry-then-
  succeed, and the new WEBHOOK channel.
