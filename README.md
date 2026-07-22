# Multi-Tenant Notification Service

A backend service for sending notifications (email, SMS, push, in-app) on behalf of multiple
tenants — with tenant-owned templates, per-tenant rate limits, scheduled and immediate sends, and
retries with backoff when a channel fails. 

## Where things stand

**Done:**
- Domain model + Flyway migrations: tenants, users, templates, channel configs, notifications,
  notification attempts
- The dispatch engine: bounded worker pool, per-tenant/per-channel rate limiting, retry with
  exponential backoff, simulated channel senders, template variable substitution
- Unit tests for the dispatch engine's logic

**Not done yet:**
- REST API layer (controllers, DTOs, request validation)
- Auth and role-based access control
- Integration tests exercising the actual HTTP endpoints

## Tech stack

Spring Boot, Spring Data JPA / Hibernate, PostgreSQL, Flyway for migrations, Lombok to cut down on
entity boilerplate, H2 in-memory for tests. Nothing exotic — the PRD rules out distributed systems
and heavy infra, so I kept the stack to what a single instance actually needs.

## Multi-tenancy approach

I went with a shared schema and a `tenant_id` discriminator column on every tenant-owned table
(`users`, `templates`, `channel_configs`, `notifications`), rather than schema-per-tenant or
database-per-tenant. it's simpler to build,
migrate, and test, and the PRD explicitly puts distributed-systems concerns out of scope, so I
didn't need the stronger isolation a separate-schema approach buys you. The cost is that every
query has to remember to filter by tenant; I've enforced that at the repository level
(`findByTenantIdAndX`-style methods) rather than a global tenant filter, since explicit,
grep-able queries are easier to verify by reading the code than a filter that's applied by magic.

## Entities

- `Tenant` — name, status (`ACTIVE` / `INACTIVE` / `SUSPENDED`)
- `User` — belongs to a tenant, has a role (`PLATFORM_ADMIN` or `TENANT_ADMIN`); username and
  email are unique per tenant, not globally, since two different tenants shouldn't be blocked from
  both having a user named `admin`
- `Template` — tenant-owned, tied to a channel, with a body that supports `{{variable}}`
  placeholders
- `ChannelConfig` — per-tenant, per-channel settings: enabled/disabled, rate limit per minute, max
  retry count
- `Notification` — a single send: channel, recipient, rendered message, current status, scheduled
  and sent time, attempt count, and the next retry time
- `NotificationAttempt` — one row per actual delivery attempt (success or failure), kept separate
  from `Notification` so current state and history don't collide

### Why a separate `NotificationAttempt` table

The PRD asks for delivery state transitions and retry attempts to be persisted with an audit
trail. I could have bolted extra columns onto `Notification` (last error, last attempt time), but
that only ever gives you the *latest* attempt — if a notification fails twice and then succeeds on
the third try, you'd lose the record of the first two failures. `NotificationAttempt` is append-only:
attempt number, outcome, error message, timestamp. `Notification` itself only holds the current,
mutable state (`status`, `attemptCount`, `nextRetryAt`) needed to drive dispatch.

### Idempotency

`notifications` has a unique constraint on `(tenant_id, idempotency_key)`. The caller of the future
"send" API supplies (or is given) an idempotency key, and resubmitting the same request is a no-op
rather than a duplicate send. This is a different concern from the retry-with-backoff inside the
dispatch engine, which retries a *single already-accepted* notification against the channel — the
idempotency key protects against the API being called twice, the retry logic protects against a
transient channel failure.

## The dispatch engine

### Bounded worker pool

One `ThreadPoolExecutor` (sizes configurable, defaults 4 core / 8 max / 200 queued) is where every
actual send happens, whether it came from an immediate submission, the scheduled-send poller, or a
retry. When the queue fills up I used `CallerRunsPolicy` rather than dropping the task or throwing
— the submitting thread just runs it directly instead. For a notification service, silently losing
work under load felt like the wrong failure mode; occasionally making a caller wait a bit longer is
the safer default.

### Per-tenant, per-channel rate limiting

Each `(tenantId, channel)` pair gets its own in-memory token bucket, refilled continuously based on
that tenant's `ChannelConfig.rateLimitPerMinute`. I didn't reach for a library like Bucket4j for
this — a token bucket is small enough to write and reason about directly, and I'd rather own that
logic than pull in a dependency for something this size.

Known limitation I'm accepting rather than solving: the bucket is created once, on first dispatch
for a tenant+channel, and won't pick up a later change to the configured rate limit without an app
restart. Refreshing it live would mean either re-reading config on every `tryConsume()` call (extra
DB read on the hot path) or wiring up cache invalidation on config updates — more machinery than
this assignment calls for.

### Retry with backoff

When a send throws a transient failure, the notification goes back to `PENDING` with a
`nextRetryAt` computed by doubling a base delay on each attempt (5s, 10s, 20s, ...), capped at a
configurable maximum (60s by default). Once `attemptCount` reaches the channel's `maxRetry`, it's
marked `FAILED` for good. The failure path uses a checked exception (`TransientSendException`) on
purpose — "this can be retried" is part of the method's contract, not a comment someone has to go
find.

### Simulated channel senders

There's no real email/SMS/push provider wired in — that's explicitly out of scope. Each channel has
a sender that logs what it would do and fails at a configurable rate
(`notification.simulation.failure-rate`, 30% by default) instead of always succeeding. That's
deliberate: with a sender that never fails, the retry/backoff logic would just be dead code that
only gets exercised in a unit test, never in a live run.

### Immediate sends, scheduled sends, and retries share one path

Rather than separate code paths for "send now" and "send later," both end up going through the
same `dispatch()` method — an immediate send is just a notification whose scheduled time already
happened. A background poller (every 2s by default) looks for notifications that are `SCHEDULED`
and due, or `PENDING` with a `nextRetryAt` that's passed, and hands them to the same bounded pool.

Before handing anything off, the poller flips the matched rows to an `IN_PROGRESS` status in one
atomic bulk update. That step exists specifically to stop two poll ticks from grabbing the same row
if the first dispatch hasn't finished executing by the time the second tick fires — without it, a
slow send could get dispatched twice, which is exactly the duplicate-delivery scenario the PRD
calls out to avoid.

### Template variables

Templates use `{{variableName}}` placeholders. If a variable isn't supplied at send time, it
renders as an empty string rather than throwing — I'd rather a message go out with a blank spot
than fail an entire send over one missing field. This is an assumption, not something the PRD
specifies either way, so it's worth calling out explicitly.

## Configuration

New properties added for the dispatch engine, all in `application.properties`:

| Property | Default | What it controls |
|---|---|---|
| `notification.dispatch.core-pool-size` | 4 | worker pool core threads |
| `notification.dispatch.max-pool-size` | 8 | worker pool max threads |
| `notification.dispatch.queue-capacity` | 200 | bounded queue size before `CallerRunsPolicy` kicks in |
| `notification.dispatch.poll-interval-ms` | 2000 | how often the scheduler checks for due sends/retries |
| `notification.retry.base-delay-seconds` | 5 | first retry delay |
| `notification.retry.max-delay-seconds` | 60 | backoff ceiling |
| `notification.simulation.failure-rate` | 0.3 | chance a simulated send fails transiently |

## Running it

```
./mvnw spring-boot:run
```

Needs a local PostgreSQL reachable at the URL in `application.properties` (`dmg_db`,
`postgres`/`postgres` by default) — Flyway applies the migrations on startup. Tests run against an
in-memory H2 database instead (`src/test/resources/application-test.properties`), so they don't
need Postgres running at all.

## Testing so far

Tests right now focus on the dispatch engine, since that's the part with the most actual logic
(state transitions, retry counting, backoff math, rate limiting) and the highest cost of getting
wrong. `NotificationDispatchService` is tested with Mockito against mocked repositories and a
mocked sender rather than a real database, since none of its behavior depends on actual persistence
— what matters is that it transitions `Notification` and records `NotificationAttempt` correctly
for each outcome (success, retryable failure, retries exhausted, channel disabled, rate-limited).
The token bucket and the backoff calculator are pure logic with no dependencies, so they're tested
directly, no mocks needed.

Once the REST layer exists, I'll add `@SpringBootTest` + MockMvc integration tests against H2 for
the real API flows — submit, schedule, retry-and-succeed, retry-exhausted, rate-limited.

## Assumptions, condensed

- Shared-schema multi-tenancy with a `tenant_id` column, not schema/DB-per-tenant.
- `IN_PROGRESS` is a real notification status, added specifically to prevent the scheduler's poller
  from dispatching the same due notification twice.
- Rate-limit rework at runtime (changing a tenant's configured limit) isn't picked up until app
  restart.
- Missing template variables render as empty strings, not errors.
- Channel senders are simulated with a random failure rate; there is no real email/SMS/push
  integration.
- A notification whose channel is disabled or unconfigured for that tenant fails terminally rather
  than waiting around for someone to fix the config.

## What's next

REST API layer (tenant, template, channel-config, and notification endpoints with validation and
error handling), then Spring Security with JWT-based auth so requests carry a real
`tenantId`/`role`, then RBAC checks on top of that.
