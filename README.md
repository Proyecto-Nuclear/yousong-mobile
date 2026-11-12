# YouSong Android customer

Connected Compose client for the existing participant API. Spanish dark surfaces, music cards/details and bottom navigation follow the supplied customer prototypes in `../documentation/Prototipo`. Participant queue/current-playing state, requests, votes and catalog suggestions use the existing backend. Playback is controlled by staff; the customer app has no audio player, automatic advance, default-list fallback, vote withdrawal or queue editing. No data or successful actions are fabricated. There is no FusionAuth login in the customer app.

## Run and scan

Open this application directory in Android Studio (compile/target SDK 37, minimum 24).

- **Debug emulator:** defaults to `http://10.0.2.2:8080`.
- **Debug phone:** developers configure `api_base_url` in `app/src/debug/res/values/strings.xml` to `http://192.168.1.21:8080` on the same trusted development network before building for a phone.
- **Production/release:** developers configure `api_base_url` in `app/src/main/res/values/strings.xml` with an HTTPS origin, without path, query, credentials or fragment. The release default is intentionally empty.

Customers never configure the server or enter/paste codes or URLs. Missing or invalid developer configuration displays an honest service-configuration error; it never falls back to a QR host or a customer server editor.

Only those two exact HTTP API origins are accepted in debug. Debug-only network security configuration allows cleartext for those two hosts, without subdomains; Android's host-level policy cannot restrict ports, so the API validator additionally requires port 8080. Release rejects HTTP and does not include the debug network security configuration. Normal TLS validation remains enabled in both variants. Development HTTP exposes traffic on the local network: use only trusted development infrastructure, not production credentials.

Tap **Escanear QR** to request camera permission and open the embedded scanner. Permission denial or cancellation displays Spanish feedback and leaves scanning available again. Permission is never requested automatically on startup. JourneyApps ZXing Android Embedded is pinned to 4.3.0, with ZXing core pinned to 3.5.4; only project Maven dependencies are used, with no global install. Barcode image saving and scanner beeps are disabled. Camera use still requires device verification.

Scan the establishment QR, normally a URL ending exactly in `/s/{slug}`. The backend-valid slug pattern is `[a-z0-9-]{3,80}`. The QR supplies only the slug: its host never changes the API origin or receives participant credentials. A valid scan immediately loads the establishment and current policy with visible progress/errors, without a separate lookup button. Explicitly authorize required F1 and independently choose optional F2. Both start unchecked. **Ingresar a buscar canciones** joins only after required F1 consent, then opens the real **Buscar** tab; errors remain visible instead of simulating success. A changed policy must be fetched and authorized again; recovery never silently selects consent. Use **Escanear otro QR** to reset the entry.

The private `QrCaptureActivity` uses JourneyApps' inherited permission and camera lifecycle management, a portrait-only activity request (not an app-wide orientation lock), a preview capped at 360dp wide × 320dp high and a 240dp square scan frame. Its native colors mirror the existing Compose customer theme. The default dependency activity declares `sensorLandscape`; scanner options now explicitly select our activity without locking to the sensor's initial rotation. Large-screen Android orientation overrides, multi-window and physical camera framing still require device checks; source configuration is not proof of physical behavior.

Entry result launchers remain registered while the camera covers the app. Lifecycle stop cancels and invalidates lookup/join results, resets unchecked consent and requires scanning again after an interrupted request. A main-thread ownership gate and synchronous busy state prevent duplicate lookup/join taps. Canceling an accepted join cannot undo a server operation; the existing uncertainty limitation below still applies.

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

Join sends `canal: ANDROID`, `politicaVersion`, `autorizaF1` and `autorizaF2`. The returned opaque participant token is sent only in the configured API's `Authorization: Bearer` header. Staff JWTs are not manufactured or used. There is no participant token-refresh endpoint.

Search and suggestions use 2–100 normalized characters and a 400 ms debounce. Search keeps returned `siguientePagina` pagination. Suggestions are real known-song metadata (`CANCION`) or query completions (`BUSQUEDA`), never fabricated results. Detail uses the selected result, including title, artist verification, channel, duration and thumbnail; missing metadata remains unavailable. Empty searches do not trigger provider calls. No hardware identifier is collected.

## Queue demo (manual, requires separately authorized real setup)

1. Staff must prepare an active establishment, publish a real default list and open a session. Provider configuration/quota/network must be available. No seed or live setup is performed by this app change.
2. Scan its QR and explicitly select F1; entry still opens **Buscar**. Pick a real result or known-song suggestion, inspect details and tap **Solicitar o apoyar**. A new request includes your first vote (201); supporting an existing active request returns 200. The UI uses the acknowledgment, not an invented vote count or position.
3. Open **Cola** to see server order, positions, votes, estimated waits and current-playing metadata. A second legitimate participant can support a song once. Already-voted, unknown-vote, paused and pending actions are disabled; server conflicts still remain authoritative.
4. Staff can explicitly advance/finish playback state. There is no promise of audible playback or automatic queue advance. Refresh or wait for polling to observe changes. Closing the session or any participant 401 returns to entry through the existing secure cleanup callback.

A single connected-screen poll reads status and queue sequentially, immediately while active and seven seconds after the completed cycle; queue reads are serialized with mutations and manual refresh requests are conflated. Screen disposal cancels requests and invalidates ownership. Older queue versions and callbacks from disposed sessions cannot replace the active snapshot. Queue states are `ABIERTO/PAUSADO/CERRADO`, distinct from session DTO values. Missing `votado` stays unknown, never false. Queue songs do not require catalog-only `canal`; default-list playback can have a null request ID.

Requests/votes are never automatically retried. Success, conflict and uncertain outcomes trigger an authoritative queue read before another mutation is enabled. If a request times out after server acceptance, it cannot be undone locally: the UI explicitly warns that acceptance is uncertain and asks for refresh before repetition. Refresh cannot prove historical nonacceptance after a song leaves the active queue; the backend's once-per-song/session rule remains authoritative. Errors, provider availability/quota, permission denial and rate limits are shown in Spanish without exposing raw server payloads or credentials.

## Session and security

- The participant credential, expiry and establishment/session metadata are persisted only as AES-256/GCM ciphertext in `noBackupFilesDir`, using a non-exportable Android Keystore key, a fresh random IV and authenticated API-origin binding. Atomic writes avoid partially replaced records. No SecurityCrypto dependency is required.
- Rotation/process recreation restores a valid record with its original validated API origin, without asking for consent again for an already joined session. New joins always start with unchecked consent. Tokens never enter preferences, saved instance state, logs or backups. Backup and device-transfer rules exclude app data as defense in depth.
- Expired, malformed, tampered or undecryptable records are erased. Local expiry also ends an open session. Any participant 401 (including `SESSION_ENDED` and `INVALID_CREDENTIAL`), closed service, or successful leave erases the record and clears customer UI state before returning to entry/scanning. Failed non-401 leave retains the session and reports an error.
- If secure persistence fails after a successful join, the current session remains memory-only with an explicit warning; no plaintext fallback is used.
- Status is requested immediately while started/on return, then seven seconds after each completed status/queue polling cycle. Lifecycle stop cancels polling and screen request scopes. Blocking HTTP may finish until its timeout, but canceled results cannot update the disposed UI.
- API and anonymous HTTPS thumbnail requests run off main with timeouts and response-size bounds. Redirects are disabled; thumbnail requests never carry participant credentials.
- A join/leave interrupted after server acceptance can be locally uncertain until status/expiry reconciliation. The backend exposes no idempotent join/resume or hardware-based recovery API. Clearing a local record is not proof of server-side revocation.

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

Use those same flags for `processReleaseMainManifest compileReleaseKotlin assembleDebugAndroidTest`. These compile release and build the instrumentation APK without release signing, deployment or device installation. The optimized release variant does not expose `testReleaseUnitTest`; production HTTP rejection is covered by explicit `debug = false` contract tests and inspection of generated release `BuildConfig.DEBUG = false` and merged manifest. This offline verification assumes Maven dependencies are already cached; missing dependencies require a separately authorized download, not implicit JDK/SDK provisioning.

Earlier baseline TDD: five session/network tests and two AES/GCM tests failed their intended assertions before implementation, then all seven passed (16 JVM tests including the original nine). The entry/scanner refinement observed two request-ownership assertion failures and one scanner-target assertion failure before fixes; all five focused tests then passed. Before the functional-demo change, the full JVM suite had 21 passing tests, including all 16 baseline tests. Debug APK and instrumentation APK assembly passed with configuration cache reused. An initial instrumentation compile failed on an invalid test import, corrected before the successful assembly. Historical release Kotlin/manifest checks passed before this refinement; release was not rebuilt for it. Gradle warns about features incompatible with Gradle 10.

`SessionStorageTest` exercises native Keystore round-trip, tamper cleanup and erasure in isolated device test storage. Existing `SearchParsingTest` checks actual Android JSON DTO parsing. These and the new `EntryScreenTest`/`ScannerContractTest` are **compiled, not executed**. No authorized disposable emulator/device was available: scanner permission/denial/cancel, actual camera decoding, Keystore hardware/platform behavior, process recreation, polling/401/leave against a running backend and visual prototype fidelity remain pending runtime proof. Unit tests and APK assembly do not establish those outcomes. Do not install on a personal device or start an emulator without explicit authorization.

Functional-demo automated verification **passed** when the parent ran the following command in the background using the existing environment above: exit 0, 77 actionable tasks (18 executed, 59 up-to-date), with configuration cache reused. Kotlin, JVM tests and instrumentation compilation executed for this change. The report contains 26 JVM tests with zero failures, errors or skips: all 21 previous tests plus five new queue ownership/input/error tests. Both debug and instrumentation APKs were generated. The four new Android JSON parsing/preservation tests were compiled, not executed.

The new tests were authored before implementation, but an initial failing execution was not observed because the worker lacked a background runner. The successful parent check is actual post-implementation verification, not evidence of a complete RED/GREEN cycle:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --offline --console=plain --configuration-cache -Porg.gradle.java.installations.auto-download=false -Pandroid.builder.sdkDownload=false
```

XML reports confirm all 26 JVM tests were discovered and passed. Android parsing tests require actual device execution for runtime proof; assembly alone only compiles them. Participant protected calls, lifecycle cancellation, rendered UI/accessibility and the full two-participant/staff demonstration remain unverified. No live backend, provider, seed, emulator or personal phone was used.
