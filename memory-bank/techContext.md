# Tech Context

## Stack
- Java 17, Spring Boot 3.3.x (web, data-jpa, validation, actuator)
- H2 in-memory database (Spring Data JPA / Hibernate)
- springdoc-openapi (Swagger UI at `/swagger-ui.html`)
- Lombok (entity boilerplate only - services/DTOs are plain/records)
- JUnit 5, Mockito, AssertJ, Awaitility (async assertions), Spring Boot Test
  + MockMvc for end-to-end tests
- Maven (Maven Wrapper `mvnw` included, no global Maven install required)

## Runtime shape
- Single Spring Boot application, single JVM process.
- `deliveryExecutor` thread pool (default 4 threads, queue capacity 1000)
  drives all provider calls.
- `RetryScheduler` is a `@Scheduled` poller (default every 1s) - not a
  separate process.
- No external network calls; all providers are in-process simulations.

## Configuration surface (`application.yml`)
| Property | Default | Purpose |
|---|---|---|
| `notification.delivery.worker-pool-size` | 4 | Delivery thread pool size |
| `notification.delivery.queue-capacity` | 1000 | Executor queue depth |
| `notification.retry.max-attempts` | 5 | Total attempts before giving up |
| `notification.retry.initial-backoff-ms` | 1000 | First retry delay |
| `notification.retry.backoff-multiplier` | 2.0 | Exponential growth factor |
| `notification.retry.max-backoff-ms` | 30000 | Backoff cap |
| `notification.retry.poll-interval-ms` | 1000 | Retry scheduler poll frequency |
| `notification.idempotency.retention-hours` | 24 | Dedup key retention |
| `notification.routing.escalate-severities` | CRITICAL | Severities that override recipient preference |

## Known constraints (see README Limitations for full list)
- Single-node only (in-JVM queue + H2 in-memory).
- No auth/authz on the API (out of scope for this prototype; see README).
- No expiration sweep for `expiresAt` (field is stored, not yet enforced).
