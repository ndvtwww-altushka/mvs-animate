#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

if ! command -v opencode >/dev/null 2>&1; then
  echo "OpenCode CLI is not installed. Install it using the official OpenCode instructions first." >&2
  exit 1
fi

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_ROOT"
HOST="${OPENCODE_HOST:-127.0.0.1}"
PORT="${OPENCODE_PORT:-4096}"
exec opencode serve --hostname "$HOST" --port "$PORT"
