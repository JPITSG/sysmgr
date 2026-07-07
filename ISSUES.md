# System Manager — Audit Findings

Every issue below was personally verified by reading the cited file at the cited line range. Agent claims that did not hold up under re-verification are listed at the bottom as **Dismissed**.

Severity legend:

- 🔴 **Critical** — feature broken, security default backwards, or major battery/data risk
- 🟠 **High** — stability or correctness gap with clear user impact
- 🟡 **Medium** — real defect with limited blast radius, or significant optimization
- ⚪ **Low / Consistency** — design smell, refactor leftover, UX paper-cut

---

## 🔴 Critical

### C1. RemoteLinkService can't start from background when notification is hidden
**File:** `RemoteLinkManager.java:53-67`

```java
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Config.get(context).remoteLinkShowNotification()) {
    context.startForegroundService(intent);
} else {
    context.startService(intent);   // ← throws IllegalStateException from background on API 26+
}
```

`minSdkVersion=26`, so the `SDK_INT >= O` half is always true. The branch path therefore reduces to *"if notification is hidden, use `startService()`."* On Android 8+, calling `startService()` from a background context (e.g. `BootReceiver` after boot, alarm-driven restart from `sync("after-check")`, `RemoteLinkManager.sync()` from a `Config` change handler) throws `IllegalStateException`. The catch swallows it silently. The Remote Link is then dead until the user opens the app.

**Category:** STABILITY

---

### C2. Accessibility service over-subscribes events 24/7 for no benefit
**File:** `app/src/main/res/xml/accessibility_service.xml:2-8`

```xml
android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged|typeWindowsChanged"
android:notificationTimeout="100"
```

`SystemManagerAccessibilityService.onAccessibilityEvent` (line 76-78) is **empty** — every dispatched event is allocated, IPC'd, and discarded. With these subscriptions plus a 100 ms throttle, the OS delivers a steady stream of events whenever the user scrolls, types, or watches any video. The service only needs to *exist* so `performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)` works and so the embedded `WifiChangeMonitor` can run; it doesn't need any event types.

**Category:** OPTIMIZATION (idle battery drain — single biggest available win)

---

### C3. Remote Link defaults to "accept any SSL certificate"
**File:** `Config.java:387-389`

```java
boolean remoteLinkAcceptAnySslCert() {
    return prefs.getBoolean(KEY_REMOTE_LINK_ACCEPT_ANY_SSL_CERT, true);
}
```

Combined with `RemoteWebSocketClient.trustAnySslSocketFactory()` (line 190-210) and `Config.serverBaseUrl()` default `https://server:1234`, a fresh install with the Remote Link enabled trusts arbitrary self-signed / MITM certificates by default. Default should be `false`; user opts in for development.

**Category:** SECURITY / INCONSISTENCY (defaults work against the user)

---

## 🟠 High

### H1. NotificationHistoryStore blocks the listener binder thread with full-file I/O
**File:** `NotificationHistoryStore.java:67-87` (called from `HighPriorityNotificationListener.java:44`)

```java
synchronized (LOCK) {
    List<Entry> entries = readLocked(app);   // reads + parses entire JSON
    entries.add(0, new Entry(...));
    while (entries.size() > MAX_ENTRIES) { ... }
    writeLocked(app, entries);                // serializes + atomic-writes
}
broadcastChanged(app);
```

`onNotificationPosted` runs on a `NotificationListenerService` binder thread. Each posted notification triggers a full JSON read + re-serialization + temp-file write + `fsync` + rename, all under a global lock. With up to 100 entries (each holding title/message/image filename), serialization can be tens of KB. Bursts of notifications serialize on the lock and block the binder thread, risking ANR / dropped binder transactions on slow storage.

**Category:** STABILITY

---

### H2. `WifiInfoReader.read()` runs on the UI thread inside `refreshStatusAndLog`
**File:** `MainActivity.java:1420` (and `WifiInfoReader.java:57-99`)

```java
WifiSnapshot wifi = WifiInfoReader.read(this);          // MainActivity.java:1420 — on UI thread
...
latch.await(1200L, TimeUnit.MILLISECONDS);              // WifiInfoReader.java:86 — blocks up to 1.2s
```

`refreshStatusAndLog()` is called 26+ times in MainActivity (every save, every toggle, every test button, `onResume`, etc.). When the primary readers don't return SSID/BSSID (common on Android 11+ without a recent location update), the method enters the fallback path that registers a `NetworkCallback` and blocks the calling thread up to 1.2 seconds. The user sees jank — and in the worst case, an ANR.

**Category:** STABILITY / OPTIMIZATION

---

### H3. `RemoteWebSocketClient` allocates frame buffer up to 2 GB
**File:** `RemoteWebSocketClient.java:104-112`

```java
if (length > Integer.MAX_VALUE) {
    throw new IOException("frame too large");
}
...
byte[] payload = readFully((int) length);
```

The only upper bound is `Integer.MAX_VALUE` (~2 GB). A malicious or buggy server can claim any payload length up to that limit and `new byte[(int) length]` will trigger `OutOfMemoryError`, crashing the process. Frames should be bounded to a sensible maximum (e.g. 1 MB) and oversize frames should close the connection.

**Category:** STABILITY / SECURITY

---

### H4. `RemoteEventHandler` decodes server-supplied images without size limit
**File:** `RemoteEventHandler.java:184-197`

```java
byte[] data = Base64.decode(imageBase64, Base64.DEFAULT);
...
return BitmapFactory.decodeByteArray(data, 0, data.length);
```

The remote can push arbitrarily large base64 image payloads via a `notification` message. A 10 MB PNG decodes to ~40 MB of ARGB bitmap data. Combined with H3, a single oversized message can OOM-kill the service. Should reject above a threshold and/or use `BitmapFactory.Options.inSampleSize` with bounds.

**Category:** STABILITY / SECURITY

---

### H5. RemoteLink reconnects immediately after a dropped connection
**File:** `RemoteLinkService.java:117-162`

```java
try {
    current.connect();
    connected = true;
    failedConnectsSinceSuccess = 0;     // ← reset on success
    ...
    runConnectedLoop(current);          // throws on read error
} catch (Exception e) {
    if (connected) {
        failedConnectsSinceSuccess = 0; // ← also reset on post-connect failure
        // NO sleep here — loop immediately re-attempts
    } else {
        long delayMillis = failedConnectsSinceSuccess == 0 ? 1000L : reconnectSeconds * 1000L;
        ...
        sleepInterruptibly(delayMillis);
    }
}
```

Backoff only applies to initial-connect failures. When a stable, post-handshake connection drops (server reset, network blip, timeout in `readTextFrame`), the outer loop tries again with zero delay. Against an unhealthy server that drops connections shortly after accepting them, the loop spins as fast as TCP/TLS handshakes allow.

**Category:** STABILITY (battery drain on flaky servers)

---

### H6. System-wide alarm volume stays raised if the process is killed mid-alert
**File:** `HighPriorityAlertPlayer.java:30-31, 184-206, 276-294`

```java
private static boolean shouldRestoreAlarmVolume;
private static int originalAlarmVolume = -1;
...
originalAlarmVolume = current;
shouldRestoreAlarmVolume = true;
audio.setStreamVolume(AudioManager.STREAM_ALARM, target, 0);   // system-wide, persistent
```

The "original" alarm volume lives in two **static** fields. If the process dies (force stop, OOM kill, crash) between `setStreamVolume(target)` and the restore in `stopLocked`, the fields are gone but the OS-level alarm-stream volume remains at the bumped value. Next launch sees `originalAlarmVolume = -1` and the restore is skipped. Persist the original volume to `SharedPreferences` so it can be restored on next start.

**Category:** STABILITY

---

## 🟡 Medium

### M1. `LogStore.append()` blocks caller on disk I/O; `trim()` compounds it
**File:** `LogStore.java:25-41, 94-116`

`append` opens / writes / closes the log file under `LOCK`, then calls `trim()` which (when log > 256 KB) reads the entire file into memory and atomically writes it back — all still inside the lock. Every `LogStore.append(...)` call from any code path blocks until that's done. Hot paths (per-task GPS post, every notification, every Wi-Fi change) all pay the cost. A 1-line bounded queue + dedicated writer thread would remove the caller-blocking property.

**Category:** OPTIMIZATION

---

### M2. `logMaxLines` is written by both `saveGpsConfig` and `saveLogConfig`
**File:** `Config.java:432, 459, 526-531`

`saveGpsConfig`'s final parameter is still `logMaxLines`, and the method writes `KEY_LOG_MAX_LINES`. A separate `saveLogConfig(enabled, logMaxLines)` also writes the same key. In practice MainActivity passes the same `text(logMaxLinesField)` to both, so there's no drift today — but the duplication is a refactor leftover that will eventually go wrong (a partial-update caller or test will overwrite the other path's value).

**Category:** INCONSISTENCY

---

### M3. `remoteLinkReconnectSeconds` is hard-coded at 60
**File:** `Config.java:395-397`

```java
int remoteLinkReconnectSeconds() {
    return 60;
}
```

No backing preference key, no UI binding. Every other tunable in this class flows through `intValue(KEY, default, min, max)`. This is the only getter that ignores the user.

**Category:** INCONSISTENCY

---

### M4. NotificationHistoryStore image-prune race
**File:** `NotificationHistoryStore.java:61-65, 282-299`

```java
static void add(...) {
    String imageFileName = writeImage(app, imageBase64);     // line 63 — UNLOCKED file write
    add(app, source, title, message, icon, !imageFileName.isEmpty(), imageFileName); // takes LOCK
}
...
private static void writeLocked(...) {
    writeAtomically(...);
    pruneImages(context, entries);                            // line 210 — deletes orphans
}
```

`writeImage()` lands an image file on disk *before* the lock is taken to record its filename in the entry list. If another thread holds the lock and runs `pruneImages()` during that window, the just-written image file is treated as an orphan and deleted. The next `add()` then registers an entry that references a file that no longer exists. Mitigated by `NotificationListenerService` serialising callbacks, but `RemoteEventHandler.handleNotification` (worker thread) and the listener can both call `add()` concurrently.

**Category:** STABILITY

---

### M5. `HighPriorityAlertPlayer.findToneUri` runs on the main thread
**File:** `HighPriorityAlertPlayer.java:88, 317-352`

`playOnMain` (main looper) calls `findToneUri`, which calls `new RingtoneManager(...).getCursor()` for `TYPE_ALARM`, then `TYPE_RINGTONE`, then `TYPE_NOTIFICATION`, iterating every row and comparing titles. On a device with many user-installed ringtones, this is hundreds of `Cursor.moveToPosition` calls and a string compare per row, all on the UI thread, *every time the alert fires*. Cache the resolved URI in memory (invalidate on tone-title config change) and resolve it once on a worker thread.

**Category:** OPTIMIZATION / STABILITY

---

### M6. `LocationHelper` requests GPS and Network providers simultaneously
**File:** `LocationHelper.java:93-95`

```java
for (String provider : providers) {
    manager.requestLocationUpdates(provider, 1000L, 0f, listener, thread.getLooper());
}
```

With both `useGpsProvider` and `useNetworkProvider` defaulting to true, every GPS post turns on the GPS chip **and** the network-positioning subsystem for up to `locationTimeoutSeconds` (default 60 s). GPS is the single most discretionary draw on a modern phone. A staged strategy (Network first → escalate to GPS only if accuracy not met) would skip GPS entirely most cycles, especially at home.

**Category:** OPTIMIZATION

---

### M7. RemoteEventHandler ACKs reboot commands before executing them
**File:** `RemoteEventHandler.java:79-93`

```java
try {
    JSONObject ack = new JSONObject();
    ack.put("type", "ack");
    ack.put("id", id);
    client.sendText(ack.toString());                  // ACK sent
    ...
} catch (Exception e) { ... return; }

if (rebootAction) {
    RebootManager.handleRemoteCommand(context, id);   // may silently no-op
    return;
}
```

`RebootManager.requestReboot` returns false when accessibility isn't enabled, but by then the ack is already gone. The server believes the reboot was accepted. Ack after the action succeeds (or send a separate `result` message).

**Category:** STABILITY (silent command loss)

---

### M8. NotificationListener does unconditional payload extraction + RebootManager call
**File:** `HighPriorityNotificationListener.java:25-26`

```java
NotificationPayload payload = NotificationTextExtractor.extract(sbn);   // always runs
RebootManager.handleNotification(this, packageName, payload, sbn.getKey()); // always runs
```

`NotificationTextExtractor.extract` iterates every notification extra and unpacks messaging-style bundles for every notification on the device. RebootManager then does its own enabled-check internally. Early-return when both the high-priority feature and reboot-from-notification are disabled.

**Category:** OPTIMIZATION

---

### M9. Shared `requestCode` allocation between AlarmScheduler and RemoteEventHandler
**File:** `AlarmScheduler.java:253-254`, `RemoteEventHandler.java:137`

```java
// AlarmScheduler:
return 0x5100 + Math.abs(taskId.hashCode() % 1000);     // 0x5100 .. 0x54E7
// RemoteEventHandler:
0x5200 + Math.abs(id.hashCode() % 1000)                  // 0x5200 .. 0x55E7
```

The ranges overlap (0x5200 .. 0x54E7 is reachable by both). PendingIntents are keyed by `(context, requestCode, intent, flags)`; collisions across the two subsystems can cancel or replace each other. Today only 3-4 alarm taskIds exist so collision is theoretical, but the architecture is brittle.

**Category:** INCONSISTENCY

---

## ⚪ Low / Consistency

### L1. `BootReceiver` declares `LOCKED_BOOT_COMPLETED` but isn't `directBootAware`
**File:** `AndroidManifest.xml:93-101`, `BootReceiver.java`

The receiver doesn't declare `android:directBootAware="true"`, so Android filters out `LOCKED_BOOT_COMPLETED` deliveries. The intent-filter line is dead. Either remove the action or add `directBootAware="true"` and audit the SharedPreferences calls (which use the credential-encrypted store and would fail before user unlock).

---

### L2. `LogStore.append()` mid-write process kill leaves a partial line
**File:** `LogStore.java:34-37`

`trim()` and `replaceWithSingleEntry` now use temp-file + rename — good. `append()` itself is still a raw `FileOutputStream(file, true).write(line)`. If the process dies mid-write, the file ends with a half line. Recoverable (next read just truncates the bad tail), but worth a one-line note.

---

### L3. Battery alert is alarm-polled
**File:** `BatteryAlertManager.java:61`, `AlarmScheduler.java:92-100`

Re-arms after every check; default 5 min cadence = ~288 wakeups/day. Could register `ACTION_BATTERY_CHANGED` inside `NetworkMonitorService` (or the accessibility service) and use the alarm only as a long safety-net fallback (~60 min).

---

### L4. PIN sequence silently strips non-digits
**File:** `SystemManagerAccessibilityService.java:197-206`

`"1-2-3"` will tap `1`, `2`, `3` (separators silently skipped). No log line says "ignored character". A user expecting separators to be meaningful won't know they were dropped.

---

### L5. TonePickerDialog search doesn't include URI
**File:** `TonePickerDialog.java:360-368`

Search checks `title` and `typeLabel`; the `uri` field exists and is populated but isn't searchable. Minor UX gap; `AppPickerDialog` for comparison searches both `label` and `packageName`.

---

### L6. NetworkMonitorService still gates start on `showWifiMonitorNotification`
**File:** `NetworkMonitorService.java:20-29`

```java
if (config.isTrackingEnabled() && config.postOnWifiChange() && config.showWifiMonitorNotification()) {
    start(context);
}
```

Now mitigated because `SystemManagerAccessibilityService.syncMonitor()` (line 99-109) starts its *own* WifiChangeMonitor when the notification is hidden. So the feature only fully works in one of two configurations: (a) notification visible + foreground service, or (b) notification hidden + accessibility service enabled. The dual-path design is fragmented and only obvious from reading the code; a user with neither path satisfied gets no Wi-Fi change detection and only the small in-UI `wifiMonitorWarning` to explain why.

---

### L7. `logMaxLines` parsed-as-string-with-default lets unparseable input revert silently
**File:** `Config.java:435-446` (similar pattern for `fallbackLatitude` / `fallbackLongitude`)

Coordinates are stored as `String` and read via `doubleValue()` which catches `NumberFormatException` and returns the hard-coded default. The user can save "abc" as a coordinate and the next read silently reverts to 52.520008, 13.404954 (Berlin) with no log. Validate at save time and refuse / re-show the field.

---

## Dismissed (verified false or already fixed)

| Claim | Why dismissed |
| --- | --- |
| `HttpPoster` doesn't `disconnect()` on exception path | Fixed — `try { ... } finally { connection.disconnect(); }` at `HttpPoster.java:19-30`. |
| `WifiChangeMonitor.forceDisconnected` sticky between events | Fixed — `forceDisconnected.set(disconnected)` at `WifiChangeMonitor.java:143` now always overwrites with the latest event's value. |
| `PendingIntent.FLAG_UPDATE_CURRENT \| FLAG_IMMUTABLE` is incompatible on Android 12+ | False — standard Android pattern; flags are orthogonal. |
| Accessibility service `android:exported="true"` is a security hole | Required by the platform for the system to bind via `BIND_ACCESSIBILITY_SERVICE`. |
| `rebootRunning` needs `volatile` | False — all reads / writes are on the main looper thread (`handler.post` and the postDelayed runnable). |
| `HighPriorityAlertPlayer.stopRunnable` orphaned when `playOnMain` returns early | False — `stopRunnable` is only assigned after a successful `ringtone.play()`; early returns leave it as previously cleared by `stopLocked(app, "restart")` on line 81. |
| `NotificationDeduper` `SystemClock.elapsedRealtime()` overflow | False — wraps after ~292 million years. |
| `AppPickerDialog` case-sensitive equals for current package | Correct — Android package names *are* case-sensitive. |
| `NotificationChannel` recreated per battery / remote notification | Documented as a no-op when the channel already exists. |
| `Config` aliased getters with inverted-polarity semantics | Fixed — old `gpsOnlyOnMatchingWifi()` / `useFallbackOnWifiMismatch()` pair removed; only the `requestGpsOnSsidMismatch` / `useFallbackOnSsidMatch` semantic remains, and the saved key preserves legacy install values cleanly. |

---

## Suggested review order

1. **C1** (Remote Link won't start from background) — silent feature break that grows worse with backgrounding.
2. **C2** (accessibility event subscription) — one-line XML change, biggest battery win.
3. **C3** (`acceptAnySslCert` default) — backwards-compatible to flip to `false`; current default invalidates the TLS.
4. **H3 + H4** (frame and image size bounds) — DoS-from-server, trivial to bound.
5. **H1 + H2 + M1** (blocking I/O on binder/UI threads) — group fix: introduce a small "background writer" helper and route `LogStore.append`, `NotificationHistoryStore.add`, and `WifiInfoReader.read()` through it.
6. **H5** (Remote Link reconnect storm) — add a 1 s backoff to the post-connect-failure path.
7. **H6** (alarm volume restore on process death) — persist the original volume in `SharedPreferences`.
8. Remaining items at your discretion.
