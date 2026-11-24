# Publishing Net Tools on Google Play

## App identity

| Field | Value |
|-------|--------|
| Application ID | `net.tools.sys` |
| Version name | `1.0` (see `app/build.gradle.kts`) |
| Version code | `1` |
| Min SDK | 29 |
| Target SDK | 37 |

Store listing graphics: `AppAssets_2026-11-18/Android/play_store_512.png` (512×512). Launcher icons are already under `app/src/main/res/mipmap-*`.

## 1. Create an upload keystore (one time)

```bash
mkdir -p release
keytool -genkeypair -v \
  -keystore release/net-tools-upload.jks \
  -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

Back up `release/net-tools-upload.jks` and passwords securely. If you lose them, you cannot update the app on Play with the same package name.

## 2. Configure signing

```bash
cp keystore.properties.example keystore.properties
# Edit paths and passwords to match your keystore.
```

`keystore.properties` and `release/*.jks` are gitignored.

## 3. Build the release App Bundle (AAB)

Google Play requires an **Android App Bundle**, not a plain APK:

```bash
./gradlew :app:bundleRelease --no-configuration-cache
```

Output: `app/build/outputs/bundle/release/app-release.aab`

Upload this file in [Play Console](https://play.google.com/console) → your app → **Production** (or **Testing**) → **Create new release**.

> **Note:** Without `keystore.properties`, Gradle signs the release bundle with the **debug** keystore (fine for local smoke tests only). Play will reject or require re-signing for production.

## 4. Play Console checklist

- **Developer account** — one-time registration fee.
- **App content**
  - **Privacy policy** — edit `site/index.html`, run `./scripts/update-gh-pages.sh`, push branch `gh-pages`; enable Pages on `gh-pages` / root (see `docs/GITHUB_PAGES.md`). Replace placeholder e-mails, paste `https://<user>.github.io/<repo>/` in **Play Console → App content → Privacy policy**. Required for apps with sensitive permissions (e.g. notifications).
  - **Data safety** — declare that server lists and monitoring history stay **on device**; no account; network used only to reach hosts the user configures.
  - **Ads** — No, if you do not show ads.
  - **Target audience** — Set accurately (not a kids app unless designed as such).
- **Store listing** — title, short/long description, screenshots (phone), feature graphic (1024×500), app icon (512×512).
- **Content rating** — complete the questionnaire (network tools / utilities).
- **App access** — all functionality available without login (unless you add accounts later).

## 5. Permissions (reviewer context)

| Permission | Why |
|------------|-----|
| `INTERNET` | Ping and port/HTTP checks to user-configured hosts |
| `ACCESS_NETWORK_STATE` | Detect when the device has no network |
| `POST_NOTIFICATIONS` | Optional alerts when monitored servers change state |

`usesCleartextTraffic` is enabled so users can check **HTTP** (non-TLS) endpoints. Mention this in Data safety / app description if asked.

## 6. Before each release

1. Bump `versionCode` (required) and `versionName` in `app/build.gradle.kts`.
2. Run `./gradlew :app:bundleRelease`.
3. Smoke-test a release build on a device (`adb install` from a universal APK if needed, or internal testing track).
4. Upload the new AAB and roll out.

## Optional: Play App Signing

Enable **Google Play App Signing** when creating the app. Google holds the app signing key; you keep the upload key (`net-tools-upload.jks`). Recommended for all new apps.

## 7. DEX code optimization (Android Vitals)

The release build uses **AGP 9.5.1** and **R8 full mode** across all packages (`isMinifyEnabled = true`, `isShrinkResources = true`, `optimization { enable = true }` without `packageScope` partial restrictions).

The generated `app-release.aab` embeds both:
- `BUNDLE-METADATA/com.android.tools/r8.json` — read directly by Play Console Android Vitals (~96.7% shrinking, ~95.6% optimization, ~96.8% obfuscation).
- `BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map` — used automatically by Play Console to deobfuscate crash and ANR stack traces.