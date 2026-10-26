# Demonstration checklist

Keep the final recording under 12 minutes. Use the root README to start all components and Android. Record actual observations, including unreliable behaviour.

| Time | Demonstration |
| --- | --- |
| 0–1 min | Repository version, startup commands and component readiness |
| 1–3 min | Android customer books; open web list updates; technician starts diagnosis |
| 3–5 min | Quote, customer decline, revised quote and approval; open web page enables repair |
| 5–6 min | READY status and Android notification; tap opens correct repair |
| 6–7 min | Disconnect browser network through developer tools, change state elsewhere, reconnect and recover current snapshot |
| 7–8 min | Invalid input and invalid transition; API failure feedback, restart and retained data |
| 8–10 min | Consumer downtime and backlog processing; broker outage/outbox recovery; crash/redelivery evidence |
| 10–11 min | Response-time target, conditions, actual result and limitations |

`node scripts/verify.mjs` saves real crash evidence: `consumer-crash.log` shows DB COMMITTED before process exit 23. `consumer-redelivery.log` shows DUPLICATE SKIPPED, redelivered=true and JMS COMMITTED. One receipt remains for that eventId.

To repeat manually, stop the ordinary consumer, leave the broker running, create a ticket and run:

```powershell
java -jar messaging/target/repair-messaging-0.0.1-SNAPSHOT.jar consume --crash-after-save
# Expected process exit code: 23
java -jar messaging/target/repair-messaging-0.0.1-SNAPSHOT.jar consume --once
.\scripts\run.ps1 Receipts
```

The crash command intentionally stops its own JVM. Avoid competing consumers during this experiment.

## Device checks to record

- APK installs and all three screens open.
- Booking and quote decisions work against the running backend.
- Loading, empty list, invalid input, offline and retry states are usable.
- Notification allowed: relevant change produces notification and tap opens correct repair.
- Notification denied: workflow works and banner opens settings.
- Check Android 12 and Android 13+ permission behaviour where available.
- Rotation, keyboard, scrolling, larger text and long descriptions remain usable.
- A notification opens the correct repair when another ticket is already open.

Build and lint success do not replace device checks. Keep screenshots/recording timestamps and expected versus actual results.
