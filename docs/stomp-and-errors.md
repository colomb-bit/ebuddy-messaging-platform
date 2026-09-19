# STOMP Event Protocol and Error Standards

## 1. Transport contract

Modern Android clients connect to `wss://api.example.com/v1/ws` and speak **STOMP 1.2**. The WebSocket endpoint accepts a STOMP `CONNECT` frame with `accept-version:1.2`, `host`, `authorization:Bearer <token>`, and a client-generated `client-id`. The server responds with `CONNECTED` or a STOMP `ERROR` frame and closes the socket.

The server uses at-least-once event delivery. A client must persist an event before acknowledging it. On reconnect, the client first calls REST `/v1/sync` using its last cursor. A WebSocket event is a latency optimization, not the recovery mechanism.

## 2. Destinations and subscriptions

The client subscribes with a unique subscription ID:

```text
SUBSCRIBE
id:sub-user
 destination:/user/queue/events
 ack:client-individual

\u0000
```

The server sends only events authorized for the authenticated user. A client must not be allowed to subscribe to another user's queue. Conversation-specific subscriptions are optional and use `/user/queue/conversation/{user_id}` after authorization.

## 3. Event envelope

Every `MESSAGE` frame has `content-type:application/json`, a `message-id`, and a `subscription` header. The JSON envelope is compact but explicit:

```json
{
  "v": 1,
  "type": "message.created",
  "event_id": "01J...",
  "cursor": "10452",
  "occurred_at": 1760000000,
  "data": {
    "id": "7c9c...",
    "from_user_id": "...",
    "to_user_id": "...",
    "client_message_id": "a-42",
    "sequence": 10452,
    "body": "hello",
    "status": "sent",
    "created_at": 1760000000
  }
}
```

`event_id` is unique for observability and duplicate suppression. `cursor` is the server resume position. `sequence` orders messages in the global change stream. The client should ignore a duplicate `event_id` after confirming its stored cursor.

## 4. Event types

### `message.created`

Sent to the recipient when a new message is committed. The sender may also receive an echo if the client requests it through a future capability flag. The event data matches the REST `Message` shape.

### `message.status`

Sent to the sender when the recipient acknowledges delivery or reading:

```json
{
  "v": 1,
  "type": "message.status",
  "event_id": "01J...",
  "cursor": "10453",
  "occurred_at": 1760000001,
  "data": { "message_id": "7c9c...", "status": "delivered", "at": 1760000001 }
}
```

Status values are monotonic. A client may receive `read` without observing `delivered` during a reconnect; it should apply the highest status.

### `system.notice`

Reserved for non-message notices such as server maintenance. Clients must ignore unknown event types while retaining the cursor if the envelope is valid.

## 5. Acknowledgement protocol

With `ack:client-individual`, the client acknowledges the STOMP delivery after durable local persistence:

```text
ACK
id:<server-delivery-ack-id>
subscription:sub-user
message-id:<server-message-id>

\u0000
```

The client then calls the application receipt endpoint, or sends an application command over the optional `/app/receipt` destination:

```text
SEND
destination:/app/receipt
content-type:application/json
receipt:client-receipt-7

{"message_id":"7c9c...","state":"delivered"}\u0000
```

A STOMP protocol `ACK` proves local processing of a transport frame. The application receipt proves user-device delivery and is persisted in PostgreSQL. Both operations are idempotent.

The client may request a broker receipt for `SEND` and `SUBSCRIBE`. The server returns `RECEIPT` with the same `receipt` value. A missing receipt is a transport failure, not proof that the business command failed; the client retries using the same client identifier.

## 6. Heartbeats, limits, and reconnect

The client proposes `heart-beat:10000,10000`. The server may negotiate a larger interval for constrained devices. A client reconnects after two missed intervals, using exponential backoff with jitter. Maximum frame size is 64 KiB and maximum message body size is 4096 UTF-8 characters. The server closes idle unauthenticated connections and rejects frames with invalid UTF-8, missing `destination`, unsupported versions, or oversized bodies.

## 7. Unified error model

Every interface maps failures to a stable machine-readable code. Human messages are safe and short; they do not expose SQL errors, stack traces, token values, or account existence details.

| Condition | HTTP | STOMP | `retryable` | Stable code |
|---|---:|---|---:|---|
| Malformed JSON, missing field, invalid enum | 400 | `ERROR` then frame rejection | No | `INVALID_REQUEST` |
| Missing, expired, or revoked token | 401 | `ERROR` then close | No; re-authenticate | `AUTH_REQUIRED` |
| Valid token but unauthorized resource | 403 | `ERROR` | No | `FORBIDDEN` |
| Resource not found, without leaking existence | 404 | `ERROR` | No | `NOT_FOUND` |
| Duplicate idempotency key with same payload | 200/201 with original result | `RECEIPT` or normal event | No | `DUPLICATE_REPLAY` |
| Same idempotency key with different payload | 409 | `ERROR` | No | `IDEMPOTENCY_CONFLICT` |
| Rate limit or concurrency limit | 429 with `Retry-After` | `ERROR` with `retry-after` header | Yes | `RATE_LIMITED` |
| Temporary database, queue, or downstream failure | 503 | `ERROR` with `retry-after` | Yes | `TEMPORARILY_UNAVAILABLE` |
| Unhandled internal exception | 500 | `ERROR` then close if connection unsafe | Usually yes, with backoff | `INTERNAL_ERROR` |
| Read timeout / no new long-poll data | 200, empty items | Not applicable | Yes | `SYNC_TIMEOUT` |

HTTP errors use this shape:

```json
{
  "error": "conflict",
  "code": "IDEMPOTENCY_CONFLICT",
  "message": "The request identifier was already used with different content.",
  "request_id": "req-01J...",
  "retryable": false
}
```

STOMP errors use an `ERROR` frame with `message`, `code`, `request-id`, and `retry-after` when applicable. The body uses the same JSON error object. A server must send exactly one terminal protocol error for a rejected connection, then close it cleanly.

## 8. Zero-unhandled-exception standard

The boundary layer must validate every request before invoking domain code. Controllers and frame handlers use a final exception mapper. The mapper logs the exception with a request ID, classifies known database and transport failures, returns the stable error shape, and prevents stack traces from reaching clients. Unknown exceptions map to `INTERNAL_ERROR`.

Database uniqueness violations map to domain conflicts by constraint name. Serialization failures map to `INVALID_REQUEST`. Connection-pool exhaustion maps to `TEMPORARILY_UNAVAILABLE`. Cancellation, client disconnect, and long-poll timeout are normal control flow and must not be logged as server errors.

Each request is assigned a request ID at ingress and propagated through REST, STOMP, queue, and database logs. Every response path, including validation failure, authentication failure, timeout, handler exception, and connection close, must terminate with a defined response or protocol close. Error handling itself must be guarded so a failed error serializer falls back to a minimal plain-text response and logs the original failure.

## 9. Status transition rules

A receipt from the recipient may move `sent` to `delivered`, or `delivered` to `read`. Repeated receipts return success without changing timestamps. A sender cannot create a receipt for a message it did not receive. The server never moves a message backward and never treats an outbound HTTP 200 as recipient delivery.

## References

[1]: https://stomp.github.io/stomp-specification-1.2.html "STOMP Protocol Specification, Version 1.2"
[2]: https://www.rfc-editor.org/rfc/rfc6455 "The WebSocket Protocol"
[3]: https://www.rfc-editor.org/rfc/rfc9110 "HTTP Semantics"
[4]: https://www.rfc-editor.org/rfc/rfc6585 "Additional HTTP Status Codes"
