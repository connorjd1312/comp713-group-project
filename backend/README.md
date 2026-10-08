# Repair Desk – Device Repair Ticket System

COMP713 Assessment 2 – Individual Project (**Option A: Distributed Web/API Application**)

A small distributed web application for a phone/laptop repair shop. Customers book a repair through a web form, and technicians move each ticket through a fixed workflow (`RECEIVED → DIAGNOSING → REPAIRING → READY → COLLECTED`). Every status change is stored as history.

Repository: https://github.com/kiwilu/repair-tickets

---

## 1. Software required

| Tool | Version | Notes |
|---|---|---|
| Java JDK | 21 (17+ should also work) | `java -version` to check |
| Maven | not required | the included Maven Wrapper (`./mvnw`) downloads it automatically |
| Web browser | any modern browser | Chrome, Safari, Firefox, Edge |
| Internet | first run only | to download dependencies |

No separate database installation is needed – the project uses an embedded **H2** database stored in a file.

## 2. Setup and start

```bash
git clone https://github.com/kiwilu/repair-tickets.git   # or unzip the submitted folder
cd repair-tickets
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

Wait for `Started RepairTicketsApplication` in the console, then open:

| What | URL |
|---|---|
| Web client – new ticket form | http://localhost:8080/index.html |
| Web client – ticket list (technician view) | http://localhost:8080/tickets.html |
| REST API | http://localhost:8080/api/tickets |
| H2 database console | http://localhost:8080/h2-console |

Stop the server with `Ctrl+C`.

### Configuration values

| Setting | Value | Where |
|---|---|---|
| Port | `8080` | Spring Boot default |
| Database URL | `jdbc:h2:file:./data/repairdb` | `src/main/resources/application.properties` |
| DB user / password | `sa` / *(empty)* | same file |
| Demo data | on (`app.seed-demo-data=true` by default) | 3 sample tickets are added when the database is empty |
| Environment variables | none required | |

**H2 console login:** set *JDBC URL* to `jdbc:h2:file:./data/repairdb`, user `sa`, empty password.

**Reset the data:** stop the server and delete the `data/` folder. It is recreated (with demo data) on the next start.

## 3. Architecture (summary)

```
Browser (HTML + JavaScript fetch)
        │  HTTP + JSON
        ▼
TicketController          ← REST endpoints, @Valid input validation
GlobalExceptionHandler    ← maps errors to 400 / 404 / 409 JSON responses
        ▼
TicketService             ← business rules, @Transactional
        ▼
Spring Data JPA repositories
        ▼
H2 database (file)        ← customers, repair_tickets, ticket_updates
```

```
src/main/java/nz/ac/aut/comp713/repair_tickets/
├── controller/   TicketController, GlobalExceptionHandler
├── service/      TicketService
├── repository/   CustomerRepository, RepairTicketRepository, TicketUpdateRepository
├── model/        Customer, RepairTicket, TicketUpdate, TicketStatus
├── dto/          request / response / error records
├── exception/    NotFoundException, BusinessRuleException
└── config/       DemoDataSeeder
src/main/resources/static/   index.html, tickets.html, ticket.html, app.js, style.css
```

## 4. API endpoints

| Method | Path | Purpose | Success | Errors |
|---|---|---|---|---|
| POST | `/api/tickets` | Create a ticket (creates or reuses the customer by email) | 201 + `Location` | 400 validation / malformed JSON |
| GET | `/api/tickets?status=&email=` | List tickets, optional filters | 200 | 400 invalid status |
| GET | `/api/tickets/{id}` | Ticket details and status history | 200 | 404 |
| PATCH | `/api/tickets/{id}/status` | Move to the next status (`{"status":"…","note":"…"}`) | 200 | 400, 404, 409 skipped step |
| DELETE | `/api/tickets/{id}` | Cancel a ticket (kept as `CANCELLED`) | 204 | 404, 409 already in progress |

All errors use the same JSON shape:

```json
{"timestamp":"…","status":409,"error":"INVALID_TRANSITION",
 "message":"Cannot move ticket 1 from RECEIVED to READY","path":"/api/tickets/1/status","fieldErrors":{}}
```

## 5. Testing

### Automated tests

```bash
./mvnw test
```

16 tests (JUnit 5, in-memory database so the real data is not touched):

- `TicketServiceTests` – business rules: new tickets start as `RECEIVED`, customers are reused by email, history is recorded, skipped steps are rejected, cancellation only while `RECEIVED`, unknown ids → not found, filtering.
- `TicketControllerTests` – HTTP behaviour: 201 on create, 400 with field errors, 400 on malformed JSON and invalid status filter, 404 on unknown id, 200 on valid update, 409 on skipped step, 204 then 409 on cancel.

### Manual tests with the web client

1. Open `index.html`, submit the form empty → field errors from the server are shown under each field (HTTP 400).
2. Submit a valid form → success message with a link to the new ticket.
3. Open the ticket → click **Move to DIAGNOSING** → status and history update.
4. Use **Set status** to choose `READY` on a `RECEIVED` ticket → rejected with HTTP 409.
5. Click **Cancel ticket** on a ticket that is already `DIAGNOSING` → rejected with HTTP 409.
6. Stop the server and click a button → "Cannot reach the server" message (network error handling).
7. Restart the server → all tickets are still there (file persistence).

### Manual tests with curl

```bash
curl -i -X POST localhost:8080/api/tickets -H "Content-Type: application/json" \
  -d '{"name":"Wayne","email":"wayne@example.com","deviceType":"PHONE","deviceModel":"Pixel 7","issueDescription":"Phone will not charge at all"}'

curl -i -X PATCH localhost:8080/api/tickets/1/status -H "Content-Type: application/json" -d '{"status":"READY"}'   # 409
curl -i localhost:8080/api/tickets/999                                                                            # 404
```

## 6. Known limitations

- No login or roles: anyone using the web client can act as a technician. Authentication was outside the scope of this assessment.
- When an existing email is used again, the stored customer name and phone are kept (not updated).
- No optimistic locking: if two technicians update the same ticket at the same moment, the last write wins.
- Listing tickets loads each ticket's history with extra queries (N+1); acceptable for small data, but would need a fetch join for large volumes.
- The H2 file database is designed for a single server instance and is not suitable for production.
