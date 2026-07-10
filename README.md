# Spring Boot Notes API

A Java/Spring Boot resource service that authenticates against the Go auth service via gRPC, demonstrating a polyglot microservices architecture.

## Architecture

```
Client ──REST/JWT──> Spring Boot Notes API ──gRPC ValidateToken──> Go Auth Service
                            │                                              │
                       PostgreSQL                                 PostgreSQL + Redis
```

## Quick Start

Prerequisites: Docker + Docker Compose

```bash
docker compose up
```

This will:
1. Start the Go auth service (port 8080, gRPC 9090)
2. Start the Java notes service (port 8081)
3. Set up both databases and Redis

## API

### Login (via Go service)
```bash
curl -X POST http://localhost:8080/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@app.com","password":"admin123"}'
```

### Get Current User (Java service - requires token)
```bash
curl -H "Authorization: Bearer <JWT_TOKEN>" \
  http://localhost:8081/api/me
```

## Stack

- **Framework**: Spring Boot 3.5
- **Java**: 21
- **Database**: PostgreSQL
- **gRPC**: For inter-service auth validation
- **Security**: Spring Security + custom gRPC filter
- **Migrations**: Flyway

## Development

Build:
```bash
./mvnw clean package
```

Run locally (requires PostgreSQL on localhost:5436 + Go service running):
```bash
./mvnw spring-boot:run
```

## Project Structure

```
src/
├── main/
│   ├── java/com/ashraf/notesapi/
│   │   ├── config/        # gRPC & Security configuration
│   │   ├── controller/    # REST endpoints
│   │   ├── security/      # gRPC auth filter
│   │   └── NotesApiApplication.java
│   ├── proto/             # auth.proto (copied from Go service)
│   └── resources/
│       ├── application.properties
│       └── db/migration/  # Flyway migrations
└── test/
```
