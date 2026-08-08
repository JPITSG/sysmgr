# System Manager

A self-hosted Android device-management app and a small companion server daemon.
It lets you keep an eye on and remotely control an Android phone over your own
infrastructure — location logging, push notifications and loud alerts, scheduled
volume/Do-Not-Disturb rules, battery alerts, remote reboot automation, and an
embedded OpenVPN client that works without root — all driven from a server you
run yourself.

This is a personal project built around one owner's setup. It's shared in case
the pieces are useful to others; it is provided as-is, with no warranty (see
[License](#license)).

> **Heads up:** it's designed for a single trusted phone talking to a server
> you control on a private network. It is not a hardened, multi-tenant product.

---

## Architecture

```
   ┌─────────────────────┐        Unix control socket        ┌──────────────────┐
   │  Local automation    │  key=value on /tmp/sysmgrd.sock   │                  │
   │  (e.g. openHAB rules,│ ────────────────────────────────▶ │     sysmgrd      │
   │   cron, scripts)     │   action=message|reboot|alarm|    │  (Node.js daemon)│
   └─────────────────────┘         ping|vpn|vnc                │                  │
                                                               │  HTTPS + WSS     │
   ┌─────────────────────┐         WSS (Basic auth, TLS)       │  Basic auth      │
   │  Android phone       │ ◀───────────────────────────────▶ │  self-signed TLS │
   │  "System Manager" app│    notifications, alarms, reboot,  │  GPS → MySQL     │
   │  (VpnService, etc.)  │    vpn cmds, GPS + notification     │  notif → JSONL   │
   │                      │    backups (with acks)             │                  │
   └─────────────────────┘                                     └──────────────────┘
```

- The **app** on the phone opens a persistent authenticated WebSocket ("Remote
  Link") to **sysmgrd** and also works standalone for the on-device features.
- **sysmgrd** exposes an HTTPS/WSS endpoint for the phone and a local Unix
  **control socket** so anything on the server (openHAB rules, cron, a shell
  script) can push commands to the phone by writing simple `key=value` lines.
- The **OpenVPN client** is entirely on the phone: an embedded `openvpn` binary
  driven through Android's `VpnService`.

---

## Features

**On-device**
- **GPS logging** — reports location on a schedule, on Wi-Fi change, and/or at
  startup, with a configurable accuracy/timeout strategy.
- **Remote notifications & high-priority alerts** — the server can push a
  notification (optionally with an image); matching alerts can trigger a loud
  alarm tone + vibration even in silent/DND.
- **Notification history** — unlimited local history with images, pagination,
  and save/share, plus per-notification Delete/Clear actions.
- **Notification backup** — optionally mirror every user-facing notification to
  your server over the Remote Link. Each notification is hashed, queued in a
  durable on-device outbox, and delivered with retry until the server
  acknowledges it; the server appends it (with its own receive time) to a
  JSON-lines store. Toggles for on/off and whether to include System Manager's
  own notifications; the app tells you if the server isn't storing them.
- **Reboot automation** — uses an Accessibility service to open the power menu
  and trigger a reboot (gesture + optional PIN), locally or remotely.
- **Scheduled volume / DND rules** — set media/ring/notification/alarm volumes
  and Do-Not-Disturb at chosen times.
- **BLE beacon** — broadcasts the phone as a standard iBeacon so your own BLE
  receivers can locate it by RSSI. The transmit rate is driven by **battery
  rules** you define ("at or above 66%, broadcast every 10s; at or above 33%,
  every 30s; otherwise stay silent"); the highest matching threshold wins, and
  the rate re-evaluates as the battery moves. Timing is handled by the
  Bluetooth controller, so it survives Doze without a wake lock. The panel
  shows live state, frequency, active rule, battery, transmit power and
  identity. Note that Android randomises the on-air Bluetooth address — match
  your receivers on the **proximity UUID**, not on a MAC.
- **Battery alerts** and **Wi-Fi change monitoring**.
- **Service notification control** — a switch per background service (task
  runner, Wi-Fi monitor, Remote Link, VPN, beacon) under **Permissions**.
  Android requires every foreground service to post a notification, so an
  "off" row posts it on a channel the system never displays: nothing reaches
  the shade or status bar and the service keeps running. The Wi-Fi monitor is
  the exception — turning it off moves the monitor into the Accessibility
  service, so no notification is created at all.
- **Settings backup/restore** to an XML file (no secrets/keys are exported).

**Embedded OpenVPN client**
- A cross-compiled `openvpn` 2.7.5 binary (OpenSSL 3.5.7, LZO 2.10, LZ4 1.10.0)
  is shipped inside the APK and driven over its management interface.
- **tun and tap** both work on an **unrooted** phone: tun via `VpnService`, and
  tap via a userspace Ethernet⇄IP bridge (ARP + framing, IPv4).
- Import a `.ovpn`/`.conf` plus certificate files; the profile is validated on
  import (structure, X.509/key checks, and a real launch-and-hold test).
- Connect/disconnect from the app or remotely via the Remote Link.

**Server (sysmgrd)**
- HTTPS + WSS with HTTP Basic auth and a self-signed TLS certificate generated
  at startup.
- Persists GPS reports to MySQL with home-radius privacy redaction.
- Local control socket for pushing `message` / `reboot` / `alarm` / `ping` /
  `vpn` / `vnc` commands to the phone.

---

## Repository layout

```
app/                     Android app (plain Java, no Gradle)
  src/main/java/...       source
  src/main/res/...        resources
  src/main/AndroidManifest.xml
sysmgrd/sysmgrd          Node.js server daemon (single file, no dependencies)
native/build-openvpn.sh  Reproducible cross-compile of the embedded openvpn binary
ISSUES.md                Internal audit notes / known issues
```

---

## Building

### Android app

The app is plain Java built with the standard Android SDK build tools — **no
Gradle**. The pipeline is: `aapt2 compile/link` → `javac` (source/target 8) →
`d8` → inject `classes.dex` and `lib/arm64-v8a/*.so` → `zipalign` → `apksigner`.

Prerequisites:
- Android SDK **build-tools 36.0.0** and platform **android-36**
- A JDK (for `javac`)
- Android **NDK r27** (only needed to build the embedded OpenVPN binary)
- `minSdkVersion` 26, `targetSdkVersion` 36

The exact wrapper script that wires these tools together is environment-specific
(it hard-codes local SDK paths and a debug keystore) and is intentionally not
committed. Point the tools at your SDK/NDK and keystore to produce a signed APK.

### Embedded OpenVPN binary

The native binary is produced by a committed, reproducible script:

```
native/build-openvpn.sh
```

It downloads and verifies OpenVPN + OpenSSL + LZO + LZ4 (+ libcap-ng),
cross-compiles them for **arm64-v8a / API 26** with the NDK (deps static,
bionic dynamic, 16 KB page aligned, patched to `TARGET_ANDROID` so tun/routes/
DNS go through the `VpnService` management interface), and stages
`app/src/main/jniLibs/arm64-v8a/libopenvpn.so`. It self-skips when the version
stamp is unchanged (`FORCE_NATIVE=1` forces a rebuild).

### Server (sysmgrd)

`sysmgrd/sysmgrd` is a single Node.js file with no npm dependencies. Run it with
Node 14+.

---

## Running

### sysmgrd

```
sysmgrd --bindip=<ip> --bindport=<port> --log=<path> --users=<user:pass,user2:pass2>
```

Optional GPS→MySQL persistence is enabled by adding `--gps-mysql-*`
(host/socket, database, table, user, password) and `--gps-home-lat/lon` args.
All credentials are supplied at runtime — nothing is stored in the repository.

Optional notification backup is enabled by adding `--notificationstore=<path>`;
the daemon appends each backed-up notification as one JSON object per line
(JSON Lines) to that file, created `0600`. Point it outside the repository. The
file only grows — rotate or prune it yourself if needed.

Push a command to the phone from the server by writing to the control socket,
for example:

```
printf 'action=vpn\ncmd=connect\n' | socat -t 90 - UNIX-CONNECT:/tmp/sysmgrd.sock
```

VNC uses the same command path. Enable its on-device **Allow VNC control from
Remote Link** switch first, then use `cmd=enable`, `cmd=disable`, or
`cmd=status`:

```
printf 'action=vnc\ncmd=enable\n' | socat -t 90 - UNIX-CONNECT:/tmp/sysmgrd.sock
```

### App

Sideload the signed APK, then open the app and configure each section (Remote
Link endpoint + credentials, GPS logger, alerts, VPN profile, etc.). Several
features require you to grant Android permissions (location, notification
access, accessibility, VPN consent, battery-optimization exemption).

---

## Security notes

- **Self-hosted and single-tenant by design.** Intended for one trusted phone
  and a server you control on a private network.
- **Credentials are runtime-only.** sysmgrd's Basic-auth users, the MySQL
  password, and its TLS certificate are provided/generated at run time; none are
  committed. The app stores its own credentials in app-private storage and never
  includes them in settings backups.
- **TLS is self-signed.** The app has a developer option to accept any TLS
  certificate for the Remote Link — appropriate only for a trusted LAN, not the
  public internet.
- **Debug signing.** The build uses a local debug keystore; produce your own
  signing key for anything beyond personal sideloading.
- See `ISSUES.md` for an honest list of known limitations.

---

## Third-party components

This repository's own source is MIT-licensed. Building the embedded VPN binary
downloads and compiles third-party projects that are governed by **their own
licenses** — notably **OpenVPN (GPLv2)**, plus OpenSSL, LZO, LZ4 and libcap-ng.
Their source and the resulting binary are not part of this repository (they are
fetched at build time). If you distribute an APK that bundles the OpenVPN
binary, the obligations of those licenses apply to that distribution.

---

## License

[MIT](LICENSE) © 2026 JP IT
