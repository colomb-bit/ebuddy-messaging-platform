# eBuddy desktop Java WebSocket client

This Java 17 module uses OkHttp 4.12 and STOMP 1.2 over WebSocket.

## Backend endpoint

```text
wss://ebuddy-backend.onrender.com/ws-chat
```

The client sends a JWT in both the WebSocket `Authorization` header and the STOMP `CONNECT` frame. After `CONNECTED`, it subscribes to `/user/queue/events`.

## Build

```bash
cd desktop-client
mvn clean package
```

The library JAR is created at `target/ebuddy-desktop-client-1.0.0.jar`. Include OkHttp and its transitive dependencies on the runtime classpath.

## Usage

```java
EbuddyWebSocketClient client = new EbuddyWebSocketClient(
    "wss://ebuddy-backend.onrender.com/ws-chat",
    jwtFromLogin,
    new EbuddyWebSocketClient.Listener() {
        public void onConnected() { System.out.println("connected"); }
        public void onMessage(String eventJson) { System.out.println(eventJson); }
        public void onError(Throwable error, okhttp3.Response response) { error.printStackTrace(); }
        public void onClosed(int code, String reason) { System.out.println(reason); }
    });
client.connect();
client.sendMessage(recipientUserId, clientMessageId, "Hello from desktop");
client.sendReceipt(messageId, "delivered");
```

## STOMP contract

- WebSocket: `/ws-chat`
- Client send: `/app/message`
- Receipt send: `/app/receipt`
- User event subscription: `/user/queue/events`
- Authentication: `authorization:Bearer <JWT>` on `CONNECT`
