# Privacy Policy — Net Tools

**Last updated:** November 24, 2025

This Privacy Policy explains how the **Net Tools** mobile application (package ID `net.tools.sys`, the “App”) handles information. The App helps you monitor server availability (ping, port checks, HTTP/HTTPS, and similar) for hosts you configure yourself.

We follow [Google Play’s Developer Program Policies](https://support.google.com/googleplay/android-developer/answer/10787469), including requirements for disclosing data collection and use.

## Summary

- No **account** is required.
- We do **not sell** or **share** your data with third parties for advertising or analytics.
- The App has **no ads** and **no developer-operated analytics SDKs**.
- Server lists, monitoring history, and settings are stored **on your device**.
- Network access is used **only** to check hosts **you configure**. Check results are **not sent** to the developer’s servers.

## 1. Data controller

**Data controller (developer):** Before publishing on Google Play, replace this line with your legal name or company, country, and support contact.

| | |
|---|---|
| **Privacy contact** | `ololoepololoe@gmail.com` |
| **App** | Net Tools (`net.tools.sys`) |

## 2. What data is processed

### 2.1. Data you enter

The App stores the following **locally on your device**:

- Server display names;
- IP addresses, hostnames, and port/protocol settings (TCP, HTTP, HTTPS, SSH, etc.);
- List order and other monitoring options you set in the UI.

This information is **provided by you**. The developer does not receive it over the internet.

### 2.2. Monitoring data

The device may store:

- Check outcomes (online/offline, response time, and related technical details);
- Availability history and statistics while you use monitoring features;
- Internal state from recent checks.

This is used only to show information in the App and for **local** notifications.

### 2.3. App settings

Stored locally, including:

- Theme preference (light/dark);
- Selected UI language;
- Scheduled/background monitoring and notification options (if enabled).

### 2.4. What we do not collect on purpose

We do **not** collect:

- Your name, postal address, phone number, or similar contact details (unless you type them yourself, e.g. in a server name field);
- Payment card data;
- Precise location (GPS);
- Cross-app advertising identifiers;
- Content from your servers beyond the technical response needed for an HTTP/HTTPS check to a URL you chose.

The App does **not** require sign-in with Google, Facebook, or other providers.

## 3. How data is used

On-device data is used **only** to:

- Run availability checks according to your settings;
- Display server status and history;
- Remember your preferences (language, theme);
- Show **local** notifications when status changes (if you granted notification permission).

We do **not** use your data for profiling, targeted advertising, or marketing networks.

## 4. Network and third parties

### 4.1. Connections to your servers

The App connects **only** to hosts and ports **you specify**, for ping, TCP, HTTP/HTTPS requests, and similar checks. Operators of those servers (you or others) may see technical request metadata (your device’s public IP, time of request, etc.) under their own policies.

The developer does **not** operate those servers and does **not** receive your check results on developer-controlled infrastructure.

### 4.2. Unencrypted HTTP

Checking servers over **HTTP** (without TLS) may use cleartext traffic. That matches the purpose of network monitoring and applies only to addresses you configure.

### 4.3. Third-party services

We do **not** send personal or usage data to partners, ad networks, analytics SDKs, or developer cloud backends—because the App has **no such integrations**.

**Google Play** and **Google** (store, updates, and Android backup if you enable it) may process data under [Google’s Privacy Policy](https://policies.google.com/privacy). That is outside the developer’s control.

## 5. Android permissions

| Permission | Purpose |
|------------|---------|
| `INTERNET` | Run network checks to addresses you configure |
| `ACCESS_NETWORK_STATE` | Detect whether the device has connectivity before checks |
| `POST_NOTIFICATIONS` | Show local alerts when server status changes (only if you allow) |

You may deny notification permission; monitoring still works except for those alerts.

## 6. Storage, backup, and export

- Primary storage is **on your device** (local database and preferences).
- **Export/import** of server lists as JSON is **initiated by you**; the file is saved wherever you choose in Android (another app or cloud). The developer does not receive the file.
- If **Android backup** is enabled (`allowBackup`), some app data may be included in your Google account backup per OS settings. That is controlled by **your device and Google**, not Net Tools.

## 7. Retention and deletion

Data remains on the device until you:

- Remove individual servers or history where the UI allows it; or
- **Uninstall the App**—local app data is generally removed with the app (except copies already in OS backups).

For questions about deletion, email the contact in section 1.

## 8. Security

We design the App so lists and history **do not leave your device** via developer channels. You are responsible for device security, Wi‑Fi/VPN credentials, and which hosts and ports you monitor.

No transmission or storage method is perfectly secure. HTTPS checks to **your** servers use standard TLS to the target host.

## 9. Children

The App is **not** directed at children under 13 (or the minimum age in your jurisdiction). We do not knowingly collect children’s personal data on developer servers.

## 10. International users

The App is distributed worldwide via Google Play. On-device processing is the same in every region. If you are in the EEA, UK, or another region with privacy laws, you may have rights of access, correction, and erasure—practically, manage data in the App or uninstall, and contact us using section 1.

## 11. Google Play Data safety (reference)

When completing **Data safety** in Play Console (as of this policy):

| Question | Answer |
|----------|--------|
| Is data collected? | Yes—**data you provide** (server configuration) and **app data** (monitoring history), **on device only**, not transmitted to the developer over the network |
| Shared with third parties? | **No** (from the App’s logic) |
| Sold? | **No** |
| Encryption in transit | Used for HTTPS checks **to your servers**; not applicable to developer data transfer (there is none) |
| Deletion requests | Delete in-app or uninstall; contact email in section 1 |

Update your console answers if you add accounts, cloud sync, analytics, etc.

## 12. Changes

We may update this Policy when the App or legal requirements change. The new version will be posted at the same URL listed in Google Play; the “Last updated” date will change. Material changes may also be noted in store release notes.

## 13. Contact

Privacy questions:

**Email:** `ololoepololoe@gmail.com`

---

**Publishing:** Host the HTML version at a public HTTPS URL (see `docs/GITHUB_PAGES.md`) and enter it in **Play Console → App content → Privacy policy**.

Russian version: [PRIVACY_POLICY.md](./PRIVACY_POLICY.md).