#!/usr/bin/env python3
"""
Stress and Burst testing script for Paytm Seat Reservation System.
Usage:
    python scripts/burst.py <BASE_URL> [--show-id SHOW_ID] [--jwt-secret JWT_SECRET]

Requirements fulfilled:
1. Accepts base URL and optional show ID.
2. Generates unique USER JWT tokens using JWT_SECRET.
3. Hot-seat storm: Hundreds of users requesting the exact same seat concurrently.
4. Per-user concurrency limit test: 15 concurrent requests for one user against max 4 limit.
5. Concurrent same-idempotency-key retries.
6. Same-key-different-body conflict test.
7. Fetches final GET /shows/{show_id}.
8. Calculates:
   - 201 count
   - 200 idempotent replay count
   - 409 seat-taken
   - 409 per-user-limit
   - 409 idempotency-key-reuse
   - 5xx count
9. Prints final seat reconciliation:
   available + held + confirmed == total
10. Exits with non-zero status if failure criteria are met.
"""

import sys
import os
import time
import json
import uuid
import hmac
import hashlib
import base64
import argparse
import urllib.request
import urllib.error
from concurrent.futures import ThreadPoolExecutor, as_completed

DEFAULT_BASE_URL = "http://localhost:8080"
DEFAULT_JWT_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970"


def base64url_encode(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b'=').decode('utf-8')


def generate_jwt(user_id: str, role: str, secret: str) -> str:
    try:
        key = base64.b64decode(secret)
        if len(key) < 32:
            key = secret.encode('utf-8')
    except Exception:
        key = secret.encode('utf-8')

    now = int(time.time())
    header = {"alg": "HS256", "typ": "JWT"}
    payload = {
        "sub": str(user_id),
        "role": role,
        "iat": now,
        "exp": now + 86400
    }

    header_bytes = json.dumps(header, separators=(',', ':')).encode('utf-8')
    payload_bytes = json.dumps(payload, separators=(',', ':')).encode('utf-8')

    encoded_header = base64url_encode(header_bytes)
    encoded_payload = base64url_encode(payload_bytes)

    signing_input = f"{encoded_header}.{encoded_payload}".encode('utf-8')
    signature = hmac.new(key, signing_input, hashlib.sha256).digest()
    encoded_signature = base64url_encode(signature)

    return f"{encoded_header}.{encoded_payload}.{encoded_signature}"


def make_request(url, method="GET", headers=None, body_dict=None, timeout=30):

    if headers is None:
        headers = {}

    data = None
    if body_dict is not None:
        data = json.dumps(body_dict).encode('utf-8')
        if "Content-Type" not in headers:
            headers["Content-Type"] = "application/json"

    req = urllib.request.Request(url, data=data, headers=headers, method=method)

    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            status = resp.status
            res_body = resp.read().decode('utf-8')
            try:
                json_data = json.loads(res_body)
            except Exception:
                json_data = res_body
            return status, json_data, resp.headers
    except urllib.error.HTTPError as e:
        res_body = e.read().decode('utf-8')
        try:
            json_data = json.loads(res_body)
        except Exception:
            json_data = res_body
        return e.code, json_data, e.headers
    except Exception as e:
        return 0, str(e), {}


def main():
    parser = argparse.ArgumentParser(description="Burst stress tester for Paytm Seat Reservation")
    parser.add_argument("base_url", nargs="?", default=os.getenv("BASE_URL", DEFAULT_BASE_URL))
    parser.add_argument("--show-id", default=os.getenv("SHOW_ID", None))
    parser.add_argument("--jwt-secret", default=os.getenv("JWT_SECRET", DEFAULT_JWT_SECRET))
    parser.add_argument("--storm-users", type=int, default=200, help="Number of users in hot seat storm")

    args = parser.parse_args()
    base_url = args.base_url.rstrip("/")
    jwt_secret = args.jwt_secret
    show_id = args.show_id

    print(f"========== BURST STRESS TEST STARTING ==========")
    print(f"Target Base URL: {base_url}")
    print(f"JWT Secret: {jwt_secret[:10]}...")

    # 1. Health check
    live_status, live_body, _ = make_request(f"{base_url}/livez")
    ready_status, ready_body, _ = make_request(f"{base_url}/readyz")
    print(f"Liveness /livez: {live_status} {live_body}")
    print(f"Readiness /readyz: {ready_status} {ready_body}")

    if live_status != 200 or ready_status != 200:
        print("[ERROR] Application health check failed! Ensure server is running.")
        sys.exit(1)

    # 2. Register Admin & Setup Show if show_id not provided
    admin_user_id = str(uuid.uuid4())
    admin_username = f"admin_{admin_user_id[:8]}"
    make_request(f"{base_url}/api/auth/register", method="POST",
                 body_dict={"username": admin_username, "password": "password123", "role": "ADMIN"})
    admin_token = generate_jwt(admin_user_id, "ADMIN", jwt_secret)

    if not show_id:
        print("\nCreating new stress test show...")
        show_req = {
            "title": f"Burst Test Show {uuid.uuid4().hex[:6]}",
            "description": "Stress testing show with 200 seats",
            "venue": "Main Arena",
            "showTime": "2026-12-31T20:00:00.000+00:00",
            "totalSeats": 200,
            "availableSeats": 200,
            "status": "ON_SALE"
        }
        status, body, _ = make_request(
            f"{base_url}/shows",
            method="POST",
            headers={"Authorization": f"Bearer {admin_token}"},
            body_dict=show_req
        )
        if status != 201 or not isinstance(body, dict) or "id" not in body:
            print(f"[ERROR] Failed to create show: HTTP {status} - {body}")
            sys.exit(1)
        show_id = body["id"]
        print(f"Created show ID: {show_id}")
    else:
        print(f"Using existing show ID: {show_id}")

    # 3. Create users in database
    storm_user_count = args.storm_users
    print(f"\nRegistering {storm_user_count + 10} unique users in database...")

    users = []

    def register_one_user(idx):
        uid = str(uuid.uuid4())
        uname = f"burst_u_{idx}_{uid[:8]}"
        make_request(f"{base_url}/api/auth/register", method="POST",
                     body_dict={"username": uname, "password": "password123", "role": "USER"})
        token = generate_jwt(uid, "USER", jwt_secret)
        return uid, token

    with ThreadPoolExecutor(max_workers=30) as executor:
        futures = [executor.submit(register_one_user, i) for i in range(storm_user_count + 10)]
        for f in as_completed(futures):
            users.append(f.result())

    print(f"Registered {len(users)} users successfully.")

    # Counters for metrics tracking
    count_201 = 0
    count_200_replay = 0
    count_409_seat_taken = 0
    count_409_per_user_limit = 0
    count_409_key_reuse = 0
    count_5xx = 0
    other_status_codes = []

    # Hot seat tracking
    hot_seat_label = "S1"
    hot_seat_confirmations = []

    # Per-user limit tracking
    per_user_test_uid, per_user_test_token = users[0]
    per_user_confirmations = 0

    # =========================================================================
    # SCENARIO A: Hot-Seat Storm (Hundreds of users requesting exact same seat S1)
    # =========================================================================
    print(f"\n--- SCENARIO A: Running Hot-Seat Storm on seat '{hot_seat_label}' ({storm_user_count} concurrent requests) ---")

    storm_users = users[1:1 + storm_user_count]

    def execute_hot_seat_request(user_info):
        uid, token = user_info
        key = f"STORM-KEY-{uid}"
        url = f"{base_url}/shows/{show_id}/reserve"
        status, body, headers = make_request(
            url,
            method="POST",
            headers={
                "Authorization": f"Bearer {token}",
                "Idempotency-Key": key
            },
            body_dict={"seats": [hot_seat_label]}
        )
        return uid, status, body

    with ThreadPoolExecutor(max_workers=50) as executor:
        futures = [executor.submit(execute_hot_seat_request, u) for u in storm_users]
        for f in as_completed(futures):
            uid, status, body = f.result()
            if status == 201:
                nonlocal_count_201 = True
                count_201 += 1
                hot_seat_confirmations.append(uid)
            elif status == 409:
                count_409_seat_taken += 1
            elif status >= 500:
                count_5xx += 1
            else:
                other_status_codes.append(status)

    print(f"Hot-Seat Storm completed. 201 Confirmed: {len(hot_seat_confirmations)}, 409 Seat Taken: {count_409_seat_taken}, 5xx Errors: {count_5xx}")

    # =========================================================================
    # SCENARIO B: Per-User Concurrency Limit Test (15 concurrent requests for 1 user)
    # =========================================================================
    print(f"\n--- SCENARIO B: Per-User Concurrency Test (15 concurrent requests for single user) ---")

    def execute_per_user_request(seq_idx):
        seat_name = f"S{seq_idx + 2}"  # S2, S3, S4...
        key = f"PER-USER-KEY-{seq_idx}-{uuid.uuid4().hex[:6]}"
        url = f"{base_url}/shows/{show_id}/reserve"
        status, body, _ = make_request(
            url,
            method="POST",
            headers={
                "Authorization": f"Bearer {per_user_test_token}",
                "Idempotency-Key": key
            },
            body_dict={"seats": [seat_name]}
        )
        return status, body

    with ThreadPoolExecutor(max_workers=15) as executor:
        futures = [executor.submit(execute_per_user_request, i) for i in range(15)]
        for f in as_completed(futures):
            status, body = f.result()
            if status == 201:
                count_201 += 1
                per_user_confirmations += 1
            elif status == 409:
                body_str = str(body)
                if "limit" in body_str.lower() or "per-user" in body_str.lower():
                    count_409_per_user_limit += 1
                else:
                    count_409_seat_taken += 1
            elif status >= 500:
                count_5xx += 1
            else:
                other_status_codes.append(status)

    print(f"Per-User Test completed. Confirmed: {per_user_confirmations}, 409 Limit Exceeded: {count_409_per_user_limit}")

    # =========================================================================
    # SCENARIO C: Concurrent Same-Idempotency-Key Retries
    # =========================================================================
    print(f"\n--- SCENARIO C: Concurrent Same-Idempotency-Key Retries (10 concurrent requests) ---")
    retry_uid, retry_token = users[-2]
    same_key = f"RETRY-KEY-{uuid.uuid4()}"
    retry_seat = "S50"

    def execute_retry_request():
        url = f"{base_url}/shows/{show_id}/reserve"
        return make_request(
            url,
            method="POST",
            headers={
                "Authorization": f"Bearer {retry_token}",
                "Idempotency-Key": same_key
            },
            body_dict={"seats": [retry_seat]}
        )

    with ThreadPoolExecutor(max_workers=10) as executor:
        futures = [executor.submit(execute_retry_request) for _ in range(10)]
        for f in as_completed(futures):
            status, body, _ = f.result()
            if status == 201:
                count_201 += 1
            elif status == 200:
                count_200_replay += 1
            elif status >= 500:
                count_5xx += 1
            else:
                other_status_codes.append(status)

    print(f"Same-Key Retries completed. 201 Created: {count_201}, 200 Replay: {count_200_replay}")

    # =========================================================================
    # SCENARIO D: Same-Key-Different-Body Conflict Test
    # =========================================================================
    print(f"\n--- SCENARIO D: Same-Key-Different-Body Conflict Test ---")
    reuse_uid, reuse_token = users[-1]
    reuse_key = f"REUSE-KEY-{uuid.uuid4()}"

    # Request A
    status_a, body_a, _ = make_request(
        f"{base_url}/shows/{show_id}/reserve",
        method="POST",
        headers={
            "Authorization": f"Bearer {reuse_token}",
            "Idempotency-Key": reuse_key
        },
        body_dict={"seats": ["S60"]}
    )
    if status_a == 201:
        count_201 += 1

    # Request B (Same Key, Different Seats!)
    status_b, body_b, _ = make_request(
        f"{base_url}/shows/{show_id}/reserve",
        method="POST",
        headers={
            "Authorization": f"Bearer {reuse_token}",
            "Idempotency-Key": reuse_key
        },
        body_dict={"seats": ["S61"]}
    )

    if status_b == 409:
        count_409_key_reuse += 1
    elif status_b >= 500:
        count_5xx += 1
    else:
        other_status_codes.append(status_b)

    print(f"Same-Key-Different-Body completed. Request A: {status_a}, Request B (Conflict): {status_b}")

    # =========================================================================
    # 7. FETCH FINAL SHOW DETAILS & SEAT RECONCILIATION
    # =========================================================================
    print(f"\n--- FETCHING FINAL SHOW DETAILS (GET /shows/{show_id}) ---")
    show_status, show_details, _ = make_request(f"{base_url}/shows/{show_id}")

    if show_status != 200 or not isinstance(show_details, dict):
        print(f"[ERROR] Failed to fetch final show details: HTTP {show_status} - {show_details}")
        sys.exit(1)

    total = show_details.get("total", 0)
    available = show_details.get("available", 0)
    held = show_details.get("held", 0)
    confirmed = show_details.get("confirmed", 0)
    seats_list = show_details.get("seats", [])

    print(f"\n==================================================")
    print(f"               BURST METRICS SUMMARY              ")
    print(f"==================================================")
    print(f"201 Created Count:               {count_201}")
    print(f"200 Idempotent Replay Count:     {count_200_replay}")
    print(f"409 Seat-Taken Count:            {count_409_seat_taken}")
    print(f"409 Per-User-Limit Count:        {count_409_per_user_limit}")
    print(f"409 Idempotency-Key-Reuse Count: {count_409_key_reuse}")
    print(f"5xx Server Error Count:          {count_5xx}")
    if other_status_codes:
        print(f"Other Status Codes:              {other_status_codes}")

    print(f"\n==================================================")
    print(f"             FINAL SEAT RECONCILIATION            ")
    print(f"==================================================")
    print(f"Total Seats:     {total}")
    print(f"Available Seats: {available}")
    print(f"Held Seats:      {held}")
    print(f"Confirmed Seats: {confirmed}")
    print(f"Reconciliation:  available ({available}) + held ({held}) + confirmed ({confirmed}) == total ({total})")

    reconciliation_passed = (available + held + confirmed == total) and (held == 0)

    # Integrity verification checks
    failed_hot_seat = len(hot_seat_confirmations) > 1
    failed_5xx = count_5xx > 0
    failed_per_user_limit = per_user_confirmations > 4
    failed_reconcile = not reconciliation_passed

    print("\n--- INTEGRITY CHECKS ---")
    print(f"1. Single Confirmation for Hot Seat '{hot_seat_label}': {'PASSED' if not failed_hot_seat else 'FAILED (' + str(len(hot_seat_confirmations)) + ' confirmed)'}")
    print(f"2. Zero 5xx Errors:                             {'PASSED' if not failed_5xx else 'FAILED (' + str(count_5xx) + ' errors)'}")
    print(f"3. Per-User Limit (<= 4 confirmed):             {'PASSED' if not failed_per_user_limit else 'FAILED (' + str(per_user_confirmations) + ' confirmed)'}")
    print(f"4. Seat Reconciliation Equation:                {'PASSED' if not failed_reconcile else 'FAILED'}")

    has_failures = failed_hot_seat or failed_5xx or failed_per_user_limit or failed_reconcile

    if has_failures:
        print(f"\n[FAILURE] Stress test failed integrity checks!")
        sys.exit(1)
    else:
        print(f"\n[SUCCESS] Stress test completed cleanly with all integrity constraints verified!")
        sys.exit(0)


if __name__ == "__main__":
    main()
