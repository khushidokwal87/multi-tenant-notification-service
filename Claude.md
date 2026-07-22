# AI Development Log

## AI Tools Used

- Cursor 
- ChatGPT
- Claude Code (Claude Sonnet 5, via Anthropic's CLI)

---

## Development Principles

AI was used as an engineering assistant for:
- Brainstorming architecture
- Reviewing design decisions
- Generating boilerplate code
- Explaining framework concepts
- Reviewing implementations

All generated code was manually reviewed and modified before being committed.

---

## Development Log

---

#### Domain Modeling

AI Assistance:
- Discussed entities.
- Suggested relationships.
- Reviewed normalization.

Developer Decisions:
- Finalized entity relationships.
- Added BaseEntity for auditing.

---

#### Retry & Delivery Audit Trail

AI Assistance:
- Reviewed the gap between the PRD's audit-trail requirement and the schema as it stood (no
  attempt history, no retry bookkeeping on Notification).
- Suggested keeping attempt history in its own append-only table rather than adding
  last-attempt-only columns to Notification.

Developer Decisions:
- Added `attemptCount` and `nextRetryAt` to Notification.
- Added NotificationAttempt (one row per attempt: number, outcome, error message) and the
  matching Flyway migration.

---

#### Dispatch Engine

AI Assistance:
- Walked through the tradeoffs for auth approach, simulated channel senders vs real providers,
  and worker-pool vs scheduled-poller dispatch models before writing any of it.
- Pointed out that immediate sends and scheduled sends don't need separate code paths — both
  can feed the same bounded pool, with a poller only responsible for promoting due rows.
- Caught a duplicate-dispatch race (two poll ticks claiming the same row) and a Spring
  self-invocation issue that would have silently dropped @Transactional on the claim step.

Developer Decisions:
- Bounded ThreadPoolExecutor with CallerRunsPolicy as the single place all sends execute.
- In-memory token bucket per tenant+channel for rate limiting, no external library.
- Exponential backoff for retries, capped and configurable.
- Simulated senders with a configurable random failure rate, so retries are actually
  exercised instead of being dead code.
- Added an IN_PROGRESS status to close the poller re-claim race.
- Accepted that rate-limit buckets don't hot-reload a config change — documented as a
  known limitation instead of solved.

---

#### Security, RBAC & REST API

Developer Decisions:
- JWT auth via a custom /api/auth/login, no Spring Security AuthenticationManager/
  UserDetailsService — manual credential check against the User table, since the auth model
  here doesn't fit that abstraction cleanly.
- Made User.tenant nullable; platform admins have no tenant row. Enforced their username/email
  uniqueness with partial unique indexes (WHERE tenant_id IS NULL) instead of forcing them
  into a placeholder tenant.
- Login takes an optional tenantName; omitted for platform admins, required for tenant admins.
- Seeded exactly one platform admin on first boot via an ApplicationRunner rather than a
  hand-written bcrypt hash in a SQL migration, so the seed always matches the real
  PasswordEncoder bean.
- Tenant-scoped resources return 404 (not 403) to a caller from a different tenant, so the
  response doesn't confirm another tenant's data exists.
- Excluded UserDetailsServiceAutoConfiguration — this app doesn't use Spring Security's
  default authentication flow at all, so the auto-generated in-memory user was just noise.
