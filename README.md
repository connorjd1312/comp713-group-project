# Repair Desk

COMP713 Assessment 3: customers book and track repairs in Android, while technicians manage tickets and quotes on the web.

Repository: https://github.com/kiwilu/comp713-group-project

MVP release: `mvp-assessment3`

## Requirements

- Java 21
- Android Studio with SDK 34 and an Android 12+ emulator/device
- Node.js 22+ for the extra test scripts

## Start the application

From the project folder, build once:

```powershell
.\scripts\run.ps1 Build
```

Then run each command in a separate terminal:

```powershell
.\scripts\run.ps1 Broker
.\scripts\run.ps1 Api
.\scripts\run.ps1 Consumer
```

Open [Repair Desk](http://localhost:8080/tickets.html). Stop each process with **Ctrl+C**.

## Run Android

1. Open the `android` folder in Android Studio and run the app.
2. Enter `http://10.0.2.2:8080` as the server address on the emulator.
3. Use `ben@example.com` to try a pending quote, or enter your own email and book a repair.

Other demo emails: `alice@example.com` and `chloe@example.com`. No passwords are required. On a physical phone, use your computer's local IP instead of `10.0.2.2`.

## Try the workflow

Book a repair in Android, open it on the web, start diagnosis and send a quote. Approve or decline the quote in Android. The web page updates automatically, and an approved quote allows repair to begin. Android notifications report new quotes and status changes.

## Tests

```powershell
.\scripts\run.ps1 Test
node --test scripts/web-tests.mjs
node scripts/verify.mjs
```

Build first. The last command tests the complete system, recovery and response time. See [test results](docs/VERIFICATION.md) and the [demo checklist](docs/DEMO.md).

## Notes

- Defaults: API port `8080`, broker port `61616`, queue `repair.events`. No environment variables are required. Override with `SERVER_PORT`, `BROKER_URL` or `BROKER_QUEUE` if needed.
- Databases are created automatically in `backend/data/` and `data/`. Keep these folders to retain saved repairs and receipts.
- This is a local demo without authentication. Background notifications may be delayed by Android.
- Built on the Assessment 2 project `kiwilu/repair-tickets` at baseline `328aeea`. Architecture and limitations are in the [technical report](docs/TECHNICAL_REPORT.md).

