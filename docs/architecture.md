# eBuddy-Style Messaging Platform: System Architecture

**Status:** Design baseline for implementation review  
**Scope:** Modern Android clients using REST and WebSocket/STOMP, plus Nokia C2-05-class J2ME clients using low-bandwidth HTTP REST and long-polling.

## 1. Architectural conclusion

The platform should use a single durable messaging core with two protocol edges. The **modern edge** exposes JSON REST for authentication, history, contacts, and recovery, and STOMP over WebSocket for near-real-time delivery. The **legacy edge** exposes a deliberately small REST surface with short JSON responses and long-polling for inbound messages. Both edges call the same application services and PostgreSQL persistence model, so delivery semantics do not diverge by device type.

Messages are durably accepted before an API reports `sent`. A message becomes `delivered` only after the recipient device acknowledges receipt. It becomes `read` only after an explicit read acknowledgement. All commands are idempotent through client-generated identifiers and server-side uniqueness constraints. This is essential because GPRS retries can repeat requests and long-poll connections can terminate without a response.

## 2. Logical components

| Component | Responsibility | Availability and failure behavior |
|---|---|---|
| API gateway | TLS termination, request IDs, rate limits, content limits, routing | Rejects malformed or oversized requests before application code. |
| Auth/session service | Credential verification, token issuance, token rotation, revocation | Uses hashed opaque session tokens and bounded expiry. |
| Messaging service | Message acceptance, conversation authorization, status transitions, idempotency | Commits message and outbox record in one database transaction. |
| Contact service | Contact requests, accepted contacts, blocking, list pagination | Enforces ownership and uniqueness in PostgreSQL. |
| Sync service | Cursor-based replay of missed messages and status changes | Reads an ordered change stream represented by message IDs and timestamps. |
| Realtime gateway | STOMP authentication, subscriptions, fan-out, reconnect handling | Treats WebSocket delivery as at-least-once; clients recover with REST sync. |
| Legacy adapter | Compact REST resources, long-poll timeout, response shaping | Uses small payloads, no WebSocket requirement, and safe retry semantics. |
| PostgreSQL | Source of truth for users, contacts, messages, receipts, sessions | Primary with synchronous replica or managed HA; backups and point-in-time recovery required. |
| Outbox/queue | Decouples committed writes from fan-out and push work | A worker retries transient delivery failures without changing message truth. |

The outbox is an implementation component rather than a second source of truth. A message row is authoritative. An outbox item records work that must be attempted after commit.

## 3. Message lifecycle

1. The client sends a message with a stable `client_message_id`.
2. The messaging service authenticates the session and authorizes the sender-recipient relationship.
3. PostgreSQL inserts the message, its initial `sent` status, and an outbox record in one transaction. A duplicate `client_message_id` returns the original message instead of creating a second message.
4. The realtime or legacy delivery worker attempts delivery. A recipient acknowledgement changes the message to `delivered`.
5. A read acknowledgement changes it to `read`. Status transitions are monotonic: `sent → delivered → read`.
6. If a client loses connectivity, it calls `/v1/sync` with its last cursor. The server returns missed messages and status updates in deterministic order.

The server must never infer `delivered` from TCP, WebSocket, or HTTP success. Those signals only prove transport-level acceptance.

## 4. Client profiles

### Modern Android

The Android client uses REST for login, contacts, history, and catch-up. It opens an authenticated STOMP connection over WebSocket and subscribes to a user queue. Every received message is persisted locally before sending a delivery acknowledgement. The client sends a read acknowledgement when the conversation is visibly opened. Reconnect always performs REST synchronization before relying on live events.

### Nokia C2-05 / J2ME over GPRS

The J2ME client uses short-lived authenticated HTTP requests and one long-poll request. It should request at most 20 messages or approximately 8 KB per response, whichever comes first. It sends compact field names only where the client contract defines them, uses integer timestamps in UTC seconds, and stores the last successful cursor locally. On timeout it reconnects with the same cursor. On a repeated send it reuses the same `client_message_id`.

The legacy API avoids nested objects, media, typing indicators, presence streams, server-side search, and large history pages. Optional features must be omitted rather than returned as `null` fields. GZIP may be negotiated, but the client must work without it.

## 5. Consistency, ordering, and retry model

The system provides **at-least-once delivery** with idempotent commands. It does not promise exactly-once network delivery. Within one sender-recipient conversation, the server assigns a monotonically increasing `sequence_no`; clients use it for ordering and gap detection. A missing sequence causes the client to pause rendering later messages and call synchronization.

PostgreSQL transactions protect message creation and status transitions. Workers use `SELECT ... FOR UPDATE SKIP LOCKED` or an equivalent queue mechanism for outbox processing. The service may retry transient database and network errors. It must not retry authorization failures, schema failures, or explicit business conflicts without changing the request.

## 6. Security baseline

All traffic uses TLS. Passwords are stored with a memory-hard password hash such as Argon2id. Session tokens are opaque random values; only SHA-256 token hashes are stored. Tokens are scoped to one device session, expire, and can be revoked. The client sends tokens in the `Authorization: Bearer` header; legacy clients may use the same header but must not place tokens in URLs.

The gateway applies per-user and per-IP rate limits. Message bodies are UTF-8, length-limited, normalized for control characters, and stored as text. Server logs contain request IDs and outcome codes but never bearer tokens, passwords, or full message bodies. Access to a message requires that the authenticated user is its sender or recipient and is not blocked by the applicable policy.

## 7. Operational requirements

Health checks distinguish liveness from readiness. Readiness is false when PostgreSQL migrations are incomplete or the primary database cannot accept transactions. Metrics should include request latency by route and client profile, authentication failures, long-poll timeouts, outbox age, duplicate-command rate, database connection saturation, and message delivery lag. Structured logs use the request ID returned to clients.

Backups must be encrypted and periodically restored into a test environment. Schema changes are forward-compatible: add nullable columns first, deploy readers, backfill, then enforce constraints. Legacy clients should receive stable error codes even when the server implementation changes.

## 8. Suggested deployment topology

A regional deployment contains a TLS load balancer, stateless API and realtime gateway instances, a worker pool, PostgreSQL primary with replicas, and a durable queue or PostgreSQL-backed outbox. WebSocket stickiness is optional because connection state is local and fan-out can use a shared broker; REST remains stateless. The long-poll timeout should be 25 seconds, below common proxy idle limits. A client retry delay of 2, 5, 15, and 30 seconds with jitter prevents a 2G reconnect storm.

## 9. Review boundaries before implementation

The attached DDL is the persistence contract. The REST specification is the contract for both Android recovery flows and J2ME operation. The STOMP specification is an event transport contract only; it does not replace REST synchronization. The error standard applies to every HTTP response, STOMP `ERROR` frame, and internal service boundary.

## References

[1]: https://www.rfc-editor.org/rfc/rfc6455 "The WebSocket Protocol"
[2]: https://stomp.github.io/stomp-specification-1.2.html "STOMP Protocol Specification, Version 1.2"
[3]: https://www.rfc-editor.org/rfc/rfc9110 "HTTP Semantics"
[4]: https://www.rfc-editor.org/rfc/rfc6750 "The OAuth 2.0 Authorization Framework: Bearer Token Usage"
[5]: https://www.postgresql.org/docs/current/ddl-constraints.html "PostgreSQL Documentation: Constraints"
