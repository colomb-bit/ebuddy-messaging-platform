# J2ME registration patch

`POST /api/j2me/register` now accepts a validated username, password, and display name. The service normalizes the username, hashes the password with BCrypt, persists `app_user`, issues a J2ME JWT session, stores its SHA-256 token hash, and marks the user online in Redis. Duplicate usernames return a structured `409 IDEMPOTENCY_CONFLICT` response.

No Flyway schema migration was required. The existing `V1__initial_schema.sql` already contains the required nullable `password_hash`, unique `username_norm`, display name, state, and timestamp columns. The endpoint is explicitly permitted in Spring Security.
