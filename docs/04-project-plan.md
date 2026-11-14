# Project Plan

> The delivery plan for **FlagMesh** and **PayCore**. Phases, deliverables, learning goals and exit criteria.
> Build order: FlagMesh first (Phases 1–4), then PayCore (Phases 5–9), then integration and polish (Phases 10–11).

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
| `flagmesh` | `control-plane/` · `sidecar/` · `admin-ui/` | Java and Go share one flag JSON contract and one Redis payload. |
| `paycore` | `payment-service/` · `deploy/` | Uses the flagmesh sidecar on `localhost:9090`. |
| `workmesh` | (existing) | Untouched. It could run the sidecar later. |

---

## 3. Tech stack

| Layer | Choice |
|---|---|
| Backend services | Java (LTS) + Spring Boot (latest stable) |
| Sidecar | Go (latest stable) |
| Admin UI | React |
| Database | PostgreSQL, with Flyway migrations |
| Messaging | Apache Kafka (KRaft mode) |
| Cache and push | Redis (Pub/Sub) |
| Testing | JUnit 5, Mockito, AssertJ, Spring Boot Test, Testcontainers |
| Runtime | Docker and Docker Compose |
| Observability | Spring Boot Actuator, structured logs, Micrometer metrics |

---

## 4. Roadmap

| Phase | Repo | Deliverable | Key concepts | Notion page | Exit criteria |
|---|---|---|---|---|---|
| **0** | both | Project setup and tooling | Maven/Gradle, project layout, Go modules, Docker basics, test setup | Java Dev | Both apps start locally. `docker compose up` starts PostgreSQL and Redis. |
| **1** | flagmesh | control-plane: flag CRUD + audit log | REST design, JPA/Hibernate, Flyway, validation, error handling | Java Dev | CRUD works via Postman. Tests pass. |
| **2** | flagmesh | Change broadcast over Redis | Redis data types, caching patterns, Pub/Sub vs Streams, versioning | System Design | `redis-cli SUBSCRIBE flag-updates` shows each change. |
| **3** | flagmesh | Go sidecar on `:9090` | Goroutines, channels, `RWMutex`, HTTP server, graceful shutdown | System Design | A toggle shows up at `localhost:9090` within 1 s. |
| **4** | flagmesh | admin-ui | React basics, CORS, REST clients | Java Dev | Flags can be toggled from the browser. |
| **5** | paycore | Accounts + double-entry ledger | ACID, isolation levels, locking, deadlocks, rollbacks | System Design | A concurrent-transfer test keeps the ledger sum at zero. |
| **6** | paycore | Idempotent payment API | Idempotency keys, retries, unique constraints, request hashing | System Design | 10 parallel identical requests create exactly 1 payment. |
| **7** | paycore | Transactional outbox + Kafka | Dual-write problem, Kafka topics, partitions, consumer groups, offsets, at-least-once delivery | System Design | With Kafka stopped, payments still succeed. Events arrive after restart. |
| **8** | paycore | Retries + DLQ | Backoff, poison messages, DLQ replay | System Design | A bad message lands in the DLQ while the main topic keeps flowing. |
| **9** | both | PayCore reads flags from the sidecar | Timeouts, fallbacks, circuit breakers (Resilience4j) | System Design | With the sidecar stopped, payments continue on default flags. |
| **10** | paycore | Full Docker Compose stack + observability | Containers, networking, health checks, metrics, logs | System Design | One command starts all services. Health endpoints are green. |
| **11** | both | Portfolio polish | Load testing (k6), documentation, demo | Both | README, diagrams and a 5-minute demo are complete. |

---

## 5. Phase details

### Phase 0 — Foundations

| # | Task | Output |
|---|---|---|
| 0.1 | Generate the Spring Boot projects from start.spring.io (steps in Notion) | `payment-service/`, `control-plane/` |
| 0.2 | Initialise the Go module for the sidecar in GoLand | `sidecar/go.mod` |
| 0.3 | Write `docker-compose.yml` for PostgreSQL + Redis (Claude provides a commented skeleton when this phase starts) | `deploy/docker-compose.yml` |
| 0.4 | Set up the testing stack (steps in Notion) | First passing test |

### Phases 1–4 — FlagMesh

| # | Task | Output |
|---|---|---|
| 1.1 | Flyway migration for `flags`, `flag_rules`, `flag_audit_log` | `V1__init.sql` |
| 1.2 | REST endpoints: create, list, get, update and delete a flag | `/api/flags` |
| 1.3 | Bean validation and a global exception handler | Consistent error JSON |
| 1.4 | Audit row written in the same transaction as every change | `flag_audit_log` |
| 2.1 | Publish to `flag-updates` after commit | Redis message per change |
| 2.2 | Optimistic locking with the `version` column | `409 Conflict` on concurrent edits |
| 3.1 | Sidecar: snapshot on startup, Redis subscriber, in-memory map | `sidecar/` binary |
| 3.2 | `GET /flags/{key}` and `GET /flags` on `:9090` | Local flag API |
| 3.3 | Periodic resync, version checks, graceful shutdown | Self-healing cache |
| 4.1 | React screen: list, toggle, edit rollout percentage | `admin-ui/` |

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
| 9.1 | Flag client with a short timeout and safe defaults | Feature-gated behaviour |
| 9.2 | Circuit breaker around sidecar calls | Graceful degradation |

### Phases 10–11 — Deploy and polish

| # | Task | Output |
|---|---|---|
| 10.1 | Dockerfile per service, full Compose stack | One-command startup |
| 10.2 | Actuator health checks, metrics, structured logging | Observable system |
| 11.1 | k6 load test on the payment API | Throughput and latency numbers |
| 11.2 | Final README, diagrams and demo script | Portfolio-ready repos |

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

---

## 8. Definition of done

- [ ] `docker compose up` starts the whole system from a fresh clone.
- [ ] Concurrency, idempotency and outbox behaviour are proven by automated tests.
- [ ] Flag changes reach PayCore within about 1 s, and PayCore survives the sidecar going down.
- [ ] All docs and diagrams are up to date.
- [ ] Both Notion pages cover every concept used.
