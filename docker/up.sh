#!/usr/bin/env bash
# 고정 월드 데이터를 받아 두고(처음 한 번) 게임·MySQL·Redis 를 띄운다.
set -euo pipefail
cd "$(dirname "$0")"
URL=https://github.com/f-api/webcraft-server/releases/download/world-v1/world.sql.gz
SHA=3f2b22bc19edb505fc80acca8b1f067555b55bfda478a6b51523bc3f65b6d1a5
FILE=initdb/world.sql.gz
sum() { if command -v sha256sum >/dev/null; then sha256sum "$1"; else shasum -a 256 "$1"; fi | cut -d' ' -f1; }
if [ ! -f "$FILE" ] || [ "$(sum "$FILE")" != "$SHA" ]; then
  echo "고정 월드 데이터 받는 중(약 80MB)"
  curl -fL --retry 3 -o "$FILE" "$URL"
  [ "$(sum "$FILE")" = "$SHA" ] || { echo "월드 데이터 해시가 다릅니다." >&2; exit 1; }
fi
[ -f .env ] || cp env.example .env
docker compose -f compose.yml --env-file .env up -d
echo "기동 중입니다. 첫 실행은 월드 데이터를 넣느라 1~3분 걸립니다: http://localhost:$(grep -E '^PORT=' .env | cut -d= -f2)/"
