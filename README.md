# YouSong Android customer

Connected Compose client for the participant API. Spanish dark surfaces, music cards/details and bottom navigation follow the customer prototypes in `../documentation/Prototipo`. Queue/current-playing state, requests, votes and catalog suggestions use the backend. Playback is controlled by the owner; the customer app has no audio player, automatic advance, vote withdrawal or queue editing. The backend may report default-list playback without a request ID. No data or successful actions are fabricated. There is no FusionAuth login in the customer app.

## Run and scan

Open this application directory in Android Studio (compile/target SDK 37, minimum 24).

- **Debug paired phone:** currently uses `http://127.0.0.1:8080` through an explicitly selected, authorized ADB transport. Before opening the app, run `adb -s <PHONE_SERIAL> reverse tcp:8080 tcp:8080`. Recreate the mapping after a debugging transport disconnect. This does not expose a new LAN port or change the firewall. Install an update with `adb -s <PHONE_SERIAL> install -r app/build/outputs/apk/debug/app-debug.apk`; never clear app data or grant permissions automatically.
- **Debug emulator:** developers can configure `api_base_url` in `app/src/debug/res/values/strings.xml` to `http://10.0.2.2:8080` before building for the emulator.
- **Debug LAN phone:** developers can configure that resource to `http://192.168.1.21:8080` when the phone can actually reach the PC on the trusted development network.
- **Production/release:** developers configure `api_base_url` in `app/src/main/res/values/strings.xml` with an HTTPS origin, without path, query, credentials or fragment. The release default is intentionally empty.

Customers never configure the server or enter/paste codes or URLs. Missing or invalid developer configuration displays an honest service-configuration error; it never falls back to a QR host or a customer server editor.

Only those three exact HTTP API origins are accepted in debug. Debug-only network security configuration allows cleartext for those three hosts, without subdomains; Android's host-level policy cannot restrict ports, so the API validator additionally requires port 8080. Release rejects HTTP and does not include the debug network security configuration. Normal TLS validation remains enabled in both variants. Development HTTP exposes traffic on the local network: use only trusted development infrastructure, not production credentials.

Tap **Escanear QR** to request camera permission and open the embedded scanner. Permission denial or cancellation displays Spanish feedback and leaves scanning available again. Permission is never requested automatically on startup. JourneyApps ZXing Android Embedded is pinned to 4.3.0, with ZXing core pinned to 3.5.4; only project Maven dependencies are used, with no global install. Barcode image saving and scanner beeps are disabled. Camera use still requires device verification.

Scan the establishment QR, normally a URL ending exactly in `/s/{slug}`. The backend-valid slug pattern is `[a-z0-9-]{3,80}`. The QR supplies only the slug: its host never changes the API origin or receives participant credentials. A valid scan immediately loads the establishment and current policy with visible progress/errors, without a separate lookup button. Explicitly authorize required F1 and independently choose optional F2. Both start unchecked. **Ingresar a buscar canciones** joins only after required F1 consent, then opens the real **Buscar** tab; errors remain visible instead of simulating success. A changed policy must be fetched and authorized again; recovery never silently selects consent. Use **Escanear otro QR** to reset the entry.

**Actualizar estado del local** makes one explicit, bounded availability/policy lookup after the owner opens or resumes the session; it never joins automatically. Explicit consent is preserved only when the policy version is unchanged; a changed policy clears both selections. A failed refresh or join requires another refresh before entering. Maintenance, pause and closure remain unavailable. A valid server-provided next-opening instant is displayed without inventing the reason for closure.

The private `QrCaptureActivity` uses JourneyApps' inherited permission and camera lifecycle management, a portrait-only activity request (not an app-wide orientation lock), a preview capped at 360dp wide × 320dp high and a 240dp square scan frame. Its native colors mirror the existing Compose customer theme. The default dependency activity declares `sensorLandscape`; scanner options explicitly select our activity without locking to the sensor's initial rotation. Large-screen Android orientation overrides, multi-window and physical camera framing still require device checks; source configuration is not proof of physical behavior.

Entry result launchers remain registered while the camera covers the app. Lifecycle stop cancels and invalidates lookup/join results, resets unchecked consent and requires scanning again after an interrupted request. The existing main-thread ownership gate and synchronous busy state also serialize refresh and prevent duplicate lookup/join taps. Canceling an accepted join cannot undo a server operation; the uncertainty limitation below still applies.

## Backend contract

| Operation | Existing endpoint |
| --- | --- |
| Public establishment | `GET /api/public/establecimientos/{qrSlug}` |
| Privacy policy | `GET /api/public/politica` |
| Join | `POST /api/public/establecimientos/{qrSlug}/participaciones` |
| Status / leave | `GET` / `DELETE /api/participante/sesion` |
| Catalog search | `GET /api/participante/catalogo/busqueda?q=...&pagina=...` |
| Catalog suggestions | `GET /api/participante/catalogo/sugerencias?q=...` |
| Participant queue | `GET /api/participante/cola` |
| Request / support song | `POST /api/participante/solicitudes` with `{videoId}` |
| Vote | `POST /api/participante/solicitudes/{solicitudId}/votos` |

Join sends `canal: ANDROID`, `politicaVersion`, `autorizaF1` and `autorizaF2`. The returned opaque participant token is sent only in the configured API's `Authorization: Bearer` header. Owner JWTs are not manufactured or used. There is no participant token-refresh endpoint. Public availability requests carry no bearer credential.

Search and suggestions use 2–100 normalized characters and a 400 ms debounce. Search keeps returned `siguientePagina` pagination. Suggestions are real known-song metadata (`CANCION`) or query completions (`BUSQUEDA`), never fabricated results. Detail uses the selected result, including title, artist verification, channel, duration and thumbnail; missing metadata remains unavailable. Empty searches do not trigger provider calls. No hardware identifier is collected.

## Queue demo (manual, requires separately authorized real setup)

1. The owner prepares an active establishment, publishes a real default list and opens a session. Provider configuration/quota/network must be available. No seed or live setup is performed by this app change.
2. Scan its QR and explicitly select F1; entry opens **Buscar**. Pick a real result or known-song suggestion, inspect details and tap **Solicitar o apoyar**. A new request includes your first vote (201); supporting an existing active request returns 200. The UI uses the acknowledgment, not an invented vote count or position.
3. Open **Cola** to see server order, positions, votes, estimated waits and current-playing metadata. A second legitimate participant can support a song once. Already-voted, unknown-vote, unavailable and pending actions are disabled; server conflicts remain authoritative.
4. The owner explicitly advances/finishes playback state. There is no promise of audible playback or automatic queue advance. Refresh or wait for polling to observe changes. Closing the session or any participant 401 returns to entry through the existing secure cleanup callback.

A single connected-screen poll reads participant status, public establishment availability and queue sequentially, immediately while active and seven seconds after the completed cycle. All reads share the existing transport mutex with mutations; manual refresh requests are conflated. Participant status/queue describe the session, not establishment pause/schedule overrides. Public availability is refreshed independently of the queue version. Failed availability/status/queue checks disable mutations without discarding the last queue. Matching public `ABIERTO` plus `puedeParticipar: true`, participant `ABIERTO` and an authoritative open queue are required before requesting or voting.

Screen disposal cancels requests and invalidates ownership. Older queue versions and callbacks from disposed sessions cannot replace the active snapshot. Queue states are `ABIERTO/PAUSADO/CERRADO/MANTENIMIENTO`, distinct from session DTO values; unknown states are read-only. Missing `votado` stays unknown, never false. Queue songs do not require catalog-only `canal`; default-list playback can have a null request ID and does not get an invented vote action.

Requests/votes are never automatically retried. Success, conflict and uncertain outcomes trigger authoritative status/availability/queue reads before another mutation is enabled. If a request times out after server acceptance, it cannot be undone locally: the UI explicitly warns that acceptance is uncertain and asks for refresh before repetition. Refresh cannot prove historical nonacceptance after a song leaves the active queue; the backend's once-per-song/session rule remains authoritative. Errors, provider availability/quota, permission denial and rate limits are shown in Spanish without exposing raw server payloads or credentials.

## Session and security

- The participant credential, expiry and establishment/session metadata are persisted only as AES-256/GCM ciphertext in `noBackupFilesDir`, using a non-exportable Android Keystore key, a fresh random IV and authenticated API-origin binding. Atomic writes avoid partially replaced records. No SecurityCrypto dependency is required.
- New joins add only optional validated `qrSlug` inside the existing encrypted JSON. File path, envelope version, key, random-IV behavior, API-origin AAD and cleanup are unchanged. No plaintext QR metadata or device-token persistence is added.
- Existing encrypted records remain readable without a slug. Missing, null, non-string or invalid optional metadata becomes unknown identity, not credential loss. Such sessions retain queue access but cannot request/vote. Participant/public DTOs have no shared stable identity proving that a new QR belongs to an old credential; names and unverified QRs cannot recover it.
- Legacy recovery is explicit: **Inicio → Salir del local**, wait for successful leave, then scan the QR and manually authorize unchecked F1 again. Failed non-401 leave preserves the old session. No automatic logout, QR reassignment, join or consent is performed.
- Rotation/process recreation restores a valid record with its original validated API origin, without asking for consent again for an already joined session. New joins always start with unchecked consent. Tokens never enter preferences, saved instance state, logs or backups. Backup and device-transfer rules exclude app data as defense in depth.
- Expired, malformed, tampered or undecryptable credential records are erased. Local expiry also ends an open session. Any participant 401 (including `SESSION_ENDED` and `INVALID_CREDENTIAL`), authoritative closed participant session/queue, or successful leave erases the record and clears customer UI state before returning to entry/scanning. Failed non-401 leave retains the session and reports an error.
- Public `CERRADO`, pause, maintenance and outside-hours availability do **not** erase valid credentials. Queue reads continue; requests/votes stay disabled. Public lookup failure, even a public 401, is not participant authentication failure.
- If secure persistence fails after a successful join, the current session remains memory-only with an explicit warning; no plaintext fallback is used.
- Polling runs only while started. Lifecycle stop cancels polling and screen request scopes. Blocking HTTP may finish until its timeout, but canceled results cannot update the disposed UI.
- API and anonymous HTTPS thumbnail requests run off main with timeouts and response-size bounds. Redirects are disabled; thumbnail requests never carry participant credentials.
- A join/leave interrupted after server acceptance can be locally uncertain until status/expiry reconciliation. The backend optionally accepts a device token for rejoining; this client does not persist that extra secret or promise interrupted-join recovery. Clearing a local record is not proof of server-side revocation.

## Verification

The daemon criteria in `gradle/gradle-daemon-jvm.properties` require **JetBrains JDK 25**, matching the full JDK bundled with Android Studio. Requiring only Java 25 allowed Gradle to choose an editor runtime without `jlink`, even when the launcher used the correct `JAVA_HOME`. Configuration-cache serialization was the outer error; the failing Android JDK image transform was the nested cause.

The criteria contain no download URLs: the old URLs were generated without a vendor constraint, and their compatibility with the JetBrains requirement was not verified. They are not retained as implicit provisioning fallbacks. Use the already installed full JDK, not an editor's embedded JRE; no JDK/SDK downloads are needed. In Android Studio, use its bundled `jbr-25` as **Gradle JDK** under **Settings → Build, Execution, Deployment → Build Tools → Gradle**. The installed JDK must include `java`, `javac` and `jlink`.

Example for this development machine:

```sh
export JAVA_HOME=/home/maicolm/.local/share/JetBrains/Toolbox/apps/android-studio/jbr
export ANDROID_HOME=/home/maicolm/Android/Sdk
export ANDROID_SDK_ROOT=/home/maicolm/Android/Sdk
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew :app:assembleDebug :app:testDebugUnitTest --offline --console=plain --configuration-cache \
  -Porg.gradle.java.installations.auto-download=false \
  -Pandroid.builder.sdkDownload=false
```

Use those same flags for `processReleaseMainManifest compileReleaseKotlin assembleDebugAndroidTest`. These compile release and build the instrumentation APK without release signing, deployment or device installation. The optimized release variant does not expose `testReleaseUnitTest`; production HTTP rejection is covered by explicit `debug = false` contract tests and inspection of generated release `BuildConfig.DEBUG = false` and merged manifest. Offline verification assumes Maven dependencies are already cached; missing dependencies require a separately authorized download, not implicit JDK/SDK provisioning.

### Prior verified baseline

Earlier TDD observed seven session/network/AES tests fail, then pass (16 JVM tests including the original nine). Entry/scanner refinement observed two ownership failures and one scanner-target failure, then five focused passes; the suite grew to 21 passing JVM tests. Historical release Kotlin/manifest checks passed before that refinement. Gradle warns about features incompatible with Gradle 10.

Before the current availability/persistence changes, the parent observed exit 0 for the command below: 77 actionable tasks (18 executed, 59 up-to-date), configuration cache reused, 26 JVM tests with zero failures/errors/skips, and debug/instrumentation APK assembly. All 21 earlier cases were preserved. Four queue parsing tests were compiled, not executed. That earlier queue implementation did not observe an initial RED execution; its successful check is not a complete RED/GREEN claim.

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --offline --console=plain --configuration-cache -Porg.gradle.java.installations.auto-download=false -Pandroid.builder.sdkDownload=false
```

### Current availability and optional QR metadata change

The parent observed actual RED for `SessionReadinessTest`: background job-1 exit 1 after 12 seconds; JUnit XML reports 10 tests, seven failures, zero errors/skips and three passes. Failures were the unsupported `MANTENIMIENTO` enum and six exact availability-message comparisons, not compilation or environment failures. The original 26 cases were not rerun by that focused command.

The parent inspected current JUnit XML showing **42 JVM tests, zero failures/errors/skips**, including the ten original RED cases now green. The initial combined command's exit/log became unavailable after a tooling reload, so its exit status is not claimed. A separate APK-only `:app:assembleDebug :app:assembleDebugAndroidTest` execution subsequently exited 0 (71 actionable tasks: six executed, 65 up-to-date). Storage/availability JSON instrumentation additions compiled, but were **not executed on a device**. Six additional pure availability/identity/consent cases were authored before their helper implementation without a separately observed RED execution.

Independent Android verification is currently **unavailable**, not passed: the verifier requires isolated scratch-copy probes, which conflict with the source-only/no-copy assignment. No audit files, probes or copies were created by that attempt. Completing that verification requires a separate explicit decision about the temporary-copy scope. Native Keystore, process-recreation and device behavior remain unproven.

`SessionStorageTest` covers native Keystore round-trip, optional encrypted metadata, legacy/malformed metadata retention, tamper cleanup and erasure in isolated device test storage. JSON parsing tests cover public availability, nullable next opening, unknown/maintenance queue states and default-list playback. APK assembly only compiles instrumentation; it does not establish native Keystore or device behavior.

No authorized disposable emulator/device is available. Scanner permission/denial/cancel, camera decoding, Keystore hardware/platform behavior, process recreation, polling/401/leave against a running backend, rendered UI/accessibility and the full two-participant/owner demonstration remain unverified. No live backend/provider mutations, seed, emulator or personal phone are used by this change. Do not install on a personal device or start an emulator without explicit authorization.
