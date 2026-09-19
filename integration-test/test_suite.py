#!/usr/bin/env python3
"""End-to-end Android/STOMP <-> Nokia/J2ME/REST messaging test.

The script expects the patched Spring Boot backend to be running. It registers
fresh users, authenticates them, sends in both directions, and fails loudly on
HTTP, STOMP, timeout, or payload assertions.
"""

import argparse
import json
import os
import sys
import time
import uuid
from dataclasses import dataclass
from typing import Any, Dict, Optional

import requests
import websocket


class TestFailure(RuntimeError):
    pass


@dataclass
class Account:
    username: str
    password: str
    user_id: str
    token: str


class EbuddyE2ETest:
    def __init__(self, api_url: str, ws_url: str, timeout: float, verify_tls: bool):
        self.api = api_url.rstrip("/")
        self.ws_url = ws_url
        self.timeout = timeout
        self.session = requests.Session()
        self.session.verify = verify_tls
        self.android: Optional[Account] = None
        self.nokia: Optional[Account] = None
        self.android_ws: Optional[websocket.WebSocket] = None

    def log(self, message: str) -> None:
        print(f"[e2e] {message}", flush=True)

    def fail_if(self, condition: bool, message: str) -> None:
        if condition:
            raise TestFailure(message)

    def json_request(self, method: str, path: str, **kwargs: Any) -> Dict[str, Any]:
        url = f"{self.api}{path}"
        response = self.session.request(method, url, timeout=self.timeout, **kwargs)
        if not response.ok:
            raise TestFailure(f"{method} {path} returned HTTP {response.status_code}: {response.text[:500]}")
        if not response.content:
            return {}
        try:
            return response.json()
        except ValueError as exc:
            raise TestFailure(f"{method} {path} returned non-JSON: {response.text[:500]}") from exc

    def register_and_login(self, prefix: str, password: str, client_type: str) -> Account:
        username = f"{prefix}_{uuid.uuid4().hex[:10]}"
        self.log(f"registering {username}")
        register = self.json_request(
            "POST",
            "/api/j2me/register",
            json={"username": username, "password": password, "displayName": prefix},
            headers={"Content-Type": "application/json"},
        )
        self.fail_if(not register.get("token"), f"registration did not return a token for {username}")

        self.log(f"authenticating {username} as {client_type}")
        login = self.json_request(
            "POST",
            "/api/auth/login",
            json={
                "username": username,
                "password": password,
                "client_type": client_type,
                "device_label": "automated-e2e",
            },
            headers={"Content-Type": "application/json"},
        )
        user = login.get("user") or {}
        self.fail_if(not login.get("token"), f"login did not return a token for {username}")
        self.fail_if(not user.get("id"), f"login did not return a user ID for {username}")
        return Account(username, password, user["id"], login["token"])

    @staticmethod
    def stomp_frame(command: str, headers: Dict[str, str], body: Optional[str] = None) -> str:
        lines = [command]
        lines.extend(f"{key}:{value}" for key, value in headers.items())
        return "\n".join(lines) + "\n\n" + (body or "") + "\x00"

    def connect_android_stomp(self) -> None:
        self.log("opening Android STOMP WebSocket")
        ws_headers = ["User-Agent: eBuddy-E2E/1.0"]
        self.android_ws = websocket.create_connection(
            self.ws_url,
            timeout=self.timeout,
            header=ws_headers,
            enable_multithread=True,
        )
        self.android_ws.send(
            self.stomp_frame(
                "CONNECT",
                {
                    "accept-version": "1.2",
                    "host": self.ws_url.split("/")[2],
                    "heart-beat": "10000,10000",
                    "authorization": f"Bearer {self.android.token}",
                    "client-id": "e2e-android",
                },
            )
        )
        command, _, _ = self.recv_stomp_frame()
        self.fail_if(command == "ERROR", "STOMP CONNECT returned ERROR")
        self.fail_if(command != "CONNECTED", f"expected CONNECTED, received {command}")
        self.android_ws.send(
            self.stomp_frame(
                "SUBSCRIBE",
                {"id": "e2e-user", "destination": "/user/queue/events", "ack": "client-individual"},
            )
        )
        self.log("Android STOMP subscription established")

    def recv_stomp_frame(self):
        if self.android_ws is None:
            raise TestFailure("Android WebSocket is not connected")
        raw = self.android_ws.recv()
        if raw is None:
            raise TestFailure("WebSocket closed while waiting for a STOMP frame")
        if isinstance(raw, bytes):
            raw = raw.decode("utf-8")
        raw = raw.rstrip("\x00")
        header_body = raw.split("\n\n", 1)
        head = header_body[0].splitlines()
        command = head[0].strip() if head else ""
        headers: Dict[str, str] = {}
        for line in head[1:]:
            if ":" in line:
                key, value = line.split(":", 1)
                headers[key] = value
        body = header_body[1] if len(header_body) == 2 else ""
        return command, headers, body

    def send_android_stomp_message(self, recipient: Account, text: str) -> str:
        client_id = f"android-{uuid.uuid4()}"
        body = json.dumps(
            {"to_user_id": recipient.user_id, "client_message_id": client_id, "body": text},
            separators=(",", ":"),
        )
        self.log(f"Android -> Nokia via STOMP: {text!r}")
        assert self.android_ws is not None
        self.android_ws.send(
            self.stomp_frame(
                "SEND",
                {"destination": "/app/message", "content-type": "application/json", "receipt": client_id},
                body,
            )
        )
        return client_id

    def poll_nokia_until(self, cursor: int, expected_text: str) -> Dict[str, Any]:
        deadline = time.monotonic() + self.timeout * 2
        current = cursor
        while time.monotonic() < deadline:
            self.log(f"Nokia long-poll cursor={current}")
            response = self.session.get(
                f"{self.api}/api/j2me/poll",
                params={"cursor": current, "wait": min(25, int(self.timeout)), "limit": 20},
                headers={"Authorization": f"Bearer {self.nokia.token}"},
                timeout=self.timeout + 10,
            )
            if not response.ok:
                raise TestFailure(f"Nokia poll failed HTTP {response.status_code}: {response.text[:500]}")
            page = response.json()
            for item in page.get("items", []):
                if item.get("body") == expected_text:
                    self.fail_if(item.get("to_user_id") != self.nokia.user_id, "Nokia received message addressed to another user")
                    return item
            try:
                current = int(page.get("next_cursor", current))
            except (TypeError, ValueError):
                pass
        raise TestFailure(f"Nokia did not receive {expected_text!r} before timeout")

    def send_nokia_rest_message(self, text: str) -> str:
        client_id = f"nokia-{uuid.uuid4()}"
        self.log(f"Nokia -> Android via REST: {text!r}")
        response = self.session.post(
            f"{self.api}/api/j2me/send",
            json={"to_user_id": self.android.user_id, "client_message_id": client_id, "body": text},
            headers={"Authorization": f"Bearer {self.nokia.token}", "Content-Type": "application/json"},
            timeout=self.timeout,
        )
        if not response.ok:
            raise TestFailure(f"Nokia send failed HTTP {response.status_code}: {response.text[:500]}")
        self.fail_if("OK" not in response.text, f"unexpected Nokia send response: {response.text}")
        return client_id

    def wait_android_message(self, expected_text: str) -> Dict[str, Any]:
        deadline = time.monotonic() + self.timeout * 2
        while time.monotonic() < deadline:
            command, headers, body = self.recv_stomp_frame()
            if command == "ERROR":
                raise TestFailure(f"STOMP ERROR: {body or headers}")
            if command == "MESSAGE":
                if headers.get("ack") or headers.get("message-id"):
                    ack_id = headers.get("ack") or headers.get("message-id")
                    self.android_ws.send(self.stomp_frame("ACK", {"id": ack_id, "subscription": "e2e-user"}))
                try:
                    envelope = json.loads(body)
                except ValueError as exc:
                    raise TestFailure(f"invalid STOMP JSON body: {body[:500]}") from exc
                data = envelope.get("data") or {}
                if data.get("body") == expected_text:
                    self.fail_if(data.get("to_user_id") != self.android.user_id, "Android received message addressed to another user")
                    return data
        raise TestFailure(f"Android did not receive {expected_text!r} before timeout")

    def run(self) -> None:
        self.log(f"backend REST: {self.api}")
        self.log(f"backend STOMP: {self.ws_url}")
        self.android = self.register_and_login("User_Android", "AndroidPass123", "android")
        self.nokia = self.register_and_login("User_Nokia", "NokiaPass123", "j2me")
        self.connect_android_stomp()

        android_text = f"android-to-nokia-{uuid.uuid4().hex[:8]}"
        self.send_android_stomp_message(self.nokia, android_text)
        nokia_received = self.poll_nokia_until(0, android_text)
        self.log(f"PASS Android -> Nokia: message {nokia_received.get('id')}")

        nokia_text = f"nokia-to-android-{uuid.uuid4().hex[:8]}"
        self.send_nokia_rest_message(nokia_text)
        android_received = self.wait_android_message(nokia_text)
        self.log(f"PASS Nokia -> Android: message {android_received.get('id')}")
        self.log("PASS complete bidirectional messaging flow")

    def close(self) -> None:
        if self.android_ws is not None:
            try:
                self.android_ws.send(self.stomp_frame("DISCONNECT", {}, None))
                self.android_ws.close()
            except Exception:
                pass


def main() -> int:
    parser = argparse.ArgumentParser(description="eBuddy Android/J2ME end-to-end integration test")
    parser.add_argument("--api", default=os.getenv("EBUDDY_API", "http://127.0.0.1:8080"), help="REST base URL")
    parser.add_argument("--ws", default=os.getenv("EBUDDY_WS", "ws://127.0.0.1:8080/v1/ws"), help="STOMP WebSocket URL")
    parser.add_argument("--timeout", type=float, default=30.0, help="per-request and receive timeout in seconds")
    parser.add_argument("--insecure", action="store_true", help="disable TLS verification for local HTTPS testing")
    args = parser.parse_args()
    test = EbuddyE2ETest(args.api, args.ws, args.timeout, not args.insecure)
    try:
        test.run()
        print("\nRESULT: PASS")
        return 0
    except (TestFailure, requests.RequestException, websocket.WebSocketException) as exc:
        print(f"\nRESULT: FAIL - {exc}", file=sys.stderr)
        return 1
    finally:
        test.close()


if __name__ == "__main__":
    raise SystemExit(main())
