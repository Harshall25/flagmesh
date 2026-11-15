# Project Plan

> The delivery plan for **FlagMesh** and **PayCore**. Phases, deliverables, learning goals and exit criteria.
> Build order: FlagMesh first (Phases 1–4), then PayCore (Phases 5–9), then integration and polish (Phases 10–11), then deployment (Phase 12).
> Everything runs locally in Docker until Phase 12. Moving to managed services later is an env var change, not a code change.

---

## 1. Objectives

| # | Objective | Measure of success |
|---|---|---|
| O1 | Build a correct payment engine | Concurrency and reconciliation tests prove there is no double charge, no lost money and no lost events. |
| O2 | Build a working feature-flag platform | A flag change reaches a running app in about 1 s, with no restart. |
| O3 | Learn microservices and system design hands-on | Every new concept is studied, grilled and written up in Notion. |
| O4 | Produce a portfolio-grade showcase | One-command startup, clear docs, diagrams and a 5-minute demo. |

---

## 2. Repositories

| Repo | Folders | Notes |
|---|---|---|
| `flagmesh` | `control-plane/` · `sidecar/` · `admin-ui/` · `deploy/` | Java and Go share one flag JSON contract and one Redis payload. `deploy/` holds `docker-compose.yml` and `Dockerfile.sidecar`. |
| `paycore` | `payment-service/` · `deploy/` | Uses the flagmesh sidecar on `localhost:9090`. |
| `workmesh` | (existing) | Untouched. It could run the sidecar later. |

---

## 3. Tech stack

| Layer | Choice |
|---|---|
| FlagMesh control-plane | Java 21 + Spring Boot 4, `JdbcClient` (hand-written SQL), Flyway |
| PayCore payment-service | Java (LTS) + Spring Boot (latest stable) |
| Sidecar | Go 1.23+ (`go-redis/v9`, `murmur3`), ~15 MB static binary |
| Admin UI | React 19 + Vite + plain CSS |
| Database | PostgreSQL 17 with Flyway migrations. Local Docker during development. At deployment, FlagMesh moves to Neon (managed) through env vars only. |
| Messaging | Apache Kafka (KRaft mode), PayCore only |
| Cache and push | Redis: Pub/Sub for FlagMesh, a token bucket for PayCore rate limiting. Local Docker during development; Upstash (managed) for FlagMesh at deployment. |
| Testing | TestNG (run on the JUnit Platform through `testng-engine`), Mockito, AssertJ, Spring Boot Test, Testcontainers · Go: table-driven tests, `go test -race` |
| Runtime | Docker and Docker Compose locally. At deployment, one Oracle Cloud Always Free ARM VM (Phase 12). |
| Observability | Datadog Logs through the agentless HTTPS intake, live log level (`slog.LevelVar`, Spring `LoggingSystem`), Spring Boot Actuator |

---

## 4. Roadmap

| Phase | Repo | Deliverable | Key concepts | Notion page | Exit criteria |
|---|---|---|---|---|---|
| **0** | both | Project setup and tooling | Maven/Gradle, project layout, Go modules, Docker basics, test setup | Java Dev | Both apps start against local Docker PostgreSQL and Redis. |
| **1** | flagmesh | Control plane + audit engine | `JdbcClient` SQL, Flyway, `@Transactional`, row locks (`FOR UPDATE`), audit diffs, `afterCommit` hooks, Redis Pub/Sub | Java Dev + System Design | A `PATCH` raises `version` by exactly 1, `flag_audit_logs` stores the exact diff, and `redis-cli SUBSCRIBE flags:updates` receives the event. |
| **2** | flagmesh | Go sidecar on `:9090` | `sync.RWMutex`, version guard, atomic file writes, MurmurHash3 bucketing, cold bootstrap, reconciliation | System Design | `go test -race` passes. With control-plane and Redis stopped, a restarted sidecar still answers `/v1/evaluate` from `flags_cache.json`. |
| **3** | flagmesh | Datadog logging + live log level | `slog.LevelVar`, Spring `LoggingSystem`, async batching, back-pressure | System Design | Flipping `observability.log-level` to DEBUG shows verbose logs in Datadog Live Tail within 2 s, with no restart. |
| **4** | flagmesh | admin-ui + local packaging | React 19, Vite, CORS, multi-stage Docker builds | Java Dev | Editing `payment.rate-limit` in the browser changes the `/v1/evaluate` result. `docker compose up --build` runs FlagMesh end to end. |
| **5** | paycore | Accounts + double-entry ledger | ACID, isolation levels, locking, deadlocks, rollbacks | System Design | A concurrent-transfer test keeps the ledger sum at zero. |
| **6** | paycore | Idempotent payment API | Idempotency keys, retries, unique constraints, request hashing | System Design | 10 parallel identical requests create exactly 1 payment. |
| **7** | paycore | Transactional outbox + Kafka | Dual-write problem, Kafka topics, partitions, consumer groups, offsets, at-least-once delivery | System Design | With Kafka stopped, payments still succeed. Events arrive after restart. |
| **8** | paycore | Retries + DLQ | Backoff, poison messages, DLQ replay | System Design | A bad message lands in the DLQ while the main topic keeps flowing. |
| **9** | both | PayCore reads flags and live settings from the sidecar | Timeouts, fallbacks, circuit breakers (Resilience4j), kill switches, token-bucket rate limiting | System Design | With the sidecar stopped, payments continue on default flags. Kill switch, rate limit and fraud-v2 respond to live edits. |
| **10** | paycore | Full Docker Compose stack + observability | Containers, networking, health checks, metrics, logs shipped to Datadog | System Design | One command starts all services. Health endpoints are green. PayCore logs appear in Datadog. |
| **11** | both | Portfolio polish | Load testing (k6), documentation, demo | Both | README, diagrams and a 5-minute demo are complete. |
| **12** | both | Deploy to one Oracle Cloud Always Free ARM VM | Linux VM basics, Docker on ARM, reverse proxy and TLS (Caddy), secrets, API auth | System Design | `docker compose up -d` on the VM serves the whole system over HTTPS. The sidecar shares payment-service's network. |

---

## 5. Phase details

### Phase 0 — Foundations

| # | Task | Output |
|---|---|---|
| 0.1 | Generate the Spring Boot projects from start.spring.io (steps in Notion) | `payment-service/`, `control-plane/` |
| 0.2 | Initialise the Go module for the sidecar in GoLand | `sidecar/go.mod` |
| 0.3 | Write a local `docker-compose.yml` for PostgreSQL + Redis in each repo (Claude provides commented skeletons). Variable names are in each repo's `deploy/.env.example`. | `paycore/deploy/` and `flagmesh/deploy/` |
| 0.4 | Set up the testing stack (steps in Notion) | First passing test |
| 0.5 | Deferred. A Datadog account is needed by Phase 3. Neon and Upstash are needed only at deployment (Phase 12). Secrets always go in env vars, never in Git. | — |

**Local ports.** Both systems run side by side, so their host ports must not clash:

| System | PostgreSQL | Redis | Kafka | App |
|---|---|---|---|---|
| PayCore | 5432 | 6379 | 9092 | payment-service 8080 |
| FlagMesh | 5433 | 6380 | — | control-plane 8081, sidecar 9090 |

### Phases 1–4 — FlagMesh

> The FlagMesh blueprint marks some files as AI boilerplate: `pom.xml`, `go.mod`, `package.json`, `application.yml`, `V1__init_flags.sql`, the empty records and structs, and the Docker files. Under this project's working rules (section 6), you write those too. Claude provides notes, plus commented skeletons for the Docker files only.

| # | Task | Output |
|---|---|---|
| 1.1 | Skeleton: `pom.xml`, `application.yml`, `ControlPlaneApplication.java`, empty records (`FeatureFlag`, `FlagAuditLog`, `UpdateFlagRequest`, `FlagUpdateEvent`), and `V1__init_flags.sql` creating `flags.feature_flags` (no `environment` column: one environment per database), `flags.flag_audit_logs` and the 4 seed flags | Flyway creates both tables and the seed rows in Neon |
| 1.2 | `FlagRepository` with `JdbcClient`: `findAll`, `findByKeyForUpdate`, `upsert`, `insertAuditLog`, `findAuditsByFlagKey`, with JSONB mapped through `PGobject` | Hand-written SQL |
| 1.3 | `FlagMutationService.updateFlag()`: `@Transactional`, lock the row, `version = version + 1`, audit before/after JSON, publish to `flags:updates` in `afterCommit` | No publish without a commit |
| 1.4 | `FlagController`: `GET/POST /api/flags`, `PATCH /api/flags/{key}`, `GET /api/flags/{key}/audits`, `GET /api/internal/flags/snapshot` | REST API |
| 2.1 | `go.mod` (`go-redis/v9`, `murmur3`) and `models.go` (`FlagRule`, `EvaluateRequest`, `EvaluateResponse`) | `go mod tidy` is clean |
| 2.2 | `store.go`: `RWMutex` cache, `GetFlag` under `RLock`, `UpsertFlag` with the version guard, `SaveSnapshot` (`.tmp` + `os.Rename`) and `LoadSnapshot` | `go test -race` with 100 readers and 10 writers |
| 2.3 | `evaluator.go` + `evaluator_test.go`: disabled, then allowlist, then MurmurHash3 bucket, otherwise `REASON_DEFAULT` | Deterministic cohorts. Going from 20% to 50% keeps the first 20% on. |
| 2.4 | `main.go`: snapshot bootstrap with disk fallback, Redis subscriber goroutine, 60 s reconciler, `GET /health`, `POST /v1/evaluate` on `:9090` | Works offline from `flags_cache.json` |
| 3.1 | `logger.go`: `slog.LevelVar` driven by `observability.log-level`, plus an async `slog.Handler` (channel of 1,000, flushed every 2 s) to Datadog | Live DEBUG in the sidecar |
| 3.2 | `DynamicLogLevelApplier` (Spring `LoggingSystem`) and `DatadogLogShipper` (`LinkedBlockingQueue` + Java `HttpClient`) | Live DEBUG in control-plane |
| 4.1 | `App.jsx`: toggles, 0–100% rollout slider, inline JSON payload editor, audit diff drawer (`package.json`, `index.html`, `main.jsx` and `App.css` are set up by hand) | Admin dashboard |
| 4.2 | `deploy/Dockerfile.sidecar` (multi-stage, ~12 MB image) and `deploy/docker-compose.yml` for control-plane, sidecar and admin-ui. Commented skeletons from Claude; you write the content. | One-command FlagMesh |

### Phases 5–9 — PayCore

| # | Task | Output |
|---|---|---|
| 5.1 | Flyway migration for the PayCore schema | `V1__init.sql` |
| 5.2 | Transfer service: lock both accounts in a fixed order, write 2 ledger rows | Balanced ledger |
| 5.3 | Concurrency test with many parallel transfers | Proof of no lost updates |
| 6.1 | Idempotency filter: hash the request, look up the key, store the response | Safe retries |
| 6.2 | Reject a reused key with a different body | `422` response |
| 7.1 | Outbox insert in the payment transaction | `outbox_events` rows |
| 7.2 | Scheduled relay: poll, publish, mark as sent | Events on `payments.events` |
| 7.3 | Idempotent consumer (dedupe by event ID) | Duplicates are harmless |
| 8.1 | Retry with exponential backoff, then send to `payments.events.dlq` | DLQ flow |
| 8.2 | DLQ replay endpoint or script | Recovery tool |
| 9.1 | Flag client calling `POST localhost:9090/v1/evaluate`, with a short timeout and safe defaults | Feature-gated behaviour |
| 9.2 | Circuit breaker around sidecar calls | Graceful degradation |
| 9.3 | `payment.kill-switch`: reject with `503` and `Retry-After` from the payload | Maintenance mode |
| 9.4 | `payment.rate-limit`: Redis token bucket sized from `requestsPerMinute` and `burst` | Live-tunable rate limit |
| 9.5 | `payment.fraud-v2`: new fraud check for users in the rollout, using `riskScoreThreshold` and `timeoutMs` | Gradual rollout |
| 9.6 | `observability.log-level`: switch PayCore logging through `LoggingSystem` | Live DEBUG in PayCore |

### Phases 10–11 — Deploy and polish

| # | Task | Output |
|---|---|---|
| 10.1 | Dockerfile per service, full Compose stack | One-command startup |
| 10.2 | Actuator health checks, metrics, structured logging | Observable system |
| 11.1 | k6 load test on the payment API | Throughput and latency numbers |
| 11.2 | Final README, diagrams and demo script | Portfolio-ready repos |

### Phase 12 — Deployment (one Oracle Cloud VM)

| # | Task | Output |
|---|---|---|
| 12.1 | Create an Oracle Always Free ARM VM, install Docker, and open only ports 80 and 443 | A reachable host |
| 12.2 | Create a Neon project `flagmesh` and an Upstash Redis database, and put their URLs in a `.env` file on the VM (never in Git) | Managed data for FlagMesh |
| 12.3 | Protect control-plane's admin and snapshot APIs with an admin token | Nobody else can flip the kill switch |
| 12.4 | Put Caddy in front as a reverse proxy, with automatic HTTPS | TLS everywhere |
| 12.5 | Build arm64 images and run `docker compose up -d`. The sidecar uses `network_mode: "service:payment-service"`. | The whole system runs live |
| 12.6 | Set `-XX:MaxRAMPercentage=75` on the JVMs, add restart policies and Docker health checks | Survives restarts and memory limits |

---

## 6. Ways of working

| Area | Rule |
|---|---|
| Code | The developer writes all application code, setup and tests. Claude explains, reviews and answers questions. |
| Setup steps | Spring Boot project generation and the testing setup are written as generic Notion notes and followed by hand. |
| Docker files | Claude may create commented skeletons only. The developer writes the actual YAML and Dockerfile content. |
| Git | The developer makes all commits. |
| Learning loop | A new concept appears, it gets explored in depth, a grill session follows, then the notes are filed in Notion. |
| Notion | Exactly two pages: **All about Java Development** and **System Design**. |

### Learning loop

| Step | Activity | Tool |
|---|---|---|
| 1 | Build until a new concept appears (e.g. Redis, transactions) | IDE |
| 2 | Deep-dive the concept: what, why, trade-offs, failure modes | Chat |
| 3 | Stress-test understanding with a grill session | grill skill |
| 4 | File structured notes in the right Notion page | notion-notes skill |
| 5 | Apply the concept in code | IDE |

---

## 7. Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Scope creep (too many services) | The project never finishes | Keep to the phases above. Out-of-scope items are listed in the architecture doc. |
| Flag contract drift (Java vs Go) | Sidecar parse errors | One shared schema in `flagmesh`. A contract test on both sides. |
| Flaky concurrency tests | False confidence | Use Testcontainers with real PostgreSQL. Run repeated test cycles. |
| Local machine resources (Kafka + Redis + PostgreSQL) | Slow development loop | Start only the services each phase needs. |
| Free-tier limits (Neon, Upstash, Datadog) | Throttling or paused databases | Keep test traffic small. The sidecar's disk backup keeps reads working. |
| Secrets in Git (`DD_API_KEY`, database URLs) | Leaked credentials | Environment variables only. Keep `.env` files in `.gitignore`. |

---

## 8. Definition of done

- [ ] `docker compose up` starts the whole system from a fresh clone.
- [ ] Concurrency, idempotency and outbox behaviour are proven by automated tests.
- [ ] Flag changes reach PayCore within about 1 s, and PayCore survives the sidecar going down.
- [ ] Flipping `observability.log-level` changes log detail live in Datadog, with no restart.
- [ ] All docs and diagrams are up to date.
- [ ] Both Notion pages cover every concept used.
