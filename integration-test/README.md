# eBuddy End-to-End Integration Test

This suite validates both directions of the cross-platform messaging flow:

1. It registers and authenticates a fresh Android account and a fresh Nokia/J2ME account.
2. It sends Android-to-Nokia through STOMP `SEND /app/message`.
3. It calls `/api/j2me/poll` with the Nokia token and asserts the message body and recipient.
4. It sends Nokia-to-Android through `POST /api/j2me/send`.
5. It listens on the Android STOMP subscription `/user/queue/events` and asserts the message body and recipient.

The test creates unique users on every run, so it does not require cleanup. The backend must be running with PostgreSQL and Redis available, and the STOMP command handler from the patched backend must be deployed.

## Run locally

```bash
cd ebuddy-integration-test
python3 -m venv .venv
. .venv/bin/activate
python -m pip install -r requirements.txt
python test_suite.py --api http://127.0.0.1:8080 --ws ws://127.0.0.1:8080/v1/ws
```

For a deployed TLS endpoint:

```bash
EBUDDY_API=https://api.example.net \
EBUDDY_WS=wss://api.example.net/v1/ws \
python test_suite.py
```

For a local HTTPS server with a self-signed certificate, add `--insecure`. The default timeout is 30 seconds. Increase it for a slow GPRS or remote deployment:

```bash
python test_suite.py --timeout 60
```

Exit code `0` means both directions passed. Exit code `1` means a request, STOMP frame, timeout, or payload assertion failed. Console output identifies the phase that failed.
