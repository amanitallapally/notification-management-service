# Engineering Scenarios

This document is the primary deliverable required by the assignment
("Three scenarios: greenfield, brownfield, ambiguous - each shows
decomposition, execution, validation").

## Greenfield Scenario

**Requirement**: design and implement the initial notification-management
capability - submission, recipient/channel selection, asynchronous
processing, delivery attempts, status retrieval.

### Decomposition
1. Define the domain model and state machines first (`domain` enums,
   `state-model.md`) so every later class has an unambiguous vocabulary.
2. Persistence: one aggregate root (`NotificationEntity`) with a child per
   routing target (`RecipientChannelEntity`) and an append-only attempt log
   (`DeliveryAttemptEntity`) - chosen so retries never overwrite history.
3. Submission path: validation (Bean Validation on the DTO) -> idempotency
   check -> persist -> route -> queue -> kick off async delivery.
4. Delivery path: a provider-agnostic orchestrator claims work atomically
   and delegates to a `NotificationProvider` strategy.
5. Status path: read-only projection of the same tables into
   `NotificationStatusResponse`.

### Execution
Implemented in this order (matches commit-sized units of work):
`domain enums -> JPA entities/repositories -> DTOs -> exception handling ->
provider strategy + registry -> routing/dedup/audit services -> delivery
pipeline (executor, retry policy, status aggregator, orchestrator, retry
scheduler) -> submission/status services -> controller`.

### Validation
- Unit tests: `RoutingServiceTest`, `RetryPolicyTest`, `EmailProviderTest`
  (failure classification), `DeduplicationServiceTest`.
- End-to-end tests (`NotificationControllerIntegrationTest`) drive the real
  HTTP + JPA + async pipeline and assert on the externally-visible status
  API, e.g. `submitAndDeliverSucceeds` polls `/notifications/{id}` until
  `DELIVERED`.
- Manual validation: see README "Try it" curl walkthrough.

## Brownfield Scenario

**Requirement**: treat the prototype as an existing system and implement an
enhancement/correction spanning multiple layers - a new channel and/or a
deduplication fix, refactoring provider-specific logic.

### Decomposition
Two coupled changes, both touching the provider layer and both required by
the assignment text ("a new notification channel/deduplication Refactor
provider-specific logic"):
1. **Refactor provider-specific logic**: the greenfield `EmailProvider`,
   `SmsProvider`, `PushProvider` each independently simulated
   recipient-driven failures (invalid/rate-limit/timeout/auth/reject/flaky).
   That per-channel duplication is a maintenance risk (a fix to the timeout
   simulation would need to be applied N times). Refactored into
   `AbstractSimulatedProvider`, a single place owning the shared
   classification rules; concrete providers now only declare their
   `ChannelType` and may override `doSend` for channel-specific success
   behavior (see `WebhookProvider`).
2. **Add a new channel (WEBHOOK)**: added `ChannelType.WEBHOOK`,
   `WebhookProvider extends AbstractSimulatedProvider`, and nothing else.
   `ProviderRegistry` auto-discovers it via Spring's `List<NotificationProvider>`
   injection; `DeliveryAttemptExecutor` and `RoutingService` have zero
   knowledge of specific channel types. This is the payoff of the
   strategy-pattern decision made during greenfield: a "multi-layer" change
   requested by the assignment ends up being additive rather than invasive
   specifically *because* the layers were decoupled correctly the first
   time.

### Execution
- Added `ChannelType.WEBHOOK` (domain layer).
- Added `AbstractSimulatedProvider` (extracted from the three existing
  providers; behavior-preserving refactor, no test changes needed for
  Email/SMS/Push).
- Added `WebhookProvider` (new layer: provider).
- Updated `application.yml` default channel order to include `WEBHOOK`
  (config layer).
- No changes required in: `ProviderRegistry`, `DeliveryAttemptExecutor`,
  `RoutingService`, `NotificationController`, or any DTO.

### Validation
- `webhookChannelDeliversSuccessfully` integration test submits a WEBHOOK
  notification end-to-end and asserts `DELIVERED` + `channel=WEBHOOK`.
- Existing Email/SMS/Push tests continued to pass unmodified after the
  refactor, demonstrating the refactor was behavior-preserving.
- Deduplication itself (both submission-level and delivery-level) was
  built into the greenfield design directly per the requirement wording
  ("must prevent avoidable duplicate notifications" is a hard functional
  requirement, not optional), and is validated by
  `duplicateSubmissionWithSameIdempotencyKeyReturnsOriginalId` plus the
  atomic-claim design in `DeliveryAttemptExecutor` (see `decisions.md#D3`).

## Ambiguous Requirement Scenario

**Requirement**: demonstrate handling of well-defined and ambiguous
requirements.

The assignment PDF itself has a documented gap (sections 4.6-4.8 are
missing from the source document - confirmed by page-by-page text
extraction, not a parsing artifact) which is itself an instance of an
"ambiguous/incomplete requirement" that had to be handled: the response was
to implement everything explicitly specified (4.1-4.5, 4.9) and avoid
guessing at unstated requirements rather than inventing scope.

Within the explicitly stated requirements, several points were genuinely
under-specified. Each was resolved with a documented, defensible default
rather than picked arbitrarily:

| # | Ambiguity | Resolution | Where documented |
|---|---|---|---|
| 1 | Idempotency key: is it caller-supplied or system-derived? | Caller-supplied if present; otherwise derived from `sourceSystem:eventId` so repeated upstream events still dedup. | `decisions.md#D3` |
| 2 | Channel routing precedence when requested channels, severity, and recipient preference disagree | Requested channels are a hard ceiling; preference filters/orders within it; CRITICAL severity escalates to all requested channels, overriding preference only. | `decisions.md#D6`, `RoutingService` Javadoc |
| 3 | "Custom state model...if documented and defensible" (4.2) | Two-level model (overall rollup + per-recipient-channel) instead of a single flat status, because a single status cannot represent partial delivery across multiple recipients/channels. | `state-model.md` |
| 4 | Retention policy for dedup keys (4.4 requires one to be documented, doesn't specify a value) | 24h default, configurable, chosen to cover the realistic lifetime of a notification + its retries without unbounded growth. | `decisions.md#D3` |
| 5 | What counts as "unnecessary sensitive content" in audit (4.9) | Notification subject/body and recipient contact address are never written to audit/attempt records; only IDs, enum states, and short computed reasons are. | `decisions.md#D8` |
| 6 | Scheduling/expiration timestamps are accepted on submission (4.1) but the spec never describes required behavior once they elapse | Timestamps are validated and persisted (so the field is honestly supported end-to-end for future use) but no background sweep enforces them yet - called out explicitly as a limitation rather than silently ignored or half-implemented. | README "Limitations" |

### Validation approach for ambiguous items
Because these are judgment calls rather than bugs, "validation" here means:
each decision is documented at the point of implementation (Javadoc +
`decisions.md`), each has an observable, testable behavior (not just a
comment), and none of them silently expand scope beyond what 4.1-4.5/4.9
actually ask for.
