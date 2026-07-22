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
- JWT auth and role-based access control for both roles
- REST API covering tenants, tenant admin provisioning, templates, channel configs, and the full
  notification lifecycle (submit, list, get, cancel, view attempt history)
- Unit tests for the dispatch engine's logic, plus MockMvc integration tests for auth, RBAC,
  tenant isolation, and the end-to-end submit-to-delivery flow

**Not done yet:**
- Pagination on list endpoints (fine at the data volumes this is built and tested for)
- Hot-reloading a tenant's rate limit without an app restart (see the rate limiting section below)

## Tech stack

Spring Boot, Spring Data JPA / Hibernate, PostgreSQL, Flyway for migrations, Spring Security with
a self-issued JWT (no OAuth/SSO), Lombok to cut down on entity boilerplate, H2 in-memory for tests.
Nothing exotic — the PRD rules out distributed systems and heavy infra, so I kept the stack to what
a single instance actually needs.

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

## Auth & RBAC

Login is a single `POST /api/auth/login` that takes a username, password, and an optional
`tenantName`, and returns a signed JWT. The JWT carries `userId`, `role`, and `tenantId` as claims;
`JwtAuthenticationFilter` verifies the signature and populates the security context directly from
those claims — there's no per-request database lookup, since the token is self-issued and
trusted once its signature checks out. Every controller pulls the caller's `tenantId` out of the
authenticated principal, never from the request body or path, so a tenant admin can't simply pass
a different `tenantId` and reach another tenant's data.

### Platform admins don't belong to a tenant

`User.tenant` is nullable. A platform admin manages tenants and global limits across the whole
system — tying their account to one specific tenant row never made sense, so instead of forcing
every user into some artificial "home tenant," platform admins are simply users with no tenant at
all. That meant the existing `(tenant_id, username)` / `(tenant_id, email)` unique constraints
needed a partner: Postgres treats `NULL` as distinct from itself in a unique constraint, so two
platform admins could otherwise share a username. Two partial unique indexes
(`WHERE tenant_id IS NULL`) close that gap without touching the tenant-scoped constraints that
already work correctly for tenant admins.

This is also why login takes an optional `tenantName`: usernames are only unique *within* a
tenant, so `POST /api/auth/login` needs to know which tenant's "admin" you mean unless you're
logging in as a platform admin, where the username is globally unique by construction.

### Bootstrapping the first platform admin

There's no self-signup and every tenant admin is created *by* a platform admin, which raises a
chicken-and-egg problem: something has to create the very first account. `PlatformAdminBootstrapper`
runs once on startup and seeds a single platform admin (username/password from
`notification.bootstrap.platform-admin.*`, both overridable) if no tenant-less user exists yet.
The default password is intentionally a placeholder — this is a seed-once bootstrap mechanism for
getting into the system, not a production credential story.

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

## REST API

All endpoints except `/api/auth/login` require `Authorization: Bearer <token>`.

| Method & path | Role | What it does |
|---|---|---|
| `POST /api/auth/login` | anyone | Log in, get a JWT |
| `POST /api/tenants` | platform admin | Create a tenant |
| `GET /api/tenants`, `GET /api/tenants/{id}` | platform admin | List / view tenants |
| `PATCH /api/tenants/{id}/status` | platform admin | Activate/suspend/deactivate a tenant |
| `POST /api/tenants/{id}/admins` | platform admin | Create the tenant admin for a tenant |
| `GET /api/tenants/{id}/channel-configs` | platform admin | View any tenant's channel config (oversight of "global limits") |
| `PUT /api/tenants/{id}/channel-configs/{channel}` | platform admin | Set any tenant's channel config |
| `POST /api/templates`, `PUT /api/templates/{id}` | tenant admin | Create / update a template (own tenant only) |
| `GET /api/templates`, `GET /api/templates/{id}` | tenant admin | List / view templates (own tenant only) |
| `GET /api/channel-configs`, `GET /api/channel-configs/{channel}` | tenant admin | View own channel config |
| `PUT /api/channel-configs/{channel}` | tenant admin | Set own channel config |
| `POST /api/notifications` | tenant admin | Submit a notification (immediate or scheduled) |
| `GET /api/notifications`, `GET /api/notifications/{id}` | tenant admin | List / view own notifications (the delivery reports the PRD asks for) |
| `GET /api/notifications/{id}/attempts` | tenant admin | View the audit trail for one notification |
| `POST /api/notifications/{id}/cancel` | tenant admin | Cancel a `PENDING`/`SCHEDULED` notification |

A tenant admin reaching for another tenant's template or notification gets `404`, not `403` — the
resource genuinely isn't part of their world, so the response shouldn't hint that it exists
elsewhere either.

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
| `notification.security.jwt.secret` | placeholder value | HMAC signing key for JWTs — override before any real deployment |
| `notification.security.jwt.expiration-minutes` | 60 | how long a token is valid |
| `notification.bootstrap.platform-admin.username` | `platformadmin` | seeded platform admin username |
| `notification.bootstrap.platform-admin.password` | `ChangeMe123!` | seeded platform admin password — change it |

## Running it

```
./mvnw spring-boot:run
```

Needs a local PostgreSQL reachable at the URL in `application.properties` (`dmg_db`,
`postgres`/`postgres` by default) — Flyway applies the migrations on startup. Tests run against an
in-memory H2 database instead (`src/test/resources/application-test.properties`), so they don't
need Postgres running at all.

On first boot, log in as the seeded platform admin (`platformadmin` / `ChangeMe123!` by default) to
create a tenant and its first tenant admin:

```
POST /api/auth/login            {"username": "platformadmin", "password": "ChangeMe123!"}
POST /api/tenants               {"name": "acme"}
POST /api/tenants/{id}/admins   {"username": "admin", "email": "admin@acme.test", "password": "..."}
POST /api/auth/login            {"username": "admin", "password": "...", "tenantName": "acme"}
```

## Testing so far

Two layers. `NotificationDispatchService`, the token bucket, and the backoff calculator are tested
directly with JUnit/Mockito, no Spring context — that's where the state-transition and math logic
lives, and mocking the DB and sender keeps those tests fast and focused on outcomes (success,
retryable failure, retries exhausted, channel disabled, rate-limited).

On top of that, `@SpringBootTest` + MockMvc integration tests drive the real HTTP endpoints against
H2: login (including rejecting a bad password and an unknown tenant), RBAC enforcement (no token →
401, wrong role → 403), cross-tenant isolation (a tenant admin gets 404, not the other tenant's
data), idempotent resubmission, scheduling then cancelling, and — with the simulated failure rate
turned to zero for determinism — a full submit-to-`SENT` round trip through the actual bounded
dispatch executor, not a mock of it. That last test is the one that proves the REST layer and the
dispatch engine are actually wired together correctly, not just individually correct.

## Assumptions, condensed

- Shared-schema multi-tenancy with a `tenant_id` column, not schema/DB-per-tenant.
- Platform admins have no tenant (`users.tenant_id` is nullable); their authority comes from their
  role, not tenant membership. Their username/email are enforced globally unique via partial
  indexes, not the tenant-scoped constraints used for everyone else.
- `IN_PROGRESS` is a real notification status, added specifically to prevent the scheduler's poller
  from dispatching the same due notification twice.
- Rate-limit rework at runtime (changing a tenant's configured limit) isn't picked up until app
  restart.
- Missing template variables render as empty strings, not errors.
- If a submitted notification supplies both a template and a raw subject/message, the rendered
  template wins.
- Channel senders are simulated with a random failure rate; there is no real email/SMS/push
  integration.
- A notification whose channel is disabled or unconfigured for that tenant fails terminally rather
  than waiting around for someone to fix the config.
- A notification can only be cancelled while it's `PENDING` or `SCHEDULED` — once a worker has
  claimed it (`IN_PROGRESS`) or it's reached a terminal state, cancellation is rejected rather than
  racing the dispatcher.
- There's no self-signup: a platform admin creates every tenant admin, and exactly one platform
  admin is seeded on first boot from `notification.bootstrap.platform-admin.*`.
