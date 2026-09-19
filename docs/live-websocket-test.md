# Live WebSocket/STOMP test guide

## 1. Connection contract

Use these production URLs:

```text
REST:       https://ebuddy-backend.onrender.com
WebSocket:  wss://ebuddy-backend.onrender.com/ws-chat
```

The backend uses STOMP 1.2 over WebSocket:

| Operation | Destination |
|---|---|
| Connect | STOMP `CONNECT` with `authorization:Bearer <JWT>` |
| Subscribe | `/user/queue/events` |
| Send message | `/app/message` |
| Send receipt | `/app/receipt` |

The WebSocket HTTP upgrade itself is permitted; authentication is enforced when the STOMP `CONNECT` frame is processed.

## 2. Create JWTs

The simplest route is to let the integration test create two temporary accounts. It registers and logs in an Android account and a Nokia/J2ME account through REST. For manual clients, use the same REST contract:

```bash
BASE=https://ebuddy-backend.onrender.com

curl -sS -X POST "$BASE/api/j2me/register" \
  -H 'Content-Type: application/json' \
  -d '{"username":"desktop_demo_001","password":"ChangeMe123!","displayName":"Desktop Demo"}'

curl -sS -X POST "$BASE/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"desktop_demo_001","password":"ChangeMe123!","client_type":"android","device_label":"desktop-test"}'
```

Save the returned `token` and `user.id`. Create a second account for the Android device. The JWT must be sent in both places used by the clients: the WebSocket `Authorization` header and the STOMP `authorization` native header.

## 3. Run the automated live exchange

From the repository:

```bash
cd integration-test
python3 -m pip install requests websocket-client
python3 test_suite.py --api https://ebuddy-backend.onrender.com \
  --ws wss://ebuddy-backend.onrender.com/ws-chat \
  --timeout 45
```

The script creates fresh users and verifies:

1. Android JWT registration/login.
2. Nokia/J2ME JWT registration/login.
3. Android STOMP `CONNECT` and `/user/queue/events` subscription.
4. Android STOMP `SEND` to `/app/message`.
5. Nokia long-poll receipt through `/api/j2me/poll`.
6. Nokia REST send through `/api/j2me/send`.
7. Android STOMP receipt of the second message.

Expected final output:

```text
RESULT: PASS
```

## 4. Android app test

The Android build is already wired to:

```text
API_BASE_URL = https://ebuddy-backend.onrender.com/
WS_URL       = wss://ebuddy-backend.onrender.com/ws-chat
```

Build and install from Android Studio, or from the `android` directory when the local Gradle wrapper/SDK is available. Log in with one test account. `EbuddyRepository.login()` stores the JWT and calls `stomp.connect()`. The OkHttp STOMP client then sends `CONNECT`, subscribes to `/user/queue/events`, and reconnects with exponential backoff.

For a direct real-time send from a ViewModel or test action:

```kotlin
stomp.sendMessage(
    SendRequest(
        toUserId = recipientId,
        clientMessageId = UUID.randomUUID().toString(),
        body = "Hello from Android"
    )
)
```

Use the existing REST `repository.send(...)` path when offline-first persistence is required; use `sendMessage(...)` to explicitly test the STOMP path.

## 5. Java desktop JAR test

Build the client:

```bash
cd desktop-client
mvn clean package
```

Use the class in a Java 17 application:

```java
EbuddyWebSocketClient client = new EbuddyWebSocketClient(
    "wss://ebuddy-backend.onrender.com/ws-chat",
    desktopJwt,
    new EbuddyWebSocketClient.Listener() {
        public void onConnected() { System.out.println("STOMP connected"); }
        public void onMessage(String json) { System.out.println("event=" + json); }
        public void onError(Throwable t, okhttp3.Response r) { t.printStackTrace(); }
        public void onClosed(int code, String reason) { System.out.println("closed=" + reason); }
    });

client.connect();
// Wait for onConnected before sending.
client.sendMessage(androidUserId, UUID.randomUUID().toString(), "Hello from Java desktop");
```

The client subscribes to `/user/queue/events` after `CONNECTED`. Incoming event JSON is an envelope with the message under `data`.

## 6. Expected troubleshooting signals

- `CONNECTED` means the JWT was accepted by the STOMP interceptor.
- An STOMP `ERROR` frame or HTTP 401 means the JWT is missing, expired, or malformed.
- HTTP 404/426 during the upgrade usually indicates the wrong path; use `/ws-chat`, not `/ws`.
- A connection that opens but receives no messages usually means the client did not subscribe to `/user/queue/events`, the recipient user ID is wrong, or the sender and recipient accounts are not distinct.
- Render may sleep on its free tier. The first REST request can take several seconds; use a 45-second timeout for the first live test.
