# Retro Web Client

The client is a single dependency-free file:

```text
src/main/resources/static/index.html
```

## Preferred deployment: same Render backend origin

Because old UC Browser and Opera Mini implementations have limited CORS and JavaScript support, serve the file from the same Spring Boot origin. The application serves static resources automatically, so after deploying the backend open:

```text
https://ebuddy-backend.onrender.com/
```

The client uses the low-bandwidth REST contract:

```text
POST /api/j2me/login
GET  /api/j2me/poll?cursor=0&wait=20&limit=20
POST /api/j2me/send
```

It never opens a WebSocket. Long-polling is repeated with a short delay after each response, and network failures retry after five seconds. Render cold starts may make the first request slow; the UI reports the retry state instead of failing silently.

## Test procedure

1. Deploy the repository through the existing Render Blueprint.
2. Open `https://ebuddy-backend.onrender.com/` in the old mobile browser.
3. Enter an existing eBuddy username and password.
4. Enter the recipient's UUID in the **Recipient user ID** field. The UUID is available from the Android login/contact data or backend account record.
5. Send a short text message.
6. Leave the page open. Incoming messages are received through `/api/j2me/poll`.
7. Verify the same conversation from Android, J2ME, or the Java desktop client.

## Deploying as a separate static file

You can copy `index.html` to any static host or upload it directly to a web server. In that case edit this line near the top of the script:

```javascript
var API = 'https://ebuddy-backend.onrender.com';
```

The static host must support HTTPS. However, cross-origin requests require the backend to return an appropriate CORS header such as:

```text
Access-Control-Allow-Origin: https://your-static-host.example
```

Same-origin hosting through Render is recommended for old browsers because it avoids CORS preflight and compatibility problems.

## Browser limitations

The client intentionally uses old-compatible primitives: `XMLHttpRequest`, `setTimeout`, plain DOM methods, compact CSS, and no framework or external assets. It is not a WebSocket client. HTTPS/TLS support still depends on the phone's browser and certificate capabilities; a phone that cannot negotiate the Render certificate may need a compatible proxy or an older HTTP-compatible host, which is outside the secure production recommendation.
