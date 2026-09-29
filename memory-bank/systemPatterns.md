# System Patterns

## Strategy pattern for channels
`NotificationProvider` + `ProviderRegistry` (Spring collects all
`NotificationProvider` beans into a `List` and indexes them by
`ChannelType`). Adding a channel = adding one class; no orchestrator
switch/if-else ever needed. This is the pattern the brownfield scenario
explicitly validated.

## Atomic claim for exactly-once-attempt semantics
`RecipientChannelRepository.claimForAttempt` is a single `UPDATE ... WHERE
status IN (...)` JPQL statement. This is the standard "conditional update as
a lock" pattern for avoiding double-processing in a multi-worker pull-based
queue, without needing `SELECT ... FOR UPDATE` or a distributed lock -
appropriate here because there is one shared database.

## Rollup/derived state instead of dual-write
`NotificationStatusAggregator.recompute` treats the parent's status as a
pure function of its children, recomputed after every child mutation,
rather than updated ad-hoc at every call site that changes a child. This
avoids an entire class of "forgot to update the parent" bugs at the cost of
an extra read per attempt.

## Collaborator extraction to respect proxy-based AOP
Spring's `@Transactional`/`@Async` only intercept calls that go through the
bean's proxy. Any method that needs one of these and is invoked from another
method on the *same* class is moved to its own `@Service` bean
(`NotificationPersistenceService`, `DeliveryAttemptExecutor`). This is
applied consistently rather than reached for only when a bug appeared (see
`decisions.md#D9`).

## Fail-safe default for unclassified exceptions
Any exception a provider throws that is *not* a `ProviderException` is
caught at the orchestration boundary and mapped to `FailureType.UNKNOWN`,
which is retryable. Default-open on retry (rather than default-closed /
immediately terminal) was chosen because a transient bug in one provider
should not silently drop a notification - it should be visible in audit
history as repeated failures instead.
