# Spring Boot Notes API — Polyglot Microservice Consumer

[![CI](https://github.com/AshrafAhmed9/springboot-resource-api/actions/workflows/ci.yml/badge.svg)](https://github.com/AshrafAhmed9/springboot-resource-api/actions/workflows/ci.yml)

A Java/Spring Boot resource service that authenticates every request against a separate [Go auth service](https://github.com/AshrafAhmed9/go-auth-service) over **gRPC** — a polyglot microservices identity system. The domain (notes CRUD) is deliberately simple; the engineering focus is the cross-service auth path: cached token validation, a fail-closed circuit breaker, and honest load-test numbers for both.

**32 tests | Testcontainers + in-process gRPC integration suite | k6 load-tested | fail-closed resilience**

## Architecture

```
                 REST + JWT                       gRPC ValidateToken
  Client ─────────────────────▶ Notes API (Java) ─────────────────────▶ Auth Service (Go)
                                     │        ▲                              │
                                     │        │ Caffeine cache              │
                                     ▼        │ (SHA-256(token) → result,   ▼
                                PostgreSQL    │  TTL ≤ 60s)          PostgreSQL + Redis
                                (notes)       │                      (users, refresh
                                              │ Resilience4j          tokens, revocation
                                              └ circuit breaker       blacklist)
```

- Clients log in against the **Go service** (unchanged) and send the JWT to the **Java service**.
- A custom Spring Security `OncePerRequestFilter` validates the token via the Go service's gRPC `ValidateToken` and populates the `SecurityContext` with the user ID and role.
- Users can only touch their own notes (enforced in the service layer via `findByIdAndOwnerId` — other users' notes 404, never 403, to avoid existence leaks).
- `GET /api/admin/notes` demonstrates role-based method security (`@PreAuthorize("hasRole('ADMIN')")`).

## The interesting engineering

### 1. Validation cache (availability vs. instant revocation)
Successful gRPC validations are cached in **Caffeine**, keyed by `SHA-256(token)` (never the raw token), with per-entry TTL = `min(60s, token's remaining lifetime)` — the expiry claim is read locally *without* signature verification, since verification is the Go service's job and the TTL only bounds staleness.

**The tradeoff, owned:** the Go service checks each token's `jti` against a Redis revocation blacklist; a cached validation skips that check, so a revoked token stays usable here for up to 60 seconds. That's a deliberate choice of latency + availability over instant revocation, with the damage bounded by the TTL.

### 2. Fail-closed circuit breaker (contrast with the Go service)
The gRPC call has a **2s deadline** and a **Resilience4j circuit breaker** (50% failure rate over a 10-call window opens it; auto half-open after 10s). When the auth service is unreachable and the cache has no entry, the API returns **503 with `Retry-After`** — a resource API must not serve data it can't authorize.

**Contrast:** the Go service's rate limiter *fails open* (falls back to in-memory) when Redis dies, because rate limiting is a quality-of-service concern. Same pattern, opposite policy — the difference is the criticality of what the dependency protects.

### 3. Load tests (k6, 10 VUs, 40s — same methodology as go-auth-service)
| Scenario | Throughput | p50 | p95 | Bottleneck |
|---|---|---|---|---|
| `GET /api/notes` warm cache | ~91.8 req/s | 6.3ms | 13.4ms | Tomcat + JSON serialization |
| `GET /api/notes` cold cache (`APP_CACHE_MAX_TTL_SECONDS=0`) | ~92.9 req/s | 6.0ms | 9.7ms | Tomcat + JSON serialization + in-network gRPC call |

Cold cache is forced with `APP_CACHE_MAX_TTL_SECONDS=0` rather than minting a token per request — the Go `/login` endpoint is rate-limited (5/60s per IP), and hammering bcrypt would measure the wrong service anyway.

**Honest reading: the two numbers are statistically indistinguishable.** On a single machine, container-to-container gRPC over the compose network is sub-millisecond — cheap enough that the cache doesn't move throughput or latency at this scale. That's not a wasted feature, it's the expected result: the cache's actual payoff is **availability during an outage** (a cached token keeps serving 200s while the auth service is down — see the circuit-breaker demo below) and **avoiding load on the auth service**, not raw latency on a healthy same-host deployment. The gap would show up at real network latency (cross-AZ, cross-region) or under auth-service load, neither of which this local setup exercises.

## API

All endpoints require `Authorization: Bearer <JWT>` from the Go service.

| Method | Path | Description |
|---|---|---|
| GET | `/api/me` | Authenticated user's ID and roles |
| POST | `/api/notes` | Create a note |
| GET | `/api/notes` | List own notes |
| GET | `/api/notes/{id}` | Get own note (404 if not owned) |
| PUT | `/api/notes/{id}` | Update own note |
| DELETE | `/api/notes/{id}` | Delete own note |
| GET | `/api/admin/notes` | All notes (admin role only) |
| GET | `/actuator/health` | Health incl. circuit breaker state (public) |
| GET | `/actuator/prometheus` | Prometheus metrics (public) |

```bash
# 1. Get a token from the Go service (seeded admin)
TOKEN=$(curl -s -X POST http://localhost:8080/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@app.com","password":"admin123"}' | jq -r .access_token)

# 2. Use it against the Java service
curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/me
curl -X POST http://localhost:8081/api/notes \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"title":"First note","body":"Hello"}'
```

## Auth semantics

| Situation | Response |
|---|---|
| No `Authorization` header | 403 (Spring Security default for anonymous) |
| Token invalid / expired / revoked (`valid=false` from Go) | 401 with reason |
| Auth service down or circuit open, token not cached | **503 + `Retry-After`** (fail closed) |
| Auth service down, token still cached | 200 — cached tokens ride out short outages |
| Valid token, someone else's note | 404 (no existence leak) |
| Valid non-admin token on `/api/admin/**` | 403 |

## Running locally

Prereqs: Docker (with compose). The compose file builds the Go service from a sibling checkout at `../4. go-auth-service`.

```bash
docker compose up --build
```

This starts: Go auth service (HTTP :8080, gRPC :9090 published as :50051), its Postgres (:5435) + Redis (:6381) + a one-shot SQL-migration job, the Java Notes API (:8081), and its own Postgres (:5436).

Run the tests (unit + Testcontainers integration — needs Docker):

```bash
./mvnw verify
```

## Configuration

| Env var / property | Default | Purpose |
|---|---|---|
| `GRPC_AUTH_HOST` / `grpc.auth.host` | `auth-api` | Auth service gRPC host |
| `GRPC_AUTH_PORT` / `grpc.auth.port` | `9090` | Auth service gRPC port |
| `APP_CACHE_MAX_TTL_SECONDS` | `60` | Validation-cache TTL cap (0 disables caching) |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5436/notes_db` | Notes DB |
| `resilience4j.circuitbreaker.instances.authService.*` | see `application.properties` | Breaker tuning |

## Design decisions & tradeoffs

| Decision | Alternative | Why |
|---|---|---|
| Plain `grpc-java` + `@Configuration`-managed channel | net.devh spring-boot starter | Less magic; the channel lifecycle is explicit and explainable |
| Ownership in the service layer (`findByIdAndOwnerId`) | Controller-level checks | Can't be bypassed by a new controller; 404 not 403 avoids existence leaks |
| Cache successes only, keyed by token hash | Cache negative results too | A failed validation is cheap to repeat; caching negatives risks caching transient failures |
| Fail closed (503) on auth outage | Fail open, serve stale | A resource API must not serve unauthenticated data; contrast with Go's fail-open rate limiter |
| In-process gRPC stub in tests (`InProcessServerBuilder`) | Spinning up the real Go binary | Deterministic, fast, still exercises the real generated stubs and filter wiring |
| Unverified local read of JWT `exp` for cache TTL | Full local JWT verification | Verification is the auth service's contract; duplicating the secret here would couple the services |

## Limitations

- Revoked tokens remain valid here for up to the cache TTL (60s) — see tradeoff above.
- The Java service trusts the Go service's role strings (`admin`/`user`) as-is.
- Single instance of each service; no horizontal scaling story (deliberately out of scope).
- Refresh tokens are Go-only; clients refresh against the Go service directly.

## Project structure

```
src/
├── main/
│   ├── java/com/ashraf/notesapi/
│   │   ├── config/        # gRPC channel, Caffeine cache, Spring Security chain
│   │   ├── controller/    # Notes CRUD, /api/me, admin endpoint
│   │   ├── dto/           # Request/response records + bean validation
│   │   ├── entity/        # Note JPA entity
│   │   ├── exception/     # 404/400 handling
│   │   ├── repository/    # Spring Data JPA (findByIdAndOwnerId etc.)
│   │   ├── security/      # gRPC auth filter, validation service, circuit-broken client
│   │   └── service/       # Ownership-enforcing business logic
│   ├── proto/auth.proto   # Copied verbatim from go-auth-service
│   └── resources/db/migration/  # Flyway
└── test/
    ├── integration/       # Testcontainers Postgres + in-process gRPC fake auth service
    ├── support/           # FakeAuthService, GrpcTestConfig, FakeJwt, base class
    └── unit/              # Cache TTL, JWT parsing, fail-closed logic, ownership
```
