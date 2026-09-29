# Notification Management Service

A scalable notification management service for receiving, processing and
delivering notifications through configurable channels (Email, SMS, Push,
Webhook). Built in Java 17 / Spring Boot 3 as a working prototype for an
AI-assisted software engineering assignment - see `memory-bank/` for the
full architecture, decisions, and scenario write-ups.

> **Assignment deliverables map**
> - Working prototype: this repository (`mvn spring-boot:run` and go).
> - Architecture overview: [memory-bank/architecture.md](memory-bank/architecture.md)
> - Three scenarios (greenfield/brownfield/ambiguous): [memory-bank/scenarios.md](memory-bank/scenarios.md)
> - Setup instructions, testing approach, limitations, trade-offs: this file
> - Decision log: [memory-bank/decisions.md](memory-bank/decisions.md)
> - State model: [memory-bank/state-model.md](memory-bank/state-model.md)
> - Changelog: [memory-bank/changelog.md](memory-bank/changelog.md)

## 1. Prerequisites

- Java 17 or newer (JDK 21 was used for development). Verify with `java -version`.
- No local Maven install required - the Maven Wrapper (`mvnw`) is committed.
- No database, message broker, or other external service needed - the
  service uses an in-memory H2 database and an in-process async pipeline
  (see [Limitations & trade-offs](#5-limitations--trade-offs)).

## 2. Engineering scenarios (greenfield / brownfield / ambiguous)

The assignment requires three engineering scenarios, each showing
decomposition, execution, and validation. Full write-ups are in
[memory-bank/scenarios.md](memory-bank/scenarios.md); the summary below maps
each required sub-item directly to the code and tests that satisfy it.

### 2.1 Greenfield - initial notification-management capability

| Sub-requirement | Implementation | Test evidence |
|---|---|---|
| Notification submission | `NotificationController.submit` -> `NotificationSubmissionService` | `submitAndDeliverSucceeds`, `invalidSubmissionIsRejectedAndAudited` |
| Recipient and channel selection | `RoutingService.resolveChannels` (requested channel + severity + preference) | `RoutingServiceTest` (4 cases) |
| Asynchronous processing | `DeliveryOrchestrator` (`@Async`) + `deliveryExecutor` thread pool | Submission returns `202 Accepted` immediately; delivery runs on worker threads |
| Delivery attempts | `DeliveryAttemptExecutor` + append-only `DeliveryAttemptEntity` | `flakyRecipientRetriesThenSucceeds` (asserts 3 attempts) |
| Status retrieval | `GET /notifications/{id}` -> `NotificationStatusService` | `submitAndDeliverSucceeds` polls status to `DELIVERED` |

### 2.2 Brownfield - enhancement spanning multiple layers

The assignment names three example change types; this prototype implements
two of them together as one coupled, multi-layer change:

- **Refactor provider-specific logic**: `EmailProvider`/`SmsProvider`/
  `PushProvider` each duplicated failure-simulation logic -> extracted into
  `AbstractSimulatedProvider`. Behavior-preserving (existing provider tests
  kept passing unmodified).
- **A new notification channel**: `ChannelType.WEBHOOK` + `WebhookProvider`,
  with **zero changes** to `ProviderRegistry`, `DeliveryAttemptExecutor`, or
  `RoutingService` - proof the greenfield strategy-pattern decoupling paid
  off. Validated by `webhookChannelDeliversSuccessfully`.

The third example ("deduplication") was deliberately built during
**greenfield** instead, since requirement 4.4 makes it a hard *must* for the
initial capability rather than an optional enhancement - called out
explicitly so it isn't mistaken for a missed brownfield item.

### 2.3 Ambiguous requirement scenario

Demonstrated via a table of concrete ambiguities - each with a documented,
defensible resolution rather than an arbitrary guess - in
[memory-bank/scenarios.md](memory-bank/scenarios.md#ambiguous-requirement-scenario):
idempotency-key derivation, channel-routing precedence, state-model shape,
dedup retention value, audit sensitive-content boundary, and
expiration-enforcement scope. Also documented: the source assignment PDF
itself is missing sections 4.6-4.8 (confirmed by page-by-page extraction),
handled by implementing only what's explicitly specified rather than
inventing scope.

## 3. Setup & running

```bash
# From the repository root
./mvnw clean install        # compiles, runs all tests, packages the jar
./mvnw spring-boot:run       # starts the service on http://localhost:8080
```

On Windows use `mvnw.cmd` instead of `./mvnw`.

Once started:
- API base path: `http://localhost:8080/api/v1/notifications`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Actuator health: `http://localhost:8080/actuator/health`

The H2 console is **disabled by default** (it has no auth layer of its own,
and the datasource uses a blank-password `sa` account - see
[Limitations & trade-offs](#5-limitations--trade-offs)). To inspect the
database locally, run with the `dev` profile instead:
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```
Then open `http://localhost:8080/h2-console` (JDBC URL
`jdbc:h2:mem:notificationdb`, user `sa`, empty password). Never activate the
`dev` profile in a shared or deployed environment.

### Try it (curl walkthrough)

Submit a notification:

```bash
curl -s -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "sourceSystem": "trading-alerts",
    "eventId": "evt-1001",
    "notificationType": "ALERT",
    "severity": "HIGH",
    "priority": "HIGH",
    "recipients": ["user-1"],
    "requestedChannels": ["EMAIL"],
    "subject": "Trade breached threshold"
  }' | jq
```

Response (`202 Accepted`):
```json
{ "notificationId": "ntf_...", "status": "ROUTED", "duplicate": false }
```

Poll status until delivered:
```bash
curl -s http://localhost:8080/api/v1/notifications/<notificationId> | jq
```

Fetch audit history:
```bash
curl -s http://localhost:8080/api/v1/notifications/<notificationId>/audit | jq
```

Resubmit the same request again -> returns the same `notificationId` with
`"duplicate": true` and no new delivery side effects (requirement 4.4).

### Exercising specific behaviors on demand

The simulated providers use **recipient-id naming conventions** so any
reviewer can exercise every failure path without reading the code or
waiting for real randomness:

| Recipient id contains... | Simulated outcome | Retried? |
|---|---|---|
| (anything else) | Success on first attempt | n/a |
| `flaky` | Fails transiently for 2 attempts, then succeeds | yes |
| `ratelimit` | Rate-limited failure | yes, until exhausted |
| `timeout` | Timeout failure | yes, until exhausted |
| `invalid` | Invalid recipient | no (terminal immediately) |
| `authfail` | Auth/authorization error | no (terminal immediately) |
| `reject` | Permanent provider rejection | no (terminal immediately) |

Example - watch a bounded retry sequence then success:
```bash
curl -s -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{"sourceSystem":"ops","eventId":"evt-2","notificationType":"SYSTEM","severity":"MEDIUM","priority":"NORMAL","recipients":["flaky-user"],"requestedChannels":["SMS"]}'
```

## 4. Testing approach

Run the full suite:
```bash
./mvnw test
```
26 tests, all passing, split into two layers:

**Unit tests** (fast, no Spring context):
- `RoutingServiceTest` - requested-channel ceiling, preference filtering/
  ordering, fallback when preference doesn't intersect the request,
  CRITICAL-severity escalation, and default-channel-order fallback ordering.
- `RetryPolicyTest` - exhaustion boundary and exponential backoff math,
  including the backoff cap.
- `EmailProviderTest` - every `FailureType` classification plus the flaky
  transient-then-success path, exercised via the shared
  `AbstractSimulatedProvider` base.
- `DeduplicationServiceTest` - first submission reserves the key; a repeat
  submission returns the original notification id.

**End-to-end tests** (`NotificationControllerIntegrationTest`, full Spring
context + MockMvc + real in-memory DB + real async executor):
- `submitAndDeliverSucceeds` - full happy path to `DELIVERED`, plus an audit
  trail assertion.
- `invalidSubmissionIsRejectedAndAudited` - missing-field validation failure
  is audited with a `rejectionReference`.
- `malformedJsonWithInvalidEnumIsRejectedAndAudited` - unreadable request
  body (invalid enum literal) is also audited, without leaking parser
  internals in the response.
- `duplicateSubmissionWithSameIdempotencyKeyReturnsOriginalId` - dedup
  boundary through the real HTTP layer.
- `sameExplicitKeyFromDifferentSourceSystemsDoesNotCollide` - source-system
  namespacing prevents cross-tenant idempotency-key collisions.
- `invalidRecipientFailsTerminallyWithoutRetry` - terminal failure
  classification, asserts exactly one attempt (no wasted retries).
- `flakyRecipientRetriesThenSucceeds` - exercises the retry scheduler
  end-to-end, asserting the final attempt count.
- `webhookChannelDeliversSuccessfully` - validates the brownfield channel
  addition through the real pipeline.

Async assertions use **Awaitility** (`await().atMost(...).untilAsserted(...)`)
rather than fixed `Thread.sleep`, so tests are both fast and not flaky under
load. Retry-related tests override `notification.retry.*` properties via
`@TestPropertySource` to use short backoffs, keeping the suite fast
(full run completes in well under 30 seconds).

### What is intentionally not tested here
- Load/performance testing (no SLA was specified for this prototype).
- Multi-instance/horizontal-scaling behavior (the in-process queue is
  inherently single-node - see Limitations).
- The H2 console / actuator endpoints (operational tooling, not business
  logic).

## 5. Limitations & trade-offs

These are deliberate scope decisions for a reviewable prototype, not
oversights. Each is expanded on in [memory-bank/decisions.md](memory-bank/decisions.md).

| Area | Limitation | Trade-off made | What production would add |
|---|---|---|---|
| Persistence | H2 in-memory; data lost on restart | Zero setup to run/review | Postgres/MySQL; schema is already portable (plain JPA, no H2-specific features) |
| Async pipeline | In-JVM thread pool, not a broker | No external infra; simple to reason about and test | SQS/Kafka/RabbitMQ or a DB outbox, so in-flight work survives a restart and scales across instances |
| Crash recovery | An item claimed (`ATTEMPTING`) when the process dies is **not** automatically resumed. (A `QUEUED` row that was never dispatched at all *is* recovered - `RetryScheduler` sweeps stale `QUEUED` rows.) | Keeps the claim mechanism simple (single atomic UPDATE) | A recovery sweep that also requeues stuck `ATTEMPTING` rows past a staleness threshold |
| Idempotency retention | Dedup keys expire after 24h (configurable) | Bounds storage growth | Tiered storage / archival instead of hard expiry, if longer dedup windows are required |
| Auth | No authentication/authorization on the API; H2 console and actuator endpoints are likewise unauthenticated (H2 console is off by default outside the `dev` profile) | Out of scope for a prototype focused on notification logic | API keys or mTLS per source system, plus per-source-system query scoping, and locking down actuator/H2 behind the same auth layer |
| Expiration/cancellation | `expiresAt` is stored and validated but not enforced by a sweep; no cancel-before-delivery API | Keeps the delivery pipeline simple | A scheduled expiry sweep transitioning stale `QUEUED` items to `EXPIRED`, and a `DELETE /notifications/{id}` style cancel API |
| Recipient preferences | Read-only, seeded via `DemoDataSeeder` | Avoids building a full preference-management API for a prototype | A recipient-profile service/API with proper CRUD and validation |
| Status-rollup locking | Uses a pessimistic DB row lock (`PESSIMISTIC_WRITE`) to serialize concurrent recomputes for one notification | Correct under concurrency, but adds lock contention/wait time proportional to fan-out per notification | Fine for prototype fan-out (one row per recipient/channel); a high fan-out production system might use an event-sourced/CQRS rollup instead |
| Transaction boundary | Provider "I/O" runs inside the same DB transaction as the claim/result persist | Correct and simple because providers are fast in-memory simulations | Move real provider I/O outside the transaction (claim in txn A, call provider, persist result in txn B), since real network calls should never hold a DB transaction open |
| Audit content | Sensitive-content exclusion is enforced by convention/code review | Simple for a prototype | A serialization-time redaction filter as defense in depth |

## 6. Project layout

```
src/main/java/com/schwab/assessment/notification/
├── api/            REST controller, DTOs, global exception handling
├── config/         Async executor config, demo data seeder
├── domain/         Enums: NotificationStatus, DeliveryStatus, ChannelType,
│                   Severity, Priority, FailureType, NotificationType, AuditAction
├── exception/      NotificationNotFoundException, ProviderException
├── model/          JPA entities
├── repository/     Spring Data JPA repositories
└── service/
    ├── delivery/    Async orchestrator, transactional attempt executor,
    │                retry policy, retry scheduler, status aggregator
    └── provider/    NotificationProvider strategy interface + Email/SMS/
                     Push/Webhook implementations + registry

memory-bank/         Architecture, decisions, state model, scenarios,
                     changelog, and other persistent project knowledge
```

## 7. Configuration reference

See [memory-bank/techContext.md](memory-bank/techContext.md) for the full
list of `application.yml` properties (retry attempts/backoff, worker pool
size, idempotency retention, routing escalation severities, etc.).
