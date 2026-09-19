#!/bin/sh
set -eu

# Render exposes Postgres as postgresql://..., while Hikari and Flyway expect
# jdbc:postgresql://.... Preserve an already JDBC-prefixed URL unchanged.
case "${SPRING_DATASOURCE_URL:-}" in
  postgresql://*) export SPRING_DATASOURCE_URL="jdbc:${SPRING_DATASOURCE_URL}" ;;
  postgres://*) export SPRING_DATASOURCE_URL="jdbc:postgresql://${SPRING_DATASOURCE_URL#postgres://}" ;;
esac

exec java ${JAVA_OPTS:-} ${JAVA_TOOL_OPTIONS:-} -jar /app/app.jar
