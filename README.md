# LocSync

A client-server Android system that shares live GNSS location from a phone to a car head unit over the phone's Wi-Fi hotspot, feeding the head unit's navigation via a mock GPS provider.

- **LocSync Server** (phone): collects high-precision location (Fused/GPS), runs as a foreground service, and streams updates over **UDP** to the client.
- **LocSync Client** (head unit): discovers the server automatically, receives updates, smooths them with a Kalman filter, and injects them as system mock GPS.

## Features

- **UDP transport** — single-client, versioned protocol, recency-based liveness.
- **Zero-config discovery** — the client finds the server by UDP broadcast (no IP to configure); a manual fixed-address mode is available as a fallback.
- **Client autostart** — starts on Wi-Fi connect via a persisted JobScheduler job (plus boot).
- **Location smoothing** — constant-velocity Kalman filter, 10 Hz output, stop-freeze and GPS-loss handling for a fluid nav icon.

## Modules

- `server-app` — the phone (server) app.
- `client-app` — the head-unit (client) app.
- `shared` — shared utilities + the UDP `Protocol` (proto wire format lives in `proto/`).

## Building

```bash
./gradlew assembleDebug        # both debug APKs
./gradlew :shared:testDebugUnitTest testDebugUnitTest   # unit tests
```

**CI**: every push (any branch) and PR builds + tests and uploads both debug APKs as a `debug-apks` artifact (GitHub Actions → the run's Artifacts). Debug builds show `Version <branch>-<shortsha>` at the bottom of the screen.

## Releasing

Releases are built and published automatically when a `v*` tag is pushed (`.github/workflows/release.yml`): it builds signed release APKs and publishes them to a GitHub Release. The tag becomes the app's `versionName` (shown at the bottom of the screen).

```bash
git tag v1.2.3
git push origin v1.2.3
```

### Signing setup (one-time)

Release builds are signed with a keystore supplied through repository secrets. The Gradle signing config expects alias `key0` and a single password used for both the store and the key.

1. Create the keystore (keep it safe and out of git — `*.jks` is gitignored):
   ```bash
   keytool -genkeypair -v -keystore keystore.jks -alias key0 \
     -keyalg RSA -keysize 2048 -validity 10000 \
     -storepass 'PASSWORD' -keypass 'PASSWORD' \
     -dname "CN=LocSync, OU=Dev, O=LocSync, C=US"
   ```
2. Add two GitHub repository secrets:
   - `KEYSTORE_BASE64` — `base64 -w0 keystore.jks`
   - `KEY_PASSWORD` — the password above
   ```bash
   base64 -w0 keystore.jks | gh secret set KEYSTORE_BASE64
   gh secret set KEY_PASSWORD --body 'PASSWORD'
   ```

> **Back up `keystore.jks` and its password.** Losing them means you can no longer sign updates with the same identity.

## Usage

1. **Phone (server)**: launch LocSync Server, grant permissions, Start Server (enable Wi-Fi hotspot).
2. **Head unit (client)**: select LocSync Client as the mock-location app (Developer Options), join the phone's hotspot, launch the app — it auto-discovers and connects.

## License

[GNU General Public License v3.0](LICENSE).
