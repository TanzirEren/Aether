# Aether — Android (Java + XML)

Realtime 1-to-1 chat + audio/video calls on **Firebase Auth + Realtime Database only**. No Kotlin, no Compose, no Firebase Storage, no paid service.

## Build the APK with GitHub Actions (3 steps)

1. **Create a Firebase project** (free Spark plan) → enable **Authentication → Email/Password** and **Realtime Database**.
   In Realtime Database → *Rules*, paste the contents of `database.rules.json` and publish.
2. **Put your Firebase config in the build.** Either:
   - edit `app/src/main/java/com/aether/chat/util/Constants.java` (`FB_API_KEY`, `FB_APP_ID`, `FB_PROJECT_ID`, `FB_DATABASE_URL`), **or**
   - add 4 repo secrets (Settings → Secrets and variables → Actions): `FB_API_KEY`, `FB_APP_ID`, `FB_PROJECT_ID`, `FB_DATABASE_URL`. The workflow injects them at build time.
   (Values are in Firebase Console → Project settings → Your apps → add an Android app with package `com.aether.chat`.)
3. **Push to GitHub** (branch `main`). Actions → *Build Aether APK* → download artifact **Aether-debug-apk** → install `app-debug.apk`.

If you skip step 2 the APK still builds; on launch it shows a "Firebase is not configured" dialog.

## Build in Codespaces / terminal
```bash
bash gradlew assembleDebug     # not ./gradlew
# APK: app/build/outputs/apk/debug/app-debug.apk
```
The `.devcontainer` installs JDK 17 + Android SDK 34 automatically.

## What's implemented
Auth (username **or** email login, register with transactional username claim, forgot password, show/hide + strength meter, remember-me) · Home (pinned-first, presence dot, unread badge, ticks, typing, search, `@username` lookup, unread/archived filters, pin/mute/archive/delete) · Chat (text, image, document, voice notes ≤90 s, reply, edit, reaction, forward, copy, delete for me/everyone, drafts, date dividers, link → in-app browser, image viewer with pinch-zoom, sent/delivered/read ticks, typing + last seen) · Friends (request/accept/reject/cancel/remove/block/unblock) · Updates feed (+badge) · Calls (WebRTC audio/video over RTDB signaling, history, missed-call alerts, 30 s timeout) · Profile/Settings (theme Midnight↔Daylight, privacy toggles, cache manager, blocked list, delete account) · local cache written on receipt (§2.2).

## Not in this build (be aware)
GIF/stickers, multi-image send, scheduled messages, QR friend-add, media gallery, in-chat search, wallpapers, chat export/import, PiP, Bluetooth routing, rich link-preview cards, downloadable Google Fonts (system sans-serif is used), real PNG logo (set `AETHER_LOGO_PNG_BASE64` in `Constants.java`; a vector Æ fallback is used until then).

## Honest notes
- **Not compiled/tested here** — I had no Android SDK in my sandbox. I validated resource IDs, imports and XML mechanically, but the first CI run may surface a compile error or two; paste the Actions log and it is quick to fix.
- **Push notifications work only while the app process is alive** (RTDB listeners). Notifications when the app is fully killed need FCM (PRD §11 marks that optional/future).
- **Calls use free public STUN only.** Calls across strict/symmetric NATs may fail without a TURN server (add one in `WebRTCManager.start()`).
- **Username login** requires `users/{uid}/email` and `usernames/{u}` to be publicly readable (so the app can resolve username → email before sign-in). That's inherent to the PRD's design.
- Schema tweaks vs the PRD (for security rules to work): `friendRequests/{to}/{fromUid}` (deterministic key), `sentRequests/`, `userPrivate/{uid}/blocked`, `callLog/{uid}`, and `userChats/.../lastKey|lastStatus|clearedAt|hidden`.
- Unsplash covers are dev placeholders — check Unsplash terms before commercial use.
- Firebase free-tier ceilings (≈100 simultaneous connections, 1 GB stored, 10 GB/month download) apply; Base64 media uses them quickly.

## Common errors
| Error | Fix |
|---|---|
| `./gradlew: No such file or directory` | use `bash gradlew assembleDebug` |
| `SDK location not found` | run `.devcontainer/setup-sdk.sh` or create `local.properties` with `sdk.dir=$HOME/android-sdk` |
| Gradle daemon killed / OOM | already set `-Xmx3072m`; use a 4-core Codespace |
| "Firebase is not configured" on launch | fill `Constants.java` or set the 4 GitHub secrets |
| Login says permission denied | publish `database.rules.json` in the Firebase console |
