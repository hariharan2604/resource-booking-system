# Resource Booking System

A RESTful API for booking resources (rooms, vehicles, equipment) with JWT-based
authentication and role-based access control (RBAC).

Built with **Spring Boot 3.3**, **Java 17**, **Spring Security**, **JWT**, and
**MySQL** via **Spring Data JPA / Hibernate**.

## Features

- JWT login at `POST /auth/login`
- Two roles: `ADMIN` and `USER`, enforced via Spring Security + `@PreAuthorize`
- `ADMIN`: full CRUD on resources and reservations
- `USER`: read-only on resources; can create reservations and view/cancel only their own
- Reservation identity is **always** resolved from the JWT principal server-side —
  a `USER`'s request body cannot impersonate another user
- Reservation statuses: `PENDING`, `CONFIRMED`, `CANCELLED`
- Reservation price stored as `DECIMAL(10,2)`, computed from the resource's hourly
  rate × booked duration
- Filtering by `status`, `minPrice`, `maxPrice`
- Pagination (`page`, `size`) and sorting (`sort=field,dir`) on list endpoints
- Structured JSON error responses for validation failures, 401s, 403s, 404s, 409s
- Swagger / OpenAPI UI with a bearer-token "Authorize" flow
- Seed data: 3 test users, 3 sample resources
- Works with MySQL or an in-memory H2 profile for zero-setup trials

## Project layout

```
src/main/java/com/example/booking/
  config/          Security, OpenAPI, and data-seeding configuration
  controller/      REST controllers (Auth, Resource, Reservation)
  dto/             Request/response payloads with Bean Validation
  entity/          JPA entities (User, Resource, Reservation) + enums
  exception/       Custom exceptions + global @RestControllerAdvice handler
  repository/      Spring Data JPA repositories
  security/        JWT util, auth filter, UserDetails, entry points
  service/         Business logic (Auth, Resource, Reservation)
  specification/   JPA Specification for dynamic reservation filtering
src/main/resources/
  application.yml            base config (profile-driven, all env-overridable)
  application-mysql.yml      MySQL datasource
  application-h2.yml         in-memory H2 for zero-setup local runs
postman_collection.json      importable Postman collection
.env.example                 template for local environment variables
```

## Prerequisites

- Java 17+
- Maven 3.8+
- A running MySQL instance (or skip this and use the bundled H2
  profile for a zero-setup trial)

## Quick start (H2, no external database needed)

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=h2
```

The app starts on `http://localhost:8080`. An in-memory H2 console is available
at `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:booking_db`, user
`sa`, empty password) — data resets on every restart.

## Running with Docker and MySQL

The complete application stack uses MySQL 8.4:

```bash
docker compose up --build
```

The API starts at `http://localhost:8080`. Stop it with `docker compose down`.

## Running against an existing MySQL instance

1. Create a database (or let the driver create it — the MySQL profile's JDBC
   URL includes `createDatabaseIfNotExist=true`):
   ```sql
   CREATE DATABASE booking_db;
   ```
2. Set environment variables:
   ```bash
   export SPRING_PROFILES_ACTIVE=mysql
   export DB_HOST=localhost
   export DB_PORT=3306
   export DB_NAME=booking_db
   export DB_USERNAME=root
   export DB_PASSWORD=root
   ```
3. Run:
   ```bash
   mvn spring-boot:run
   ```

## Building a runnable jar

```bash
mvn clean package
java -jar target/resource-booking-system.jar --spring.profiles.active=mysql
```

## Environment variables reference

| Variable              | Default (dev only)         | Description                                   |
|------------------------|-----------------------------|------------------------------------------------|
| `SPRING_PROFILES_ACTIVE` | `mysql`                   | `mysql` or `h2`                                  |
| `DB_HOST`              | `localhost`                 | Database host                                  |
| `DB_PORT`              | `3306`                      | Database port                                  |
| `DB_NAME`              | `booking_db`                | Database name                                  |
| `DB_USERNAME`          | `root`                      | Database user                                  |
| `DB_PASSWORD`          | `root`                      | Database password                              |
| `JWT_SECRET`           | *(dev placeholder — see below)* | Base64-encoded HMAC secret, ≥256 bits      |
| `JWT_EXPIRATION_MS`    | `86400000` (24h)            | Token lifetime in milliseconds                 |
| `SERVER_PORT`          | `8080`                      | HTTP port                                      |
| `DDL_AUTO`             | `update`                    | Hibernate `ddl-auto` mode                      |
| `SHOW_SQL`             | `false`                     | Log generated SQL                              |
| `LOG_LEVEL`            | `INFO`                      | Log level for `com.example.booking`            |

## Test coverage

Run the integration suite and generate the JaCoCo report:

```bash
mvn clean verify
```

Open `target/site/jacoco/index.html` to inspect line and branch coverage.

## CI/CD with GitHub Actions

The workflow at `.github/workflows/ci-cd.yml` runs Maven verification, uploads
the JaCoCo report, and validates the Docker Compose configuration on pushes and
pull requests. Pushes to `master` also build and publish the application image to
GitHub Container Registry:

```text
ghcr.io/<owner>/<repository>:latest
```

The workflow uses GitHub's built-in `GITHUB_TOKEN`; enable package write access
for the repository workflow if publishing is blocked by repository settings.

**Never use the bundled default `JWT_SECRET` in production.** Generate your own:

```bash
openssl rand -base64 64
```

## Seed users

The app seeds these accounts on first startup (idempotent — safe on every restart):

| Username | Password  | Role  |
|----------|-----------|-------|
| `admin`  | `admin123`| ADMIN |
| `user`   | `user123` | USER  |
| `alice`  | `alice123`| USER  |

It also seeds 3 sample resources: a conference room, a vehicle, and a piece of
equipment.

## API documentation

Once running, Swagger UI is available at:

```
http://localhost:8080/swagger-ui.html
```

Raw OpenAPI JSON at `http://localhost:8080/v3/api-docs`. Click **Authorize** in
the UI and paste a JWT (obtained from `/auth/login`) as `Bearer <token>` to try
authenticated endpoints interactively.

A ready-to-import Postman collection is included at `postman_collection.json`
— it covers login, resource CRUD, and reservation CRUD/filtering, including a
couple of requests that intentionally demonstrate expected `403` responses.

## API overview

### Authentication and user registration

| Method | Path             | Access       | Description                         |
|--------|------------------|--------------|-------------------------------------|
| POST   | `/auth/register` | Public       | Creates a `USER` account            |
| POST   | `/auth/login`    | Public       | Returns a JWT                       |
| POST   | `/api/users`     | ADMIN        | Creates a `USER` or `ADMIN` account |

Public registration can never assign the `ADMIN` role. Admin accounts must be
created by an existing administrator or provisioned by the deployment seed.

```bash
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"newuser","password":"password123"}'
```

### Auth

| Method | Path           | Access | Description        |
|--------|----------------|--------|---------------------|
| POST   | `/auth/login`  | Public | Returns a JWT       |

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

### Resources

| Method | Path                    | Access        | Description               |
|--------|-------------------------|---------------|----------------------------|
| GET    | `/api/resources`        | Any authenticated | List, paginated          |
| GET    | `/api/resources/{id}`   | Any authenticated | Get one                  |
| POST   | `/api/resources`        | ADMIN          | Create                    |
| PUT    | `/api/resources/{id}`   | ADMIN          | Full update               |
| DELETE | `/api/resources/{id}`   | ADMIN          | Delete                    |

### Reservations

| Method | Path                              | Access               | Description                          |
|--------|------------------------------------|----------------------|----------------------------------------|
| GET    | `/api/reservations`                | Any authenticated     | ADMIN sees all, USER sees only their own |
| GET    | `/api/reservations/{id}`           | Owner or ADMIN        | Get one                                |
| POST   | `/api/reservations`                | Any authenticated     | Create (owner = JWT subject)           |
| PATCH  | `/api/reservations/{id}/cancel`    | Owner or ADMIN        | Cancel                                 |
| PUT    | `/api/reservations/{id}`           | ADMIN                 | Full update (resource, times, status)  |
| DELETE | `/api/reservations/{id}`           | ADMIN                 | Delete                                 |

**Filtering, pagination, sorting** (on `GET /api/reservations`):

```
GET /api/reservations?status=PENDING&minPrice=10&maxPrice=100&page=0&size=20&sort=price,desc
```

- `status`: one of `PENDING`, `CONFIRMED`, `CANCELLED`
- `minPrice` / `maxPrice`: decimal bounds, inclusive
- `page` / `size`: standard Spring Data pagination (0-indexed)
- `sort`: `field,asc|desc`, repeatable for multi-field sort

Example creating a reservation as a logged-in `USER` (note: no `userId` field —
the owner is taken from the JWT):

```bash
curl -X POST http://localhost:8080/api/reservations \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
        "resourceId": 1,
        "startTime": "2027-01-10T09:00:00",
        "endTime": "2027-01-10T11:00:00"
      }'
```

### Error response shape

```json
{
  "timestamp": "2026-08-28T10:15:00",
  "status": 400,
  "error": "Validation Failed",
  "message": "One or more fields are invalid",
  "path": "/api/reservations",
  "fieldErrors": {
    "startTime": "startTime must be in the future"
  }
}
```

## Security notes

- Passwords are hashed with BCrypt; plaintext is never stored or logged.
- Sessions are stateless — every request is authenticated by JWT, no server-side
  session state.
- RBAC is enforced at two layers: URL-pattern rules in `SecurityConfig` (coarse)
  and `@PreAuthorize`/service-layer checks (fine-grained, e.g. "own reservation
  only").
- A `USER`'s reservation ownership is derived exclusively from the authenticated
  principal (`UserPrincipal` built from the JWT `sub` claim) — any `userId` sent
  in a request body is ignored unless the caller is an `ADMIN` explicitly booking
  on someone else's behalf.

## Tests

```bash
mvn test
```

Includes a smoke test suite covering successful login, invalid-credential
rejection, and unauthenticated access being blocked with `401`.

## Notes on the price calculation

`Resource.pricePerHour` × booked duration (in hours) = `Reservation.price`,
rounded to 2 decimal places. This happens server-side on create/update — clients
never supply the reservation price directly, which keeps it consistent with the
resource's current rate and the actual booked time window.
