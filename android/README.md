# eBuddy Android Native Client

Kotlin Android client using MVVM/Clean Architecture boundaries, Jetpack Compose, Retrofit 2 with Moshi, Room, encrypted session storage, and an OkHttp WebSocket STOMP 1.2 client.

## Backend configuration

The default production values in `app/build.gradle.kts` are:

```text
API_BASE_URL = https://ebuddy-backend.onrender.com/
WS_URL       = wss://ebuddy-backend.onrender.com/ws-chat
```

The backend supports these authentication endpoints:

```text
POST /api/auth/signin
POST /api/auth/signup
```

The legacy `/api/auth/login` endpoint remains available for compatibility. Signup accepts:

```json
{"username":"alice","password":"at-least-8-chars","displayName":"Alice"}
```

Signin accepts:

```json
{"username":"alice","password":"at-least-8-chars","client_type":"android","device_label":"Android"}
```

## Screens and navigation

`MainActivity.kt` provides the complete Compose flow:

1. `LoginScreen` submits username/password through `EbuddyViewModel.login()`.
2. `RegisterScreen` submits display name/username/password through `EbuddyViewModel.register()`.
3. `ContactsScreen` loads contacts and opens a selected conversation.
4. `ChatScreen` renders the Room `Flow` and sends messages using the existing offline-first REST path.
5. `EbuddyViewModel` restores an encrypted session during startup and routes the user directly to contacts when a valid saved session exists.

## STOMP contract

After login, `StompClient` connects with the JWT in the WebSocket `Authorization` header and STOMP `CONNECT` frame:

```text
CONNECT
accept-version:1.2
heart-beat:10000,10000
authorization:Bearer <JWT>
```

It then subscribes to:

```text
/user/queue/events
```

For an explicit real-time send test:

```kotlin
stomp.sendMessage(
    SendRequest(
        toUserId = recipientId,
        clientMessageId = UUID.randomUUID().toString(),
        body = "Hello from Android"
    )
)
```

Receipts use `/app/receipt`. The client reconnects after failures with exponential backoff up to 30 seconds, which accommodates Render free-tier cold starts. `OkHttp` REST requests use 20-second connect, 45-second read, and retry-on-connection-failure settings.

## Secure token storage

`Network.kt` uses `EncryptedSharedPreferences` backed by an Android Keystore `MasterKey`. The JWT and session metadata are encrypted at rest. On startup, `EbuddyRepository.restore()` restores the session, sets the interceptor token, and reconnects STOMP. Logout clears the encrypted store and closes the socket.

## Offline-first behavior

Messages are written to Room before a send request is attempted. Conversation screens render from Room `Flow`s. Successful REST sync and STOMP events upsert into Room. Every send uses a stable client-generated ID for retry/idempotency.

## Build and run

Open the `android` directory in Android Studio Ladybug or newer, allow Gradle sync, select an emulator or physical Android device, and run the `app` configuration. For a command-line debug build when Android SDK/Gradle are installed:

```bash
cd android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The current sandbox has no Android SDK or Gradle executable, so Android compilation must be performed in Android Studio or another Android SDK environment. Backend Maven compilation and Android source-contract validation were completed locally.
