#!/bin/sh
set -eu

# Render exposes Postgres as postgresql://..., while Hikari expects
# jdbc:postgresql://.... Preserve local jdbc URLs unchanged.
case "${DB_URL:-}" in
  postgresql://*) export DB_URL="jdbc:${DB_URL}" ;;
esac

exec java ${JAVA_TOOL_OPTIONS:-} -jar /app/app.jar
