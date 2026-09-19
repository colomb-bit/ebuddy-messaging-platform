# eBuddy Android Native Client

Kotlin Android client using MVVM and Clean Architecture boundaries. The UI is Jetpack Compose, persistence is Room, REST uses Retrofit 2 with Moshi, and live events use an OkHttp WebSocket STOMP 1.2 client.

## Configure the backend

Set `API_BASE_URL` and `WS_URL` in `app/build.gradle.kts`. Defaults are `https://api.example.com/` and `wss://api.example.com/v1/ws`.

The client uses:

- `POST /api/auth/login`
- `GET /api/contacts`
- `POST /api/messages`
- `GET /api/sync`
- `POST /api/messages/{id}/receipt`
- STOMP subscription `/user/queue/events`

## Offline-first behavior

Messages are written to Room before a send request is attempted. Conversation screens render only from Room `Flow`s. Successful REST sync and STOMP events upsert into Room. The STOMP client reconnects after transport failure using 1, 2, 4, 8, 16, and 30 second delays. REST requests use OkHttp retry-on-connection-failure and every send has a stable client-generated ID.

## Build

Open the project in Android Studio Ladybug or newer and run `./gradlew assembleDebug`. The included project uses Android Gradle Plugin 8.6.1, Kotlin 2.0.21, compile SDK 35, and min SDK 23.

The current sandbox has no Android SDK or Gradle executable, so Android Studio/Gradle compilation cannot be executed in this environment. The project includes source, Gradle configuration, manifest, Room KSP configuration, tests, and resource files required for an Android Studio build.
