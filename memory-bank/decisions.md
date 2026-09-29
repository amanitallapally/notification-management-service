# Decisions Log (ADR-style, condensed)

Each entry: **Decision - Context - Rationale - Trade-off/Alternative considered**.

## D0. Copilot PR review fixes (2026-09-29)
GitHub Copilot's automated PR review found 10 real defects across
correctness, concurrency, security, and validation. All were fixed; each is
cross-referenced from its relevant existing decision below, and net-new
decisions (D10-D12) were added for issues that didn't fit an existing entry.
Summary:
1. Malformed JSON / invalid enum tokens bypassed the audit/rejection path -> `GlobalExceptionHandler` now also handles `HttpMessageNotReadableException`.
2. `NotificationEntity.idempotencyKey` had a permanent unique DB constraint that fought the documented 24h retention window -> constraint removed; enforcement now lives solely in `IdempotencyRecordEntity` (see D3).
3. Idempotency reservation was committed before notification persistence/routing, so a later failure left an orphaned reservation -> both now happen in one transaction (see D3).
4. Submitting to the bounded delivery executor could throw and strand `QUEUED` rows with nothing ever re-scanning them -> `RetryScheduler` now also sweeps stale `QUEUED` rows, and the dispatch loop no longer lets a rejection surface as a 500 (see D2, D11).
5. Caller-supplied idempotency keys weren't namespaced by source system, allowing cross-system collisions/leaks -> all keys are now namespaced (see D3).
6. Concurrent delivery-attempt transactions could race the notification-status rollup and leave it permanently stale -> `NotificationStatusAggregator` now takes a pessimistic write lock on the parent row (see D12).
7. The H2 console was enabled by default with a blank-password `sa` account and no auth layer -> disabled by default, opt-in via a `dev` Spring profile (see D10).
8. `sourceSystem`/`eventId` accepted blank/whitespace-only values, which could collapse to a colliding derived idempotency key -> now `@NotBlank` + length-bounded.
9. `RetryScheduler` resubmitted the same still-in-flight row to the async executor on every poll tick -> now dispatches synchronously via the atomic-claim `process()` method (see D11).
10. `notification.routing.default-channel-order` was documented but never read -> now wired into `RoutingService` as the no-preference fallback ordering.

## D1. Persistence: H2 in-memory via Spring Data JPA
- **Context**: Prototype must run end-to-end with no external setup.
- **Rationale**: Zero-infrastructure `mvn spring-boot:run`; JPA gives a
  realistic relational model that maps 1:1 to a future Postgres schema.
- **Trade-off**: Data does not survive a restart; no real concurrency/tuning
  characteristics of a production database. Swapping the datasource to
  Postgres/MySQL requires only `application.yml` changes + a driver
  dependency - entities/repositories are portable.

## D2. Async delivery: in-process thread pool, not a broker
- **Context**: Requirement calls for asynchronous processing of deliveries.
- **Rationale**: Keeps the submission simple to run/review; Spring's
  `@Async` + `ThreadPoolTaskExecutor` gives real concurrency and a bounded
  queue without external dependencies.
- **Trade-off**: Not durable across a process crash - an item claimed
  (`ATTEMPTING`) but not completed when the JVM dies is not automatically
  resumed by this prototype (see Limitations in the README). A production
  system would use SQS/Kafka/RabbitMQ (or a DB-backed outbox with a
  recovery sweep) so in-flight work survives restarts and scales across
  instances.
- **Bounded-queue rejection recovery (fixed 2026-09-29)**: submitting a
  freshly-queued row to the executor can throw `RejectedExecutionException`
  once the queue is full. `NotificationSubmissionService` now catches this
  per-target instead of letting it surface as a `500` - the row is already
  persisted as `QUEUED`, and `RetryScheduler` sweeps any `QUEUED` row older
  than `notification.retry.stale-queue-threshold-ms` (default 5s) so it
  still gets dispatched, just delayed.

## D3. Deduplication boundary and retention (requirement 4.4)
- **Submission-level boundary**: one `idempotencyKey` maps to exactly one
  logical `NotificationEntity`, enforced entirely by `IdempotencyRecordEntity`
  (an expiry-aware lookup-then-insert in `DeduplicationService`).
  `NotificationEntity.idempotencyKey` itself is **not** a unique DB
  constraint (fixed 2026-09-29): a permanent unique index there fought the
  documented retention window, since after a key expired in
  `idempotency_records` the notifications table would still reject reuse
  with a constraint violation.
- **Atomic reservation (fixed 2026-09-29)**: reserving the idempotency key,
  persisting the notification, and routing/queuing its recipient-channels
  now all happen inside one `@Transactional` method
  (`NotificationPersistenceService.submitTransactionally`). Previously the
  reservation could commit before persistence/routing ran, so a later
  failure left the key permanently mapped to a notification that was never
  fully created - subsequent legitimate retries of that submission would
  have been wrongly suppressed as duplicates of a non-existent record.
- **Source-system namespacing (fixed 2026-09-29)**: both the caller-supplied
  key and the derived fallback are now namespaced as `sourceSystem:<key>` /
  `sourceSystem:eventId`. Previously an explicit caller-supplied key was used
  verbatim, so two independent source systems reusing the same key value
  would suppress each other's notifications and could leak the first
  system's notification id/status to the second.
- **Retention**: idempotency records expire after
  `notification.idempotency.retention-hours` (default 24h). After expiry the
  key may be reused. This bounds storage growth at the cost of only
  deduplicating within that window - a deliberate, documented trade-off
  rather than unbounded retention.
- **Delivery-level boundary** (reprocessing a queued item must not create
  uncontrolled duplicate side effects): `RecipientChannelRepository
  .claimForAttempt` is a single atomic conditional UPDATE
  (`QUEUED`/`FAILED_RETRYABLE` -> `ATTEMPTING`). If two threads (e.g. the
  retry scheduler and a manual replay) race for the same row, only one
  UPDATE affects a row; the loser's provider call never happens.
- **Suppressed duplicates are visible**: a suppressed resubmission emits a
  `NOTIFICATION_DUPLICATE_SUPPRESSED` audit event against the *original*
  notification id, so it is discoverable via the audit API.

## D4. Retry and failure handling (requirement 4.5)
- **Classification**: `FailureType` enum encodes exactly the six categories
  named in the spec (transient, permanent rejection, invalid recipient,
  rate-limited, timeout, auth error) plus `UNKNOWN` for unclassified
  exceptions (fail-safe: treated as retryable so a coding bug in a new
  provider doesn't silently drop a notification).
  the same taxonomy so retry logic never has channel-specific branches.
- **Retryable vs terminal**: only `TRANSIENT_PROVIDER_FAILURE`, `TIMEOUT`,
  `RATE_LIMITED`, and `UNKNOWN` are retried. `PERMANENT_PROVIDER_REJECTION`,
  `INVALID_RECIPIENT`, and `AUTH_ERROR` fail terminally on the first
  attempt - retrying these can never succeed and would waste provider
  quota / mask a real configuration problem.
- **Bounded exponential backoff**: `RetryPolicy` computes
  `initialBackoff * multiplier^(attempt-1)`, capped at `maxBackoffMs`, with a
  configurable `maxAttempts` (default 5, i.e. up to 4 retries). Once
  exhausted, a still-retryable failure moves to `EXHAUSTED` (distinct from
  `FAILED_TERMINAL`) so status/audit can distinguish "gave up after retrying"
  from "never should have retried."

## D5. State model (requirement 4.2 allows a custom model if documented)
See `state-model.md` for the full enums and transition tables. Summary:
notification-level status is a **rollup** of its recipient-channel statuses,
recomputed after every delivery attempt by `NotificationStatusAggregator`,
rather than being independently mutated - this avoids the two models
drifting out of sync.

## D6. Channel routing precedence (requirement 4.3, ambiguous)
The spec lists requested channel, severity, recipient preference, and
"routing policy" as inputs but does not define their precedence. Documented
assumption: requested/eligible channels are a hard ceiling (never route
outside what the caller asked for); recipient preference filters/orders
within that ceiling; CRITICAL severity (configurable) escalates to every
requested channel, overriding preference but not the ceiling. See
`RoutingService` Javadoc and `scenarios.md#ambiguous` for the full reasoning.

## D7. Provider strategy pattern + brownfield refactor
Greenfield already used a `NotificationProvider` interface + `ProviderRegistry`
so channel-specific logic never leaks into the orchestrator. The brownfield
task extracted the shared failure-simulation logic that would otherwise be
copy-pasted per channel into `AbstractSimulatedProvider`, then added
`WebhookProvider` as a genuinely new channel with zero orchestrator changes.
See `scenarios.md#brownfield`.

## D7b. Rejected submissions are audited under a standalone reference id
Requirement 4.9 explicitly lists "Notification accepted / rejected" as an
audit action. Bean-validation failures happen before a `NotificationEntity`
exists (no notification id has been assigned yet), so `GlobalExceptionHandler`
generates a standalone `rej_<uuid>` reference, records a
`NOTIFICATION_REJECTED` audit event against it (field names/validation
messages only - no submitted values), and returns that reference in the
`400` response body as `rejectionReference`. This event is **not**
retrievable via `GET /notifications/{id}/audit` since no notification
record backs it - that endpoint 404s for ids with no persisted
notification. This is a deliberate, documented trade-off: it satisfies the
audit requirement without relaxing NOT NULL constraints on
`NotificationEntity` just to persist a half-formed rejected record.

## D8. Audit content minimization (requirement 4.9)
`AuditEventEntity.details` and `DeliveryAttemptEntity.providerResponseSummary`
are free-text but populated only with identifiers, enum names, and short
computed reasons - never the notification subject/body, recipient contact
address, or any credential/secret. This is enforced by convention (code
review) in this prototype; a production system would add a
serialization-time redaction filter as defense in depth (see Limitations).

## D9. Self-invocation and `@Transactional` / `@Async`
Spring's `@Transactional` and `@Async` are proxy-based - calling an annotated
method from another method *on the same bean* bypasses the proxy silently.
This was hit twice during implementation (submission persistence, delivery
attempt claim) and fixed by moving the annotated logic into a dedicated
collaborator bean (`NotificationPersistenceService`,
`DeliveryAttemptExecutor`) called from a thin orchestrator. Documented here
because it is an easy regression to reintroduce.

## D10. H2 console disabled by default (fixed 2026-09-29)
- **Context**: the H2 console has no authentication layer of its own, and
  the datasource uses a blank-password `sa` account. Leaving it enabled
  unconditionally means any network client that can reach the service can
  browse or mutate the database through `/h2-console`.
- **Fix**: `spring.h2.console.enabled` is now `false` in `application.yml`
  and only re-enabled by `application-dev.yml` (activate with
  `--spring.profiles.active=dev` / `SPRING_PROFILES_ACTIVE=dev`), which is
  documented as local-development-only.
- **Trade-off**: reviewers must remember to add the `dev` profile to inspect
  the database manually; this is intentional friction to prevent the profile
  from being active anywhere it shouldn't be.

## D11. Retry scheduler dispatches synchronously (fixed 2026-09-29)
- **Context**: `RetryScheduler` polls on a fixed interval; because a row
  stays `FAILED_RETRYABLE`/`QUEUED` until a worker actually claims it, an
  item that hasn't been picked up yet by the time the *next* poll tick
  fires was being resubmitted again via `processAsync`, piling up duplicate
  no-op tasks in the bounded delivery executor under backlog and risking
  starving new deliveries.
- **Fix**: the scheduler now calls `DeliveryOrchestrator.process` (the
  synchronous path) directly. The atomic claim in `claimForAttempt` still
  makes redundant calls for an already-claimed row a harmless no-op, but
  running on the scheduler's own thread means the same row is never
  in-flight on two different executor tasks at once.
- **Trade-off**: a large batch of due retries now processes serially on the
  scheduler thread rather than fanning out across the worker pool, so the
  poll loop runs slightly slower under heavy retry volume. Documented and
  accepted as the safer default for a single-node prototype; a production
  system would use per-item visibility timeouts from a real queue instead.
- **Also added**: a stale-`QUEUED` sweep (`findByStatusAndQueuedAtBefore`)
  recovers rows that were persisted but never actually dispatched (see D2).

## D12. Pessimistic locking for the status rollup (fixed 2026-09-29)
- **Context**: `NotificationStatusAggregator.recompute` reads all sibling
  `RecipientChannelEntity` rows and writes a derived overall status. Two
  siblings completing at nearly the same time on separate transactions could
  each read a stale snapshot of the other (still `ATTEMPTING`), both compute
  `IN_PROGRESS`, and both commit - leaving the notification permanently
  stuck even though every child had actually reached a terminal state.
- **Fix**: `NotificationRepository.findByIdForUpdate` takes a
  `PESSIMISTIC_WRITE` lock on the parent notification row before reading its
  children, serializing concurrent recomputes for the same notification id
  so the later transaction always observes the earlier one's committed
  child state.
- **Trade-off**: adds row-lock contention (and, under H2's default lock
  timeout, a small risk of a lock-wait timeout under pathological
  concurrency) in exchange for correctness. Acceptable given delivery
  attempts for one notification are typically low-cardinality (one row per
  recipient/channel).

