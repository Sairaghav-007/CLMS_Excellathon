#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "===================================================================="
echo "       CLMS - CORPORATE LEARNING MANAGEMENT SYSTEM LAUNCHER         "
echo "===================================================================="
echo ""

# 1. Check PostgreSQL
echo "[1/3] Checking PostgreSQL on port 5434..."
if nc -z localhost 5434 2>/dev/null; then
    echo "[OK] PostgreSQL is active on port 5434."
else
    echo "[WARN] PostgreSQL not detected on port 5434. Starting via Docker..."
    docker compose up -d postgres
    sleep 3
fi

# 2. Launch Backend in background
echo "[2/3] Launching Spring Boot Backend..."
(cd "$SCRIPT_DIR/Backend" && ./mvnw spring-boot:run) &
BACKEND_PID=$!
echo "[OK] Backend started with PID $BACKEND_PID"

# 3. Launch Frontend
echo "[3/3] Launching React/Vite Frontend..."
(cd "$SCRIPT_DIR/FrontEnd" && npm run dev) &
FRONTEND_PID=$!
echo "[OK] Frontend started with PID $FRONTEND_PID"

trap "kill $BACKEND_PID $FRONTEND_PID 2>/dev/null || true" EXIT

echo ""
echo "===================================================================="
echo "[SUCCESS] Services running. Press Ctrl+C to terminate."
echo "  - Frontend: http://localhost:5173"
echo "  - Backend:  http://localhost:8080"
echo "===================================================================="

wait
