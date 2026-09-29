# State Model (requirement 4.2)

The spec allows a custom state model if documented and defensible. This
service uses **two** state machines: an overall `NotificationStatus` and a
per (recipient, channel) `DeliveryStatus`. The overall status is always a
deterministic rollup of the child statuses (see `NotificationStatusAggregator`).

## NotificationStatus (overall)

| Status | Meaning |
|---|---|
| `RECEIVED` | Persisted, not yet routed. |
| `ROUTED` | Recipient/channel targets resolved and queued. |
| `IN_PROGRESS` | At least one recipient-channel is still queued/attempting/awaiting retry. |
| `DELIVERED` | Every recipient-channel `SUCCEEDED`. |
| `PARTIALLY_DELIVERED` | At least one `SUCCEEDED` and at least one terminally failed, none still in flight. |
| `FAILED` | Every recipient-channel terminally failed (`FAILED_TERMINAL` or `EXHAUSTED`). |
| `REJECTED` | Submission rejected before persistence (validation failure - returned synchronously, never persisted). |
| `DUPLICATE` | Fallback value if a duplicate is detected but the original record is unexpectedly missing (defensive; should not occur in practice). |
| `EXPIRED` | Reserved for expiration-timestamp handling (see Limitations - expiry sweep is not implemented in this prototype). |
| `CANCELLED` | Reserved for a future cancel-before-delivery API (not implemented). |

Transitions: `RECEIVED -> ROUTED -> IN_PROGRESS -> {DELIVERED | PARTIALLY_DELIVERED | FAILED}`.
`IN_PROGRESS` can be re-entered from `PARTIALLY_DELIVERED`/`FAILED`-looking
states while any child is still `FAILED_RETRYABLE` (a still-registered retry).

## DeliveryStatus (per recipient + channel)

| Status | Meaning | Retry eligible? |
|---|---|---|
| `PENDING` | Reserved for pre-queue validation states (not currently used; created rows start at `QUEUED`). | n/a |
| `QUEUED` | Created, waiting for a worker. | n/a |
| `ATTEMPTING` | Claimed by a worker, provider call in flight. | n/a |
| `SUCCEEDED` | Provider accepted the message. | terminal |
| `FAILED_RETRYABLE` | Failed with a retryable `FailureType`, backoff scheduled (`nextRetryAt` set). | yes, until exhausted |
| `EXHAUSTED` | Retryable failure type but `maxAttempts` reached. | terminal |
| `FAILED_TERMINAL` | Non-retryable failure type (invalid recipient, permanent rejection, auth error). | terminal |
| `SUPPRESSED_DUPLICATE` | Reserved for delivery-level suppression reporting (see Limitations). | n/a |

Transitions: `QUEUED -> ATTEMPTING -> {SUCCEEDED | FAILED_RETRYABLE | FAILED_TERMINAL}`,
and `FAILED_RETRYABLE -> ATTEMPTING` (via the retry scheduler) repeating until
`SUCCEEDED`, `FAILED_TERMINAL`, or `EXHAUSTED`.

## Why a rollup instead of an independently-tracked overall status
An independently-mutated overall status can drift from the true state of its
children (e.g. a bug forgets to update it after the last channel succeeds).
Recomputing it as a pure function of the children after every attempt
guarantees consistency at the cost of one extra read+maybe-write per attempt
- an acceptable trade-off at this scale.
