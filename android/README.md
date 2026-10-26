# Repair Desk – Android customer app

Native Android app (Java, Android platform APIs only) for the **customer** role. It talks to the
backend REST API in `../backend`.

| Screen | What it does | API calls |
|---|---|---|
| `MainActivity` | Sign in with email, list my repairs (loading / empty / error + retry), notification permission | `GET /api/tickets?email=` |
| `NewTicketActivity` | Book a repair; server validation errors shown on each field | `POST /api/tickets` |
| `TicketDetailActivity` | Progress, history, **approve / decline the technician's quote**, cancel while RECEIVED | `GET /api/tickets/{id}`, `POST /api/tickets/{id}/quote/decision`, `DELETE /api/tickets/{id}` |

**Mobile capability – notifications.** `TicketNotifier` posts a notification when a technician sends a
quote or changes the status (e.g. *"Your MacBook Air M1 is ready for pickup"*). Tapping it opens the
ticket. The check runs every 15 s while the app process is alive (`RepairDeskApp`) and every 15 min via
`JobScheduler` (`UpdateCheckJobService`) otherwise. If the notification permission is denied, the app
keeps working and shows a banner that links to the notification settings.

## Run

1. Start the backend: `cd ../backend && ./mvnw spring-boot:run` (port 8080).
2. Open this `android/` folder in Android Studio (JDK 17+, Android SDK 34) and let Gradle sync.
3. Run on an emulator (Android 12 / API 31 or newer). The default server address is
   `http://10.0.2.2:8080`, which is the host PC as seen from the emulator.
   On a physical phone, enter `http://<PC's LAN IP>:8080` on the sign-in screen (same Wi-Fi, firewall open).
4. Sign in as a demo customer, e.g. `ben@example.com` (ticket in DIAGNOSING with a pending quote).

## Demo of the shared workflow

1. **Android (Ben):** open the MacBook ticket → the $229 quote is waiting → *Approve*.
2. **Web (technician, `ticket.html?id=2`):** reload → quote shows APPROVED → *Move to REPAIRING* (blocked with
   HTTP 409 before approval).
3. Put the Android app in the background; on the web move the ticket to READY → a notification appears within ~15 s.
