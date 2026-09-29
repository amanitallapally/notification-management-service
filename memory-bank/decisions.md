# Decisions Log (ADR-style, condensed)

Each entry: **Decision - Context - Rationale - Trade-off/Alternative considered**.

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

## D3. Deduplication boundary and retention (requirement 4.4)
- **Submission-level boundary**: one `idempotencyKey` maps to exactly one
  logical `NotificationEntity`. Enforced by a unique DB constraint plus an
  explicit `IdempotencyRecordRepository` lookup-then-insert
  (`DeduplicationService`), so races are caught by the DB constraint, not
  just application logic.
- **Key derivation**: if the caller does not supply `idempotencyKey`, one is
  derived as `sourceSystem:eventId`. This is a documented assumption for the
  ambiguous-requirement scenario: the spec requires idempotency but does not
  mandate the key be caller-supplied.
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
