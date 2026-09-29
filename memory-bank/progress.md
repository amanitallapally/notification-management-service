# Progress

## Status: prototype complete
All functional requirements referenced in the assignment (4.1 Submit, 4.2
Status, 4.3 Routing, 4.4 Dedup/Idempotency, 4.5 Retry/Failure, 4.9 Audit)
are implemented and covered by automated tests. All three required
scenarios (greenfield, brownfield, ambiguous) are implemented and
documented in `scenarios.md`.

## What works
- Submit -> route -> async deliver -> retry -> status/audit, end to end,
  verified via `mvn test` (22/22 passing) and manual curl walkthrough.
- Idempotent resubmission returns the original notification without
  creating duplicate side effects.
- Deterministic failure-injection recipient-id conventions let any
  reviewer manually exercise every failure classification without reading
  the code (see README "Try it").

## Deliberately out of scope for this prototype
- Durable broker-backed queue (see `decisions.md#D2`).
- Authentication/authorization on the API.
- Recipient preference management API (preferences are seeded/read-only).
- Expiration/cancellation enforcement.
All of the above are called out explicitly in the README rather than
silently omitted.

## Suggested next increments (not built, for future work)
1. Replace the in-JVM executor with a durable queue and an outbox pattern
   for exactly-once submission-to-queue handoff.
2. Add an idempotency-key and audit-record retention/cleanup job (currently
   expiry is enforced only at lookup time, not proactively purged).
3. Add authentication (e.g. mTLS or API keys for source systems) and
   authorization scoping (a source system should only query its own
   notifications).
4. Add pagination to the audit history endpoint for high-retry notifications.
