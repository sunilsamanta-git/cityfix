# CityFix: Civic Complaint & Issue Tracking System

Citizens report problems (potholes, broken streetlights, water leaks, garbage, drainage), the system routes each
complaint to the right municipal department, officers fix it and post updates, and the citizen can follow progress
with a public tracking ID.

**Problem it solves:** in most cities complaints disappear into phone calls and paper registers. Nobody knows who
owns the problem or what happened to it. CityFix gives every complaint an owner, a status and an audit trail.

## Tech stack
| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3.3, Spring Security (JWT), Spring Data JPA, Bean Validation |
| Database | H2 in-memory (default, zero setup) or MySQL 8 (`mysql` profile) |
| Frontend | HTML, CSS, vanilla JavaScript (served by Spring Boot, no build step) |
| API docs | Swagger UI (springdoc-openapi) |
| Tests | JUnit 5, MockMvc |

## Features
- **Three roles:** Citizen, Officer (one department), Admin. Enforced on the server with `@PreAuthorize`.
- **Auto-routing:** category decides the department (pothole goes to Roads, streetlight to Electrical...). "Other" waits for an admin.
- **Strict status workflow:** `REPORTED -> ASSIGNED -> IN_PROGRESS -> RESOLVED`, or `REJECTED`. Steps cannot be skipped; rules live in `Status.java`.
- **Audit trail:** every change is stored in `StatusHistory` with who, when and a note.
- **Public tracking:** anyone with a tracking ID (like `CF-1A2B3C4D`) can see progress, without login and without seeing the reporter's name.
- **Photo upload:** JPG/PNG/WebP, max 5 MB, stored under random names.
- **Admin dashboard:** counts per status, average hours to resolve, filters, assign/re-assign, create officers.

## Run it
Requirements: JDK 17+ and Maven 3.9+.

```bash
mvn spring-boot:run
```
Open http://localhost:8080 . Swagger UI: http://localhost:8080/swagger-ui.html

Run the tests: `mvn test`

### Use MySQL instead of H2
```bash
docker compose up -d
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

### Demo accounts (created on first start)
| Role | Email | Password |
|---|---|---|
| Citizen | citizen@cityfix.com | Citizen@123 |
| Officer (Roads) | officer.roads@cityfix.com | Officer@123 |
| Officer (Water) | officer.water@cityfix.com | Officer@123 |
| Admin | admin@cityfix.com | Admin@123 |

## Demo script (2 minutes)
1. Sign in as **Citizen**, report a pothole with a photo. Note the tracking ID. It is already "Assigned" to Roads.
2. Sign out, paste the tracking ID in the box on the home page: public tracking works without login.
3. Sign in as **Roads officer**: you only see Roads complaints. Move it to "In progress", then "Resolved" with a note.
4. Sign in as **Admin**: see stats, report an "Other" complaint as the citizen and assign it manually.

## API overview
| Method | Path | Who |
|---|---|---|
| POST | `/api/auth/register`, `/api/auth/login` | public |
| GET | `/api/public/track/{trackingId}` | public |
| POST | `/api/complaints` | citizen |
| POST | `/api/complaints/{id}/photo` | citizen (owner) |
| GET | `/api/complaints/my` | citizen |
| GET | `/api/complaints?status=&departmentId=` | officer (own dept), admin |
| GET | `/api/complaints/{id}` | owner, same-department officer, admin |
| PUT | `/api/complaints/{id}/assign` | admin |
| PUT | `/api/complaints/{id}/status` | officer (own dept), admin |
| GET | `/api/admin/stats` | admin |
| POST | `/api/admin/officers` | admin |
| GET / POST | `/api/departments` | any user / admin |

## Project structure
```
src/main/java/com/cityfix
  config/       SecurityConfig, DataSeeder, OpenApiConfig, WebConfig
  security/     JwtService (create/verify tokens), JwtAuthFilter (reads the Bearer header)
  model/        JPA entities + enums (Status holds the workflow rules)
  repository/   Spring Data interfaces
  service/      business rules: ComplaintService, AuthService, FileStorageService
  controller/   thin REST controllers
  dto/          request/response records (entities are never exposed directly)
  exception/    ApiException + one global JSON error handler
src/main/resources/static   the web UI
```

## Design decisions (good interview answers)
- **Why JWT?** The API is stateless, so it scales horizontally without sessions. The token carries email and role.
- **Why DTOs?** Entities contain password hashes and lazy relations. DTOs control exactly what leaves the API.
- **Why is the workflow inside the `Status` enum?** One place defines the rules, the UI gets `nextStatuses` from the server, and a unit test covers it.
- **How is access controlled?** Two layers: `@PreAuthorize` by role, then `checkAccess()` in the service for ownership and department scope. Role alone is not enough: an officer must not edit another department's complaint.
- **Why a history table?** Citizens need transparency and the city needs accountability. It also gives you the data for "average resolution time".
- **Passwords:** BCrypt hashes, never stored in plain text.
- **Uploads:** content type whitelist, size limit, random file names (prevents path traversal and overwrites).

## Ideas to extend (mention these as "next steps")
- Email/SMS notification when status changes (Spring Mail + `@Async`)
- Map pin using latitude/longitude (Leaflet.js) and duplicate-complaint detection by distance
- SLA deadlines per category with escalation to the admin after N days
- Pagination on the list endpoint, React frontend, Docker image + CI pipeline
