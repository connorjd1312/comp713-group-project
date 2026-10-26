# Repair Desk — Assessment 3 MVP

A repair-ticket system with a technician web client, native Android customer app, persistent Spring REST API, WebSocket updates, durable broker and independent audit-receipt consumer.

Repository: https://github.com/kiwilu/comp713-group-project

Final MVP version identifier: `mvp-assessment3` (local Git tag; resolve and include its commit hash in the submitted report).

## Prerequisites

- JDK 21 for backend and messaging.
- Android Studio, SDK Platform 34, Build Tools 34.0.0, JDK 17/21 for Gradle, and an API 31+ emulator/device.
- Node.js 22+ for verification scripts (developed using Node 24).
- Internet for the first Maven/Gradle dependency download. Wrappers are included.
- Several GB for Android tools; broker operation preserves at least 256 MiB free disk space.

## Build and run

From the repository root in PowerShell:

```powershell
.\scripts\run.ps1 Build
```

Run each component in its own terminal, from the repository root:

```powershell
# Terminal 1 — loopback port 61616
.\scripts\run.ps1 Broker
# Terminal 2 — HTTP/WebSocket port 8080
.\scripts\run.ps1 Api
# Terminal 3 — independent consumer
.\scripts\run.ps1 Consumer
```

Wait for `BROKER READY`, `Started RepairTicketsApplication` and `CONSUMER READY`. Stop foreground components with Ctrl+C. No Docker, cloud account or external database is required.

Open http://localhost:8080/index.html to book a repair and http://localhost:8080/tickets.html for the technician list. Click a ticket row for details and quote/status controls.

macOS/Linux: build using `./backend/mvnw -f backend/pom.xml clean package` and `./backend/mvnw -f messaging/pom.xml clean package`. Run `java -jar messaging/target/repair-messaging-0.0.1-SNAPSHOT.jar broker` or `consume` from the root. Run `java -jar target/repair-tickets-0.0.1-SNAPSHOT.jar` from `backend/`.

### Android

1. Open `android/` in Android Studio, install SDK Platform 34 and Build Tools 34.0.0, select JDK 17/21 for Gradle, and sync.
2. Run on an API 31+ emulator/device.
3. Use `http://10.0.2.2:8080` on the emulator. On a physical phone use `http://<PC LAN IP>:8080` on the same Wi-Fi and allow the backend through the firewall.
4. Continue with a demo email below or a new email. This selects a customer; it is not password authentication.
5. Allow notifications on Android 13+, or continue using the app with the permission-denied banner.

Command-line build: from `android/`, run `.\gradlew.bat assembleDebug lintDebug` (Unix: `./gradlew assembleDebug lintDebug`). Set `ANDROID_HOME` if Android Studio has not created `local.properties`. APK: `android/app/build/outputs/apk/debug/app-debug.apk`.

### Demo customers

Seeded only when the backend ticket database is empty:

| Email | Starting state |
| --- | --- |
| alice@example.com | New phone repair |
| ben@example.com | Laptop diagnosis, pending $229 quote |
| chloe@example.com | Tablet ready for collection |

There are no passwords or technician accounts. Web pages represent the technician role. Use a trusted local network.

## Shared workflows

1. **Booking and diagnosis:** In Android, select New repair and submit. The open web list updates. On the web, move the ticket to DIAGNOSING and issue a quote. Refresh Android to see the quote.
2. **Quote decision and repair:** Approve the quote in Android. The technician's open page updates through WebSocket and enables repair. Declining blocks repair and allows a revised quote. After approval, advance REPAIRING → READY → COLLECTED. New quotes and relevant status changes produce Android notifications when the update check runs; tapping opens that repair.

Android checks about every 15 seconds while its process is active and requests a 15-minute background job interval; Android can defer background execution. These are local notifications derived from API polling, without guaranteed immediate push delivery. The browser uses WebSocket, with a REST snapshot after reconnection, rather than periodic polling.

## Advanced integration: broker path

Ticket changes and outbox rows commit together. A scheduled publisher sends persistent JSON to `repair.events` and retries failures. The independent consumer owns its receipt database, saves one receipt per eventId, then acknowledges through a JMS transaction commit. Duplicate delivery is allowed; a unique eventId prevents duplicate saved effects.

Inspect publication counts at http://localhost:8080/api/integration/status and consumer results with `.\scripts\run.ps1 Receipts`.

Stop the consumer, change a ticket, restart it and inspect the saved receipts. Stop the broker: ticket operations still work and pending events remain in the outbox. Restarting the broker drains the backlog. See [docs/DEMO.md](docs/DEMO.md) for the crash-and-redelivery experiment.

## Configuration and storage

| Variable | Default | Purpose |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | Backend HTTP/WebSocket |
| `BROKER_URL` | `tcp://127.0.0.1:61616` | Broker listener and client connection |
| `BROKER_QUEUE` | `repair.events` | Destination |
| `MESSAGING_ENABLED` | `true` | Backend publisher; setting false retains outbox capture |
| `REPAIR_DATA_DIR` | `data` relative to launch directory | Broker journal and consumer database |
| `SPRING_DATASOURCE_URL` | `jdbc:h2:file:./data/repairdb` | Backend database |
| `JAVA_HOME`, `ANDROID_HOME` | Machine-specific | Build tools |

The run script launches messaging from the root and the API from `backend/`. Persistent data therefore lives in root `data/` and `backend/data/`. Keep both across restarts. Database tables are created automatically; no manual schema installation is needed. Backend ownership: customers, tickets, history, quotes and outbox. Consumer ownership: receipts; it never queries backend tables.

Local H2 console: http://localhost:8080/h2-console, JDBC URL `jdbc:h2:file:./data/repairdb`, user `sa`, empty password.

## Verification

```powershell
.\scripts\run.ps1 Test
node --test scripts/web-tests.mjs
node scripts/verify.mjs
```

Build both JARs before the full-system script. It uses isolated ports and databases under ignored `test-results/`, starts its own processes and stops them afterward. It checks role interactions, validation, WebSocket recovery, broker outage, durable queue restart, crash/redelivery, duplicate suppression, persistence and response time.

Response-time acceptance target: p95 GET ticket-detail response below 500 ms, 101 tickets, 10 warm-ups and 100 sequential measured requests on the local demo machine. Raw timings, machine details, results and component logs are saved per run. This measures the complete HTTP response and JSON parsing; it does not measure Android rendering or visible browser-update latency.

See [docs/VERIFICATION.md](docs/VERIFICATION.md), [docs/TECHNICAL_REPORT.md](docs/TECHNICAL_REPORT.md) and [docs/DEMO.md](docs/DEMO.md). Device and visual checks must be recorded separately.

## Starting point, limitations and submission

The selected Assessment 2 project is `kiwilu/repair-tickets`, imported at `328aeea` and tagged `a2-baseline`. Retained: booking, status/history, basic web pages and persistence. Added or revised: quotes, Android, notifications, WebSocket recovery, ticket versions, durable outbox, broker/consumer and verification. Preserve original authorship when describing contributions.

Limitations: customer selection is not authenticated login; one backend instance; embedded H2; local plain HTTP and unauthenticated loopback broker; no event retention cleanup or invalid-message operator interface; no guaranteed background notification latency. This is a local demonstration system.

Before submitting, make the final repository version accessible, export reports to PDF/DOCX and record a video of no more than 12 minutes. Include source, tests, wrappers and configuration in the project ZIP. Exclude `target/`, `build/`, `.gradle/`, SDKs, local databases and `test-results/`. Submit the reports outside the ZIP.
