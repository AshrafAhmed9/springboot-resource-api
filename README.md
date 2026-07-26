# Spring Boot Notes API

[![CI](https://github.com/AshrafAhmed9/springboot-resource-api/actions/workflows/ci.yml/badge.svg)](https://github.com/AshrafAhmed9/springboot-resource-api/actions/workflows/ci.yml)

A Spring Boot service that keeps its own data (notes) but doesn't do its own auth — instead it checks every request against my separate [Go auth service](https://github.com/AshrafAhmed9/go-auth-service) over gRPC. So it's a two-language microservices setup where one service owns identity and the other owns resources.

The notes CRUD itself is boring on purpose. The part I actually cared about is everything around the auth call: caching validations so I'm not hitting the auth service on every request, handling the auth service being down without leaking data, and measuring whether the cache is even worth it.

30 tests · Testcontainers + in-process gRPC integration tests · k6 load-tested

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

- You log in against the Go service and send the JWT here.
- A custom Spring Security `OncePerRequestFilter` calls the Go service's gRPC `ValidateToken`, and if it's good, puts the user ID and role into the `SecurityContext`.
- You can only see and edit your own notes. That's enforced in the service layer with `findByIdAndOwnerId`, and someone else's note returns 404 rather than 403 so you can't probe which IDs exist.

## The parts I actually cared about

### Caching validations
Every good validation goes into a Caffeine cache, keyed by `SHA-256(token)` (I don't want raw tokens sitting in memory). The TTL is `min(60s, whatever's left on the token)`. To figure out "whatever's left" I read the `exp` claim locally without checking the signature — verifying the signature is the Go service's job, and I only need the expiry to decide how long to cache.

The catch: the Go service checks each token's `jti` against a Redis blacklist, so it can revoke tokens instantly. My cache skips that check, which means a token I've already cached stays usable here for up to 60 seconds after it's revoked. I decided that was an acceptable trade for not hammering the auth service, and the 60s TTL caps how stale things can get. If instant revocation mattered more I'd shorten or drop the cache.

### What happens when the auth service is down
The gRPC call has a 2-second deadline and a Resilience4j circuit breaker (opens at 50% failures over a 10-call window, tries again after 10s). If the auth service is unreachable and I don't have the token cached, I return 503 with a `Retry-After` header. I'd rather refuse the request than serve data I couldn't authorize.

This is the opposite of what the Go service does with its rate limiter — if Redis dies there, it falls back to in-memory limits instead of blocking everyone. Different call, but it makes sense: rate limiting is a nice-to-have, auth isn't.

### Is the cache even worth it? (load test)
k6, 10 VUs, 40s each, same setup I used on the Go service:

| Scenario | Throughput | p50 | p95 |
|---|---|---|---|
| `GET /api/notes` warm cache | ~91.8 req/s | 6.3ms | 13.4ms |
| `GET /api/notes` cold cache (`APP_CACHE_MAX_TTL_SECONDS=0`) | ~92.9 req/s | 6.0ms | 9.7ms |

For the cold run I set `APP_CACHE_MAX_TTL_SECONDS=0` to turn the cache off, instead of logging in fresh every request — the Go `/login` is rate-limited (5 per 60s per IP) and would've just measured bcrypt anyway.

The honest result: the two numbers are basically the same. On one machine, gRPC between containers on the compose network is well under a millisecond, so the cache doesn't buy you anything on throughput here. That doesn't mean it's pointless — its real value shows up when the auth service is down (cached tokens keep working, see above) and in not piling load onto the auth service. You'd see an actual latency gap if the two services were on different machines or the auth service were under real load, neither of which happens on a laptop.

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
| GET | `/actuator/health` | Health incl. circuit breaker state (public) |

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

## Running locally

Prereqs: Docker (with compose). The compose file builds the Go service from a sibling checkout at `../4. go-auth-service`.

```bash
docker compose up --build
```

That brings up the Go auth service (HTTP :8080, gRPC :9090 published on :50051), its Postgres (:5435) and Redis (:6381), a one-shot job that runs the Go service's SQL migrations, this Notes API (:8081), and its own Postgres (:5436).

Run the tests (unit + Testcontainers integration, so you need Docker):

```bash
./mvnw verify
```

Testcontainers needs a Docker Engine API version of at least 1.44 (Docker Desktop's "Allow the default Docker socket to be used" setting on, and a reasonably current Docker Desktop). This project pins Testcontainers 2.0.x specifically for that compatibility.

## Configuration

| Env var / property | Default | Purpose |
|---|---|---|
| `GRPC_AUTH_HOST` / `grpc.auth.host` | `auth-api` | Auth service gRPC host |
| `GRPC_AUTH_PORT` / `grpc.auth.port` | `9090` | Auth service gRPC port |
| `APP_CACHE_MAX_TTL_SECONDS` | `60` | Validation-cache TTL cap (0 disables caching) |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5436/notes_db` | Notes DB |
| `resilience4j.circuitbreaker.instances.authService.*` | see `application.properties` | Breaker tuning |

## A few decisions worth explaining

| What I did | Instead of | Why |
|---|---|---|
| Plain `grpc-java` with a `@Configuration` channel bean | The net.devh Spring Boot starter | Less magic to reason about; I can see exactly where the channel is created and torn down |
| Ownership check in the service layer (`findByIdAndOwnerId`) | Checking in the controller | A new controller can't accidentally skip it, and returning 404 (not 403) means you can't tell which note IDs exist |
| Only cache successes, keyed by the token hash | Also caching failures | Re-checking a bad token is cheap, and caching failures could pin a temporary blip in place |
| Return 503 when auth is down and nothing's cached | Serving stale/anything | I won't hand back data I couldn't authorize (opposite of the Go rate limiter, which fails open) |
| In-process gRPC stub in tests (`InProcessServerBuilder`) | Booting the real Go binary for tests | Fast and deterministic, and it still runs the real generated stubs and the real filter |
| Read the JWT `exp` locally without verifying the signature | Fully verifying the JWT here too | Verification belongs to the auth service; copying its secret here would couple the two services |
| `AuthGrpcClient` is its own class | Calling gRPC from inside `AuthValidationService` | Spring's proxy-based `@CircuitBreaker` is bypassed by self-invocation — a call from one method to another in the same class never goes through the proxy, so the breaker would silently do nothing |

## Things it doesn't do

- No role-based access control — this version doesn't have an admin-only endpoint. The interesting engineering here is the auth integration (cache, circuit breaker, ownership), not RBAC.
- A revoked token can still work here for up to the cache TTL (60s) — covered above.
- It trusts the Go service's role strings (`admin`/`user`) without questioning them.
- One instance of each service. No horizontal scaling — I left that out on purpose.
- No refresh handling; refresh tokens live in the Go service and clients refresh there directly.
- No Prometheus/metrics export — that's demonstrated in other projects of mine, not this one.

## Project structure

```
src/
├── main/java/com/ashraf/notesapi/     one flat package — reading top to bottom
│   ├── NotesApiApplication.java       is the request's actual path
│   ├── SecurityConfig.java            filter → validation service → gRPC client,
│   ├── GrpcAuthFilter.java            then controller → service → repository
│   ├── AuthValidationService.java
│   ├── AuthGrpcClient.java
│   ├── TokenCache.java
│   ├── GrpcConfig.java
│   ├── Note.java
│   ├── NoteRepository.java
│   ├── NoteService.java
│   ├── NoteController.java
│   └── ApiExceptionHandler.java
│   proto/auth.proto                   Copied verbatim from go-auth-service
│   resources/db/migration/            Flyway
└── test/java/com/ashraf/notesapi/
    ├── support/                       FakeAuthService, GrpcTestConfig, FakeJwt, base class
    └── *Test.java                     30 tests (17 unit + 13 Testcontainers/in-process-gRPC integration)
```
