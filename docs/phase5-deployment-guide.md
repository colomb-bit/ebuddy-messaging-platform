# Phase 5: eBuddy End-to-End Build, Deployment, and Launch Guide

This guide deploys the generated Spring Boot backend, builds the Nokia C2-05 MIDlet, builds the native Android client, and verifies a live Android-to-J2ME conversation.

## 0. Required tools and deployment choices

The backend requires Java 21, Maven 3.8 or newer, Docker Engine with Docker Compose v2, PostgreSQL 16, and Redis 7. The J2ME build requires NetBeans 8.2 Mobility support together with Oracle Java ME SDK 3.4 or a Nokia Series 40 SDK. The Android build requires Android Studio Ladybug or newer, Android SDK platform 35, build tools, and a Gradle 8.7-compatible environment.

Use a DNS name with TLS for real devices. A private LAN address is sufficient for initial testing, but a Nokia C2-05 on a mobile operator network cannot reach a private `192.168.x.x` or `10.x.x.x` address unless the phone and server are on the same reachable network.

The generated application defaults to `https://api.example.com` and `wss://api.example.com/v1/ws`. Replace these values with the actual public server name before building clients.

## 1. Backend deployment with Docker Compose

### 1.1 Prepare the backend

Unpack the backend archive and enter the project directory:

```bash
unzip ebuddy-backend.zip -d ebuddy-backend
cd ebuddy-backend
```

Set a strong JWT secret. It must contain at least 32 UTF-8 bytes:

```bash
export JWT_SECRET="replace-this-with-a-random-secret-at-least-32-bytes-long"
```

Build the executable JAR before building the Docker image. The supplied Dockerfile copies this JAR from `target/`:

```bash
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
mvn clean package -DskipTests
```

### 1.2 Start the complete stack

The supplied `docker-compose.yml` starts PostgreSQL, Redis, and the Spring Boot application:

```bash
JWT_SECRET="$JWT_SECRET" docker compose up -d --build
```

Check service state and application logs:

```bash
docker compose ps
docker compose logs -f app
```

The application should report that Flyway completed and that the embedded server is listening on port `8080`. The health endpoint is:

```bash
curl -fsS http://127.0.0.1:8080/actuator/health
```

Expected result is a JSON response containing an `UP` status. Stop the stack with:

```bash
docker compose down
```

Use `docker compose down -v` only when intentionally deleting the local PostgreSQL volume and all development data.

### 1.3 Run PostgreSQL and Redis in Docker with the JAR outside Docker

This layout is useful during backend development:

```bash
cd ebuddy-backend
docker compose up -d postgres redis
```

Run the JAR with the same database and Redis values as the Compose services:

```bash
export DB_URL='jdbc:postgresql://127.0.0.1:5432/ebuddy'
export DB_USERNAME='ebuddy'
export DB_PASSWORD='ebuddy'
export REDIS_HOST='127.0.0.1'
export REDIS_PORT='6379'
export JWT_SECRET='replace-this-with-a-random-secret-at-least-32-bytes-long'
export PORT=8080
java -jar target/ebuddy-backend-1.0.0.jar
```

For a server process, use a service manager rather than a terminal session. A minimal systemd unit is:

```ini
[Unit]
Description=eBuddy Spring Boot backend
After=network-online.target docker.service

[Service]
User=ebuddy
WorkingDirectory=/opt/ebuddy-backend
Environment=DB_URL=jdbc:postgresql://127.0.0.1:5432/ebuddy
Environment=DB_USERNAME=ebuddy
Environment=DB_PASSWORD=replace-me
Environment=REDIS_HOST=127.0.0.1
Environment=REDIS_PORT=6379
Environment=JWT_SECRET=replace-with-at-least-32-random-bytes
Environment=PORT=8080
ExecStart=/usr/bin/java -jar /opt/ebuddy-backend/ebuddy-backend-1.0.0.jar
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

After saving it as `/etc/systemd/system/ebuddy.service`, run:

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now ebuddy
sudo systemctl status ebuddy
journalctl -u ebuddy -f
```

## 2. Server host, IP, firewall, and TLS configuration

### 2.1 Determine the server address

For a LAN-only test, find the server address:

```bash
hostname -I
ip -4 addr show
```

Use the address on the same network as the Android phone. Confirm the service is listening on all interfaces rather than only loopback:

```bash
ss -ltnp | grep ':8080'
curl -fsS http://SERVER_LAN_IP:8080/actuator/health
```

Spring Boot listens on all interfaces by default when no `server.address` is set. To make this explicit, add the following to `application.yml` or set it through an environment-specific configuration:

```yaml
server:
  address: 0.0.0.0
  port: 8080
```

Open the port on a host firewall when required:

```bash
sudo ufw allow 8080/tcp
sudo ufw status
```

On a cloud VM, also add an inbound TCP rule for port 8080 in the cloud security group. Do not expose PostgreSQL port 5432 or Redis port 6379 to the public Internet.

### 2.2 Recommended production topology

Expose only a reverse proxy on TCP 443. Proxy HTTPS REST requests to `127.0.0.1:8080` and proxy WebSocket upgrades from `/v1/ws` to the same backend. Use a DNS record such as `api.example.net` pointing to the public server IP. Obtain a certificate with Certbot or the platform certificate service.

The public client URLs then become:

```text
REST:      https://api.example.net/
WebSocket: wss://api.example.net/v1/ws
```

Verify both paths from an external network before installing either client:

```bash
curl -fsS https://api.example.net/actuator/health
curl -i -N \
  -H 'Connection: Upgrade' \
  -H 'Upgrade: websocket' \
  -H 'Sec-WebSocket-Version: 13' \
  -H 'Sec-WebSocket-Key: SGVsbG9XZWJTb2NrZXQ=' \
  https://api.example.net/v1/ws
```

A real WebSocket client should be used for the final upgrade check. The reverse proxy must preserve `Upgrade` and `Connection` headers and allow idle WebSocket connections for at least several minutes.

## 3. Create test users and contacts

The backend registration endpoint is:

```text
POST /api/j2me/register
```

It is intentionally available without authentication. Use it to create two test accounts before connecting the devices:

```bash
API='https://api.example.net'

curl -fsS -X POST "$API/api/j2me/register" \
  -H 'Content-Type: application/json' \
  -d '{"username":"android_demo","password":"AndroidPass123","displayName":"Android Demo"}'

curl -fsS -X POST "$API/api/j2me/register" \
  -H 'Content-Type: application/json' \
  -d '{"username":"nokia_demo","password":"NokiaPass123","displayName":"Nokia Demo"}'
```

Each response contains a JWT token. Save the token for the corresponding account or log in through the clients. To add contacts, first log in and copy each token, then call:

```bash
curl -fsS -X POST "$API/api/contacts?username=nokia_demo" \
  -H "Authorization: Bearer ANDROID_TOKEN"

curl -fsS -X POST "$API/api/contacts?username=android_demo" \
  -H "Authorization: Bearer NOKIA_TOKEN"
```

The duplicate registration request must return a structured conflict response rather than creating a second account.

## 4. Nokia C2-05 J2ME build and installation

### 4.1 Install the build environment

Install NetBeans 8.2 with Mobility support and one Java ME platform. In the Java ME SDK installation, confirm the following files exist:

```text
<WTK_HOME>/lib/cldcapi11.jar
<WTK_HOME>/lib/midpapi20.jar
<WTK_HOME>/bin/preverify
```

Use the JDK version supported by the selected Java ME SDK. Older SDKs commonly require JDK 8 for their tooling even though the generated bytecode targets CLDC 1.1.

### 4.2 Unpack and configure the project

```bash
unzip ebuddy-j2me.zip -d ebuddy-j2me
cd ebuddy-j2me
```

Edit `src/com/ebuddy/j2me/Models.java` and set:

```java
static final String API = "https://api.example.net";
```

Use `https://` for a public TLS endpoint. If the phone and server are on the same test LAN and the device supports the certificate and transport, a reachable HTTP URL can be used temporarily, but bearer credentials must not be sent over untrusted plaintext networks.

Open the directory in NetBeans 8.2. Register the CLDC/MIDP platform as `DefaultCLDC` or change `platform.active` and the platform name in `nbproject/project.xml` to match the installed SDK.

### 4.3 Build with NetBeans

In NetBeans:

1. Open the project folder.
2. Select the configured CLDC/MIDP platform.
3. Clean and Build the project.
4. Confirm that `dist/ebuddy.jar` and `dist/ebuddy.jad` were created.
5. Confirm the JAD `MIDlet-Jar-Size` equals the byte size of the JAR.

The NetBeans Mobility build runs Java compilation, preverification, packaging, and JAD generation for the selected platform.

### 4.4 Build with Ant and Java ME SDK

On Linux or macOS:

```bash
export WTK_HOME=/opt/Java_ME_SDK_3.4
export JAVA_HOME=/path/to/jdk-8
export PATH="$JAVA_HOME/bin:$PATH"
ant clean jar
```

The generated files are:

```text
dist/ebuddy.jar
dist/ebuddy.jad
```

The standalone Ant file copies the JAD but does not know the final JAR length until packaging completes. Set the JAR size before transfer:

```bash
JAR_SIZE=$(wc -c < dist/ebuddy.jar | tr -d ' ')
sed -i "s/^MIDlet-Jar-Size:.*/MIDlet-Jar-Size: $JAR_SIZE/" dist/ebuddy.jad
```

On Windows PowerShell, use the Java ME SDK command prompt and run:

```powershell
set WTK_HOME=C:\Java_ME_SDK_3.4
ant clean jar
(Get-Item dist\ebuddy.jar).Length
```

If the SDK provides `preverify.exe`, ensure it is the executable selected by the NetBeans platform or replace the Ant `preverify` property accordingly.

### 4.5 Transfer to the Nokia C2-05

Use one of these methods:

- Copy both files to a microSD card, insert it into the phone, open the file manager, and select the JAD file.
- Send the JAD and JAR over Bluetooth from a computer or another phone. Keep both files in the same folder.
- Use Nokia PC Suite or the vendor data-transfer utility if the device and operating system support it.
- Host the JAD and JAR on an HTTP/HTTPS server and open the JAD URL in the phone browser.

Install the **JAD**, not only the JAR. The JAD contains the MIDlet class, profile, configuration, permissions, and JAR size. Accept the installation prompts and place the application in Applications or Games.

If installation fails, check that the JAD URL matches the actual JAR filename, the JAR size is correct, the JAR is preverified, and the manifest contains `MicroEdition-Profile: MIDP-2.1` and `MicroEdition-Configuration: CLDC-1.1`.

### 4.6 Configure APN and 2G data

The exact menu labels vary by operator and firmware. On the C2-05, open the network or connectivity settings and configure:

1. Activate mobile data or packet data.
2. Create or select the operator Internet access point.
3. Enter the operator APN exactly as supplied by the carrier.
4. Leave username and password empty unless the carrier explicitly requires them.
5. Select the Internet access point as the default browser and application data profile.
6. Verify that the phone can open a normal HTTP/HTTPS page before launching eBuddy.
7. Disable Wi-Fi assumptions; the MIDlet uses the cellular data profile through `Connector.open`.

Do not guess the APN. Request the operator's Series 40 Internet configuration SMS or consult its support page. If the browser works but the MIDlet does not, check the Java application data-access permission and accept the “allow application to connect” prompt.

## 5. Android client build

### 5.1 Import into Android Studio

```bash
unzip ebuddy-android.zip -d ebuddy-android
```

Open Android Studio and select **Open**, then choose the extracted `ebuddy-android` directory. Allow Gradle synchronization to complete. Install Android SDK platform 35 and Android SDK Build-Tools through SDK Manager if Android Studio requests them.

### 5.2 Configure server URLs

Edit `app/build.gradle.kts` and replace these fields:

```kotlin
buildConfigField("String", "API_BASE_URL", "\"https://api.example.net/\"")
buildConfigField("String", "WS_URL", "\"wss://api.example.net/v1/ws\"")
```

The REST URL must end with `/`. The WebSocket URL must use `wss://` for TLS. Use `http://SERVER_LAN_IP:8080/` and `ws://SERVER_LAN_IP:8080/v1/ws` only for a controlled LAN test where cleartext traffic is intentionally permitted and the Android network security policy has been adjusted.

### 5.3 Build the APK

From Android Studio, choose **Build > Make Project**, then **Build > Build APK(s)**. The debug APK is normally produced at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

If a Gradle wrapper has been generated in the project, the command-line build is:

```bash
./gradlew clean :app:assembleDebug
```

If no wrapper exists, generate one with a compatible Gradle 8.7 installation or run the same task from Android Studio's Gradle tool window. Install the debug APK on an Android device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Grant Internet access automatically through the manifest. Launch the application, enter the Android test account, and confirm that the contact list loads.

## 6. End-to-end Android-to-Nokia test plan

### 6.1 Prepare accounts

Create `android_demo` and `nokia_demo` with the registration commands in section 3. Add each account as a contact of the other. Confirm that both accounts are active and that the server can issue tokens.

### 6.2 Start the services

Start the backend and dependencies:

```bash
cd ebuddy-backend
JWT_SECRET="$JWT_SECRET" docker compose up -d --build
curl -fsS https://api.example.net/actuator/health
```

Review logs while testing:

```bash
docker compose logs -f app
```

### 6.3 Connect the Nokia phone

1. Verify the C2-05 browser can reach the backend host.
2. Launch eBuddy.
3. Log in as `nokia_demo`.
4. Confirm the contact list contains `Android Demo`.
5. Open the Android contact chat.
6. Leave the chat active so the MIDlet long-poll request remains open.

The Nokia client calls `/api/j2me/poll` in a background thread. A 25-second empty response is a normal timeout and should be followed by another poll, not an error screen.

### 6.4 Connect Android

1. Launch the Android APK.
2. Log in as `android_demo`.
3. Confirm contacts are shown and the presence indicator is displayed.
4. Open the conversation with `Nokia Demo`.
5. Confirm the STOMP connection subscribes to `/user/queue/events`.
6. Send `Hello from Android`.

Expected flow:

1. Android writes an optimistic local message into Room.
2. Android calls `POST /api/messages` with a stable `client_message_id`.
3. Spring Boot persists the message and transactional outbox item.
4. The outbox publisher emits a STOMP event to the Nokia account's queue if it is connected through a modern client; the J2ME client receives it through `/api/j2me/poll`.
5. Nokia displays the message.
6. Nokia sends its delivery acknowledgement.
7. Android receives or synchronizes the status as `delivered`.

### 6.5 Reply from Nokia

On the C2-05:

1. Type `Hello from Nokia`.
2. Select Send.
3. Wait for the compact `OK` response.
4. Confirm the message appears in the chat history.

Expected Android behavior:

1. The Nokia request reaches `POST /api/j2me/send`.
2. PostgreSQL stores a `sent` message.
3. Android receives a STOMP `message.created` event or sees it during `/api/sync`.
4. The message is written to Room.
5. The Android client sends a `delivered` receipt.
6. Opening or viewing the conversation sends or triggers the `read` state according to the configured receipt flow.

### 6.6 Test reconnect and offline behavior

Test each of the following:

- Disable Android data for 10 seconds, send no message, re-enable it, and confirm STOMP reconnects.
- Close the Nokia lid or move out of coverage while Android sends a message. Reopen the application or restore coverage and confirm long-poll synchronization retrieves the message.
- Submit the same Android message twice with the same client ID through a REST test. Confirm only one database message exists.
- Kill and relaunch the Android app. Confirm Room still displays previously synchronized messages.
- Stop and restart Redis. Confirm the application remains available while presence is rebuilt on login.
- Stop and restart the backend. Confirm both clients retry and resynchronize rather than crash.

### 6.7 Diagnostic commands

Check recent message rows:

```bash
docker compose exec postgres psql -U ebuddy -d ebuddy \
  -c 'select message_id, sender_user_id, recipient_user_id, status, sequence_no, created_at from message order by sequence_no desc limit 20;'
```

Check outbox processing:

```bash
docker compose exec postgres psql -U ebuddy -d ebuddy \
  -c 'select outbox_id, message_id, event_type, state, attempts from message_outbox order by outbox_id desc limit 20;'
```

Check Redis presence:

```bash
docker compose exec redis redis-cli keys 'presence:*'
docker compose exec redis redis-cli get 'presence:USER_UUID'
```

Check API login manually:

```bash
curl -fsS -X POST https://api.example.net/api/j2me/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"nokia_demo","password":"NokiaPass123","client_type":"j2me"}'
```

## 7. Acceptance criteria

The deployment is accepted only when the health endpoint is `UP`, Flyway has applied successfully, PostgreSQL and Redis are reachable only on trusted interfaces, Android login succeeds, Nokia login succeeds over its configured APN, contacts appear on both clients, Android-to-Nokia and Nokia-to-Android messages arrive, delivery/read status transitions are monotonic, duplicate sends do not create duplicate messages, and both clients recover after a network interruption.

## References

[1]: https://developer.android.com/build "Android Build Documentation"
[2]: https://developer.android.com/develop/ui/compose "Jetpack Compose Documentation"
[3]: https://docs.oracle.com/javame/config/cldc/ref-impl/midp2.0/jsr118/ "MIDP 2.0 API Documentation"
[4]: https://docs.docker.com/compose/ "Docker Compose Documentation"
[5]: https://ant.apache.org/manual/ "Apache Ant Manual"
