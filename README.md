# eBuddy Messaging Platform

Cross-platform instant messaging platform with a Spring Boot 3 / Java 21 backend, a low-bandwidth Nokia C2-05 J2ME MIDlet, and a Kotlin/Jetpack Compose Android client.

## Render deployment

The repository root contains `render.yaml`, `Dockerfile.render`, and `render-entrypoint.sh`. In Render, choose **New → Blueprint**, connect this repository, select the desired branch, and deploy the Blueprint. See [`docs/render-deploy.md`](docs/render-deploy.md).

## Components

- Backend: Spring Boot, PostgreSQL/Flyway, Redis-compatible Key Value, JWT, REST, STOMP/WebSocket.
- Android: Kotlin, MVVM/Clean boundaries, Compose, Retrofit, OkHttp STOMP, Room, Coroutines/Flow.
- J2ME: MIDP 2.1 / CLDC 1.1 Nokia C2-05 client using low-bandwidth HTTP long-polling.
- Integration tests: Python `requests` and `websocket-client` suite under `integration-test/`.

No credentials, JWT secrets, database passwords, SDK paths, or local Gradle state are included.
