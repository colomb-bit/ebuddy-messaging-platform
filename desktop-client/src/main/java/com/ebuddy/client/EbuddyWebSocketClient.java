package com.ebuddy.client;

import okhttp3.*;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Minimal STOMP 1.2 client for the eBuddy backend.
 * The listener receives the JSON event envelope from /user/queue/events.
 */
public final class EbuddyWebSocketClient implements AutoCloseable {
    public interface Listener {
        void onConnected();
        void onMessage(String eventJson);
        void onError(Throwable error, Response response);
        void onClosed(int code, String reason);
    }

    private final OkHttpClient http;
    private final String wsUrl;
    private final String jwt;
    private final Listener listener;
    private volatile WebSocket socket;

    public EbuddyWebSocketClient(String wsUrl, String jwt, Listener listener) {
        this.http = new OkHttpClient.Builder()
                .pingInterval(20, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
        this.wsUrl = Objects.requireNonNull(wsUrl, "wsUrl");
        this.jwt = Objects.requireNonNull(jwt, "jwt");
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    public void connect() {
        Request request = new Request.Builder()
                .url(wsUrl)
                .header("Authorization", "Bearer " + jwt)
                .build();
        socket = http.newWebSocket(request, new WebSocketListener() {
            @Override public void onOpen(WebSocket webSocket, Response response) {
                webSocket.send(frame("CONNECT",
                        "accept-version:1.2\nheart-beat:10000,10000\nauthorization:Bearer " + jwt,
                        null));
            }

            @Override public void onMessage(WebSocket webSocket, String text) {
                String command = text.substring(0, text.indexOf('\n')).trim();
                if ("CONNECTED".equals(command)) {
                    webSocket.send(frame("SUBSCRIBE",
                            "id:ebuddy-user\ndestination:/user/queue/events\nack:auto", null));
                    listener.onConnected();
                } else if ("MESSAGE".equals(command)) {
                    int separator = text.indexOf("\n\n");
                    String body = separator >= 0 ? text.substring(separator + 2) : "";
                    if (body.endsWith("\u0000")) body = body.substring(0, body.length() - 1);
                    listener.onMessage(body);
                } else if ("ERROR".equals(command)) {
                    listener.onError(new IllegalStateException(text), null);
                }
            }

            @Override public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                listener.onError(t, response);
            }

            @Override public void onClosed(WebSocket webSocket, int code, String reason) {
                listener.onClosed(code, reason);
            }
        });
    }

    public boolean sendMessage(String toUserId, String clientMessageId, String body) {
        String json = "{\"to_user_id\":\"" + escape(toUserId) +
                "\",\"client_message_id\":\"" + escape(clientMessageId) +
                "\",\"body\":\"" + escape(body) + "\"}";
        return send(frame("SEND", "destination:/app/message\ncontent-type:application/json", json));
    }

    public boolean sendReceipt(String messageId, String state) {
        String json = "{\"message_id\":\"" + escape(messageId) +
                "\",\"state\":\"" + escape(state) + "\"}";
        return send(frame("SEND", "destination:/app/receipt\ncontent-type:application/json", json));
    }

    private boolean send(String value) {
        WebSocket current = socket;
        return current != null && current.send(value);
    }

    private static String frame(String command, String headers, String body) {
        return command + "\n" + headers + "\n\n" + (body == null ? "" : body) + '\u0000';
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }

    @Override public void close() {
        WebSocket current = socket;
        socket = null;
        if (current != null) current.close(1000, "client close");
        http.dispatcher().executorService().shutdown();
        http.connectionPool().evictAll();
    }
}
