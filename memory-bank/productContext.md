# Product Context

## Problem
Business and technical systems (trading alerts, ops/system events, etc.)
need a single place to submit "something happened, tell the right people"
requests without each upstream system reimplementing channel delivery,
retry, and dedup logic itself.

## Users
- **Upstream systems** (source systems): submit notifications via the REST
  API, poll or are eventually pushed status.
- **Recipients**: end users who receive the actual Email/SMS/Push/Webhook
  message (out of scope to model as first-class users in this prototype -
  recipients are opaque string identifiers with optional channel
  preferences).
- **Operators/reviewers**: use the status and audit APIs to answer "did
  notification X get delivered, to whom, on what channel, and if not, why."

## Why these UX decisions
- **202 Accepted on submit** (not 200/201 with the final state): the system
  is explicitly asynchronous: the client should not assume delivery is
  complete when the call returns. A duplicate submission returns 200 (it
  performed no new work, so it's a query-like outcome, not an accepted
  command).
- **Status is a rollup, not a single provider response**: because one
  notification can fan out to many recipients and channels, a single
  "status" string would hide partial failure - callers need the per
  recipient/channel breakdown to act (e.g. retry a specific channel
  manually, alert on a specific recipient).
- **Audit is separate from status**: status answers "what is the state
  right now"; audit answers "what happened, in order, and why" - a
  reviewer/compliance need, not an operational one, so it is a distinct
  endpoint and a distinct, append-only table.
