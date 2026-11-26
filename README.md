# YouSong Android customer

Connected Compose client for the participant API. Spanish dark surfaces, music cards/details and bottom navigation follow the customer prototypes in `../documentation/Prototipo`. Queue/current-playing state, requests, votes and catalog suggestions use the backend. Playback is controlled by the owner; the customer app has no audio player, automatic advance, vote withdrawal or queue editing. The backend may report default-list playback without a request ID. No data or successful actions are fabricated. There is no FusionAuth login in the customer app.

## Run and scan

Open this application directory in Android Studio (compile/target SDK 37, minimum 24).

- **Debug paired phone:** currently uses `http://127.0.0.1:8080` through an explicitly selected, authorized ADB transport. Before opening the app, run `adb -s <PHONE_SERIAL> reverse tcp:8080 tcp:8080`. Recreate the mapping after a debugging transport disconnect. This does not expose a new LAN port or change the firewall. Install an update with `adb -s <PHONE_SERIAL> install -r app/build/outputs/apk/debug/app-debug.apk`; never clear app data or grant permissions automatically.
- **Debug emulator:** developers can configure `api_base_url` in `app/src/debug/res/values/strings.xml` to `http://10.0.2.2:8080` before building for the emulator.
- **Debug LAN phone:** build with `./gradlew assembleDebug -PyousongLanHost=192.168.1.21` (the PC's address on the trusted network). That debug build uses `http://192.168.1.21:8080` as its API origin and allows cleartext only to that host. The value must be a private IPv4 address (10.x.x.x, 172.16-31.x.x or 192.168.x.x); anything else fails the build. The Docker deployment in `yousong-backend/deploy` builds the APK this way.
- **Production/release:** developers configure `api_base_url` in `app/src/main/res/values/strings.xml` with an HTTPS origin, without path, query, credentials or fragment. The release default is intentionally empty.

Customers never configure the server or enter/paste codes or URLs. Missing or invalid developer configuration displays an honest service-configuration error; it never falls back to a QR host or a customer server editor.

Only these exact HTTP API origins are accepted in debug: `http://10.0.2.2:8080`, `http://127.0.0.1:8080` and, when configured, `http://<yousongLanHost>:8080`. The debug-only network security configuration is generated at build time (`generateDebugNetworkSecurity`) and allows cleartext for those hosts, without subdomains; Android's host-level policy cannot restrict ports, so the API validator additionally requires port 8080. Release rejects HTTP and does not include the debug network security configuration. Normal TLS validation remains enabled in both variants. Development HTTP exposes traffic on the local network: use only trusted development infrastructure, not production credentials.

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
| Colombian trends (anonymous) | `GET /api/public/tendencias` |
| Local rankings (anonymous) | `GET /api/public/establecimientos/{qrSlug}/destacadas?dias=30` |

Song objects may carry an optional Spanish `genero` label (absent, null or blank means no genre; a non-string or a label longer than 60 characters rejects the response like any other bad shape). `tendencias` returns at most 25 songs; `destacadas` returns `masVotadas`, `masPedidas`, `masSonadas` and `ultimasSonadas` with at most 10 entries each. Songs in these lists may come in the catalog shape (with `canal`) or the queue shape (without it). Both endpoints are called **without** the participant bearer, outside the queue's transport lock, once per connected screen and again only on an explicit refresh, a retry tap or (for `destacadas`) a change of the playing song. A failing list shows a quiet retry card and never blocks the queue. Sessions saved before `qrSlug` existed, or a `404` for the slug, hide the local's rankings.

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

## Design v2: "Noche de verbena"

The teachers found v1 too plain, and the team asked for a Spotify-like app that shows what Colombia is listening to and a whole new palette. Subject: a bar jukebox for a Colombian night out, used one-handed, in the dark, between conversations. The palette is a *verbena* (street party): violet night, mango paper lanterns, one aqua glow for what is live.

- **Color.** Dark is first-class: ground `#14101F`, surface `#1E1830`, raised `#2A2243`, hairline `#342B52` (decorative only), control outline `#8A7FB0`, text `#F6F1FF` / `#BDB4D6`, mango `#FFB547` (actions, rank numerals, badge), aqua `#3DD6C6` (now playing, live dot, focus, equalizer), error `#FF8F8F` on `#3A1F2B`, success `#CFF7EF` on `#17332F`. Light: `#F6F3FB` / `#FFFFFF` / `#ECE6F6`, text `#1B1530` / `#50476B`, outline `#7D7399`, accent text violet `#5B3FD0`, live teal `#09766B`. Every Material 3 role is defined in `ui/theme/Color.kt`; native windows, the scanner and the launcher icon (night ground, mango "Y", monochrome layer kept; legacy WebP icons regenerated from the same vector) mirror it.
- **Two written deviations from the brief.** (1) Light live teal is `#09766B`, not `#0B7F73`: the latter is 4.45:1 on the light background. (2) In light mode Material's `primary` is the violet, because Material also paints text buttons, progress, cursor and checkboxes with `primary`, and mango on white is 1.6:1. Mango stays the light-mode CTA fill through `YouSongExtras.cta` and `CtaButton`, which every filled button uses. Raised surfaces pass `contentColor = onSurface` explicitly, because `surfaceContainerHighest` (dark) and `surfaceContainerHigh` (light) equal `surfaceVariant`, and Material would otherwise pick the muted `onSurfaceVariant` for their text.
- **Type.** Manrope only (bundled). The greeting is 40sp ExtraBold with tight tracking; section headings 20sp ExtraBold; body 14-16sp. The one oversized element is the outlined mango rank numeral in "Tendencias en Colombia" (88dp, ignores font scale because it is decorative; the rank is read aloud as "Puesto N en Colombia"). Numbering is used only there, because the content is a real ranking.
- **Layout.** Inicio: greeting by time of day, the local and its live state, a now-playing hero (with "Sigue" and its vote; with nothing playing the head of the queue takes the hero; with an empty queue it invites the first request), then horizontal carousels with 148dp covers and 16dp radius, snap fling and 16dp content padding: "Tendencias en Colombia", "A continuación", "Lo más votado en {local}", "Últimas que sonaron". Buscar: a 60dp search field, "Explorar" tiles (genres from today's trends by frequency, then Colombian defaults; a trend cover tilted in the corner when there is one), result rows "Artista · Género · 3:34" with a quick add/vote. Cola keeps its function with genre chips. Detail sheet: square hero cover, genre chip, duration, one primary CTA. Mini-player shows how much of the song has played as of the last queue read. Radii grow with size: 8 row covers, 12 tiles and mini-player, 16 carousel covers, 24 hero, 28 sheet. Inicio and Explorar cap at 960dp, reading columns at 640dp.
- **Motion.** Only the playing equalizer moves on its own; vote counts pulse once when they change; covers cross-fade on load. All of it stops under the system "remove animations" setting. Skeletons are static.
- **Accessibility.** Headings on the greeting and every section, merged card/row semantics with "Ver detalle" click labels, content descriptions on covers that carry meaning, genre chips read as "Género: Salsa", 48dp targets, live regions on snackbars, skeletons and retry cards. Checked at font scale 2.0 and on an 840dp frame in the design gallery.

WCAG 2.x contrast for every text/background pair and control outline (text needs 4.5:1, controls 3:1). Genre tiles all carry `#1B1530` text.

**DARK**

| Pair | Foreground | Background | Ratio | Min |
| --- | --- | --- | --- | --- |
| onSurface/background | `#F6F1FF` | `#14101F` | 16.86:1 | 4.5:1 |
| onSurface/surface | `#F6F1FF` | `#1E1830` | 15.42:1 | 4.5:1 |
| onSurface/raised | `#F6F1FF` | `#2A2243` | 13.45:1 | 4.5:1 |
| onSurfaceVariant/background | `#BDB4D6` | `#14101F` | 9.49:1 | 4.5:1 |
| onSurfaceVariant/surface | `#BDB4D6` | `#1E1830` | 8.68:1 | 4.5:1 |
| onSurfaceVariant/raised | `#BDB4D6` | `#2A2243` | 7.57:1 | 4.5:1 |
| onSurfaceVariant/high #241D3A | `#BDB4D6` | `#241D3A` | 8.13:1 | 4.5:1 |
| primary text/background | `#FFB547` | `#14101F` | 10.64:1 | 4.5:1 |
| primary text/raised | `#FFB547` | `#2A2243` | 8.49:1 | 4.5:1 |
| onPrimary/primary | `#1B1530` | `#FFB547` | 9.99:1 | 4.5:1 |
| live aqua/background | `#3DD6C6` | `#14101F` | 10.35:1 | 4.5:1 |
| live aqua/raised | `#3DD6C6` | `#2A2243` | 8.26:1 | 4.5:1 |
| onTertiary/aqua | `#0B2B28` | `#3DD6C6` | 8.37:1 | 4.5:1 |
| error text/background | `#FF8F8F` | `#14101F` | 8.52:1 | 4.5:1 |
| error text/surface | `#FF8F8F` | `#1E1830` | 7.80:1 | 4.5:1 |
| onErrorContainer/errorContainer | `#FFD9DC` | `#3A1F2B` | 11.49:1 | 4.5:1 |
| onSuccess/success | `#CFF7EF` | `#17332F` | 11.74:1 | 4.5:1 |
| onPrimaryContainer/primaryContainer | `#FFE2B8` | `#4D3510` | 9.18:1 | 4.5:1 |
| onSecondaryContainer/secondaryContainer | `#E9E1FF` | `#3B3163` | 9.24:1 | 4.5:1 |
| secondary lilac/background | `#C9B8FF` | `#14101F` | 10.49:1 | 4.5:1 |
| inverseOnSurface/inverseSurface | `#1E1830` | `#F6F1FF` | 15.42:1 | 4.5:1 |
| outline control/background | `#8A7FB0` | `#14101F` | 5.11:1 | 3:1 |
| outline control/surface | `#8A7FB0` | `#1E1830` | 4.67:1 | 3:1 |
| outline control/raised | `#8A7FB0` | `#2A2243` | 4.08:1 | 3:1 |
| badge onPrimary/mango | `#1B1530` | `#FFB547` | 9.99:1 | 4.5:1 |
| white/coverScrim | `#FFFFFF` | `#14101F` | 18.69:1 | 4.5:1 |
| mini-player title onSurface/#2A2243 | `#F6F1FF` | `#2A2243` | 13.45:1 | 4.5:1 |
| genre chip onSecondaryContainer | `#E9E1FF` | `#3B3163` | 9.24:1 | 4.5:1 |

**LIGHT**

| Pair | Foreground | Background | Ratio | Min |
| --- | --- | --- | --- | --- |
| onSurface/background | `#1B1530` | `#F6F3FB` | 15.99:1 | 4.5:1 |
| onSurface/surface | `#1B1530` | `#FFFFFF` | 17.55:1 | 4.5:1 |
| onSurface/container | `#1B1530` | `#ECE6F6` | 14.39:1 | 4.5:1 |
| onSurfaceVariant/background | `#50476B` | `#F6F3FB` | 7.79:1 | 4.5:1 |
| onSurfaceVariant/surface | `#50476B` | `#FFFFFF` | 8.56:1 | 4.5:1 |
| onSurfaceVariant/container | `#50476B` | `#ECE6F6` | 7.01:1 | 4.5:1 |
| onPrimary/mango CTA | `#1B1530` | `#FFB547` | 9.99:1 | 4.5:1 |
| accent violet/background | `#5B3FD0` | `#F6F3FB` | 6.24:1 | 4.5:1 |
| accent violet/surface | `#5B3FD0` | `#FFFFFF` | 6.85:1 | 4.5:1 |
| accent violet/container | `#5B3FD0` | `#ECE6F6` | 5.61:1 | 4.5:1 |
| white/violet (onPrimary role) | `#FFFFFF` | `#5B3FD0` | 6.85:1 | 4.5:1 |
| live teal/background | `#09766B` | `#F6F3FB` | 5.01:1 | 4.5:1 |
| live teal/surface | `#09766B` | `#FFFFFF` | 5.50:1 | 4.5:1 |
| live teal/container | `#09766B` | `#ECE6F6` | 4.51:1 | 4.5:1 |
| error/background | `#B3261E` | `#F6F3FB` | 5.95:1 | 4.5:1 |
| onErrorContainer/errorContainer | `#410E0B` | `#FADCE0` | 12.67:1 | 4.5:1 |
| onSuccess/success | `#0B3D37` | `#D3F2EC` | 10.18:1 | 4.5:1 |
| onPrimaryContainer/primaryContainer | `#1B1530` | `#FFB547` | 9.99:1 | 4.5:1 |
| onSecondaryContainer/secondaryContainer | `#1F1248` | `#E4DCFF` | 12.90:1 | 4.5:1 |
| inverseOnSurface/inverseSurface | `#F6F1FF` | `#2B2442` | 13.21:1 | 4.5:1 |
| outline control/background | `#7D7399` | `#F6F3FB` | 4.00:1 | 3:1 |
| outline control/surface | `#7D7399` | `#FFFFFF` | 4.39:1 | 3:1 |
| outline control/container | `#7D7399` | `#ECE6F6` | 3.60:1 | 3:1 |
| genre chip onSecondaryContainer | `#1F1248` | `#E4DCFF` | 12.90:1 | 4.5:1 |
| badge #1B1530 on mango | `#1B1530` | `#FFB547` | 9.99:1 | 4.5:1 |

**GENRE TILES (dark text #1B1530)**

| Pair | Foreground | Background | Ratio | Min |
| --- | --- | --- | --- | --- |
| mango | `#1B1530` | `#FFB547` | 9.99:1 | 4.5:1 |
| aqua | `#1B1530` | `#3DD6C6` | 9.72:1 | 4.5:1 |
| rosa | `#1B1530` | `#FF8FB8` | 8.25:1 | 4.5:1 |
| lila | `#1B1530` | `#B9A6FF` | 8.33:1 | 4.5:1 |
| lima | `#1B1530` | `#C6E86B` | 12.67:1 | 4.5:1 |
| coral | `#1B1530` | `#FF9E7A` | 8.71:1 | 4.5:1 |
| cielo | `#1B1530` | `#7CC8FF` | 9.66:1 | 4.5:1 |
| arena | `#1B1530` | `#F2D49B` | 12.26:1 | 4.5:1 |

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

### UI v2 verification

On the authorized disposable emulator (AVD `Medium_Phone_API_37.0`, `emulator-5554`), the instrumented suite (55 tests, including fake-backend checks that public endpoints never carry the bearer) passed through `adb shell am instrument`, and the offline design gallery rendered every v2 screen and state in dark and light at font scale 1.0 and 2.0 plus small and 840dp frames. The gallery folder is selected with `-e gallery_folder <name>`. Trends and local rankings were verified only against fixtures and the fake backend, not a running backend.

Earlier note: no authorized disposable emulator/device was available. Scanner permission/denial/cancel, camera decoding, Keystore hardware/platform behavior, process recreation, polling/401/leave against a running backend, rendered UI/accessibility and the full two-participant/owner demonstration remain unverified. No live backend/provider mutations, seed, emulator or personal phone are used by this change. Do not install on a personal device or start an emulator without explicit authorization.
