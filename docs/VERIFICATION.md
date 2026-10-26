# MVP verification results

Executed 27 October 2026 against the completed MVP. These are observed automated results; device walkthroughs remain separate.

| Check group | Result |
| --- | --- |
| Backend Maven tests | 32 passed, 0 failures/errors |
| Messaging Maven tests | 2 passed, 0 failures/errors |
| Browser-script tests | 4 passed |
| Separate-process full-system checks | 11 passed |
| Android assembleDebug | Successful; debug APK produced |
| Android lintDebug | Successful; 0 errors, 9 warnings |

Commands: `./backend/mvnw -f backend/pom.xml test package`, the equivalent messaging command, `node --test scripts/web-tests.mjs`, `node scripts/verify.mjs`, and Android `gradlew assembleDebug lintDebug`.

## Executed full-system outcomes

1. Invalid booking returns 400; missing ticket returns 404.
2. Customer booking is readable through REST and produces a notice on a real WebSocket connection.
3. Broker unavailable: ticket creation succeeds and a pending durable outbox event exists.
4. Quote decline, revised quote and approval follow the rules; wrong supplied customer email returns 403; repair is blocked before approval; WebSocket announces approval.
5. Disconnect/change/reconnect followed by the subscription-confirmed REST snapshot returns current READY state.
6. Starting the broker drains pending outbox rows.
7. Broker restart preserves queued events. Consumer crash after database commit exits 23; redelivery skips the saved receipt and commits acknowledgement.
8. A separately running consumer processes queued events. Publishing the same eventId again does not create an additional receipt.
9. Ticket-detail response-time target passes under the conditions below.
10. Killing/restarting the backend preserves ticket status, quote and history.
11. Pending outbox rows survive that restart and are eventually published and consumed.

The browser-script suite checks delayed responses, obsolete errors, ticket versions, approval-dependent repair actions, and subscribe/acknowledge/snapshot recovery. It uses a small DOM test double and is not a visual browser test.

## Response-time requirement

Target: p95 GET ticket-detail HTTP operation below 500 ms. This provides a practical local responsiveness budget for a common read operation.

| Condition or measurement | Observed value |
| --- | --- |
| Machine | Intel Core i5-9400F @ 2.90 GHz, Windows 10 build 19045 |
| Runtime | Java 21, Node 24.15.0 |
| Dataset | 101 persistent tickets |
| Warm-up | 10 requests |
| Measured sample | 100 sequential requests, concurrency 1 |
| Boundary | Full HTTP response and JSON parsing on loopback |
| p95 | **5.11 ms** |
| Acceptance | **PASS**, below 500 ms |

Raw sorted timings and environment fields are in [evidence/response-time.json](evidence/response-time.json). Full-system outcomes and timestamp are in [evidence/system-checks.json](evidence/system-checks.json). Raw component logs remain in `test-results/2026-10-26T22-20-13-481Z/` locally; the directory name is UTC (27 October in New Zealand).

This measurement does not establish performance under concurrent load, Android rendering latency, visible WebSocket latency or background notification delivery time.

## Findings corrected during verification

- Duplicate WebSocket subscription could register multiple wrappers; registration now uses session ID.
- Overlapping REST refreshes could regress the display; versions and request generations guard them.
- Android 12 should not request the Android 13 notification permission; permission checks now distinguish API levels.
- Windows Gradle wrapper did not propagate a build failure reliably; it now preserves the exit code.
- Keeping the consumer database open removed repeated H2 AUTO_SERVER startup overhead.
- A real process crash exposed H2's delayed write setting. Persistent demonstration databases now use WRITE_DELAY=0 so acknowledged database commits are flushed before the intentional crash.
- Oracle's Windows launcher could leave its JVM child running. The verification script resolves the actual JDK executable and stops the exact processes it starts.

## Remaining manual acceptance

Android device/emulator installation, notification allow/deny and tap navigation, rotation/keyboard/larger text, and a visual cross-client walkthrough have not been executed in this session. Follow [DEMO.md](DEMO.md) and record actual results. Android lint's nine warnings include locale normalisation, accessibility/resource/style suggestions, backup rules and a missing launcher icon; none prevents APK generation.

Complete the group/member details and personal reports, capture the demonstration video, and export the final report documents before submission. No personal contribution or device-test result is implied by this automated evidence.
