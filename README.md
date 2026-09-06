# TicketRush

TicketRush is a high-concurrency event ticket booking system designed to
handle flash-sale traffic without overselling tickets.

## Problem

Thousands of users may attempt to purchase a limited number of seats
at exactly the same moment.

The system must guarantee:

- No overselling
- No double booking
- Temporary seat holds
- Payment retry safety
- Idempotent requests
- Live seat availability
- Protection against abusive traffic
- Reliable operation under heavy load

## Tech Stack

- Java 21
- Spring Boot 3
- PostgreSQL
- Flyway
- Docker
- Maven
- JUnit
- GitHub Actions

## Current Status

### Phase 0 — Foundation

- [x] Spring Boot application
- [x] PostgreSQL via Docker
- [x] Flyway migration
- [x] Users table
- [x] Health endpoint
- [x] JUnit test
- [x] CI pipeline

## Running Locally

Start PostgreSQL:

```bash
docker compose up -d
```

Run the application (Windows):

```powershell
.\mvnw.cmd spring-boot:run
```

Run the application (Mac/Linux):

```bash
./mvnw spring-boot:run
```

Run tests:

```bash
./mvnw test
```
