# eBuddy C2-05 J2ME MIDlet

This project targets **CLDC 1.1 / MIDP 2.1** and uses only `javax.microedition.lcdui`, `javax.microedition.io`, `javax.microedition.rms`, and CLDC/MIDP core classes. It avoids generics, annotations, reflection, heavyweight JSON libraries, and blocking work on the UI thread.

## Build with NetBeans 8.2

1. Install Oracle Java ME SDK 3.4 or a Nokia Series 40 SDK and NetBeans 8.2 Mobility support.
2. Register a CLDC/MIDP platform named `DefaultCLDC`.
3. Open this folder as a NetBeans Mobility project.
4. Set the backend URL in `src/com/ebuddy/j2me/Models.java` (`AppConfig.API`).
5. Build the project. NetBeans runs `javac`, `preverify`, and packages `dist/ebuddy.jar` and `dist/ebuddy.jad`.

The standalone Ant build requires `WTK_HOME` to point to the Java ME SDK installation. It expects `cldcapi11.jar`, `midpapi20.jar`, and `bin/preverify` below that directory.

## Runtime behavior

The UI thread only changes screens and fields. `NetworkWorker` owns all HTTP connections and retries each request up to four times with 1.5, 3, 4.5, and 6 second delays. Long-polling uses `/api/j2me/poll?cursor=<cursor>&wait=25&limit=20`; timeout is treated as an empty successful response. The cursor and bearer token are persisted in RMS so a dropped 2G connection can resume without replaying the entire conversation.

The parser is a bounded scanner that extracts strings, numbers, booleans, and object fragments without allocating a general-purpose object tree. Response bodies are capped at 4096 bytes. The chat input is capped at 240 characters for Series 40 memory and screen constraints.

## Backend compatibility

The login, poll, and send routes match the Spring Boot backend. The registration form submits `POST /api/j2me/register` with `username`, `password`, and `display_name`; the backend must expose that registration route because Phase 1 and the supplied backend implementation only define login, contacts, messages, and receipts. The application does not place tokens in URLs.

## Source compatibility

The sandbox does not contain the Java ME SDK API jars or `preverify`, so device bytecode generation cannot be executed here. The source is intentionally written in Java 1.3-compatible syntax and the build metadata names the required CLDC/MIDP boot class paths.
