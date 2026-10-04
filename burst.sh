#!/usr/bin/env bash
set -e

BASE_URL="${1:-http://localhost:8080}"
echo "Running Paytm Seat Reservation Burst Script against: ${BASE_URL}"
python3 scripts/burst.py "$BASE_URL" "${@:2}"
