# Deploy eBuddy on Render with Blueprint mode

The repository now contains `render.yaml`, `Dockerfile.render`, and `render-entrypoint.sh`. The Blueprint creates a free Docker web service, a free Render Postgres database, and a free Render Key Value instance. It links both `DATABASE_URL` and the backend's `DB_URL` to Postgres, and links `REDIS_URL` to Key Value.

## Files to commit

Commit these files at the root of the repository that will be connected to Render:

```text
render.yaml
Dockerfile.render
render-entrypoint.sh
pom.xml
src/
```

The Render Dockerfile builds the Spring Boot application from source with Maven and Java 21. It does not require `target/ebuddy-backend-1.0.0.jar` to be committed.

## Mobile-only setup

Render Blueprints are connected to a Git repository. Render does not treat a phone file upload as a permanent source repository. The simplest phone-only workflow is:

1. Open GitHub in your mobile browser or the GitHub mobile application.
2. Create a new private repository, for example `ebuddy-backend`.
3. Upload the backend project files. If uploading a ZIP, extract it first; the repository root must contain `render.yaml`, not a parent folder containing it.
4. Ensure `render-entrypoint.sh` is executable. If GitHub's web editor cannot preserve the executable bit, the Dockerfile's `RUN chmod 0555 /app/render-entrypoint.sh` still makes it executable inside the image.
5. Open [Render Dashboard](https://dashboard.render.com) in the mobile browser.
6. Sign in and select **New** followed by **Blueprint**.
7. Connect GitHub, choose the repository, and select the branch containing `render.yaml`.
8. Leave **Blueprint Path** as `render.yaml` when it is at the repository root.
9. Review the resources Render proposes: `ebuddy-backend`, `ebuddy-postgres`, and `ebuddy-redis`.
10. Select **Deploy Blueprint**.
11. Wait for the Postgres and Key Value resources to provision and for the Docker build to finish.

Render will prompt for no JWT secret because the Blueprint uses `generateValue: true` for `JWT_SECRET`. Do not replace this with a password committed to Git.

## What the Blueprint configures

The web service uses the free plan and the Dockerfile at `Dockerfile.render`. It exposes the Spring health check at `/actuator/health` and uses Render's assigned `PORT` through the Spring configuration. Render's Blueprint sets `PORT` to `10000`, which is also the port exposed by the runtime image.

The Postgres resource is named `ebuddy-postgres`, uses database name `ebuddy`, and is linked as both:

```text
DATABASE_URL=Render Postgres connectionString
DB_URL=Render Postgres connectionString
```

The entrypoint converts a Render URL beginning with `postgresql://` into `jdbc:postgresql://` before starting Spring Boot. Existing local `jdbc:` URLs are left unchanged.

The Key Value resource is named `ebuddy-redis` and is linked as:

```text
REDIS_URL=Render Key Value connectionString
```

Spring Boot now prefers `REDIS_URL` and falls back to `REDIS_HOST` and `REDIS_PORT` for local Docker Compose deployments.

## After deployment

Open the web service in Render and copy its public URL, for example:

```text
https://ebuddy-backend.onrender.com
```

Verify health from a phone browser or a REST client:

```text
https://ebuddy-backend.onrender.com/actuator/health
```

Update the Android project's `API_BASE_URL` and `WS_URL` to use the deployed service:

```kotlin
buildConfigField("String", "API_BASE_URL", "\"https://ebuddy-backend.onrender.com/\"")
buildConfigField("String", "WS_URL", "\"wss://ebuddy-backend.onrender.com/v1/ws\"")
```

Rebuild the Android APK after changing these values. For the J2ME MIDlet, update its API base URL to the same HTTPS service before packaging or use the existing project build configuration if it already points there.

## Important free-tier limitations

Render's current pricing lists free Postgres with a **30-day limit**. Free Postgres is suitable for a temporary prototype, not durable production storage. Free Key Value provides 25 MB and no persistence, so it is suitable for presence and session cache only. The application must continue to work after Redis cache loss; users can log in again to rebuild presence and sessions.

The free web service is intended for development and hobby workloads. Expect cold starts or sleeping behavior depending on current Render free-service policy. Long-polling and WebSocket traffic may be interrupted during sleep or redeployments; the J2ME client retry loop and Android STOMP reconnect logic are intended to recover.

## Troubleshooting from a phone

If the Blueprint preview reports an unknown field, confirm that `render.yaml` is at the repository root and that the file uses `type: keyvalue`, not the deprecated `type: redis`. If the web service fails during startup, open its Render logs and check that Flyway can connect to Postgres and that the generated `JWT_SECRET` exists. If Redis fails, verify that the Key Value service is present and that the web service has a populated `REDIS_URL` environment variable.

If Render builds an old version, open the Blueprint page and choose **Manual Sync**, or push a new commit to the connected branch. Do not manage the same Render services from multiple Blueprints.

## Official references

- [Render Blueprint YAML reference](https://render.com/docs/blueprint-spec)
- [Render Infrastructure as Code setup](https://render.com/docs/infrastructure-as-code)
- [Render pricing and free resource limits](https://render.com/pricing)
