#!/bin/sh
set -eu

# Render may expose Postgres as postgresql://... or postgres://..., while
# HikariCP and Flyway require jdbc:postgresql://.... Normalize only the
# datasource URL supplied by Render; application.yml remains the fallback for
# local development when the variable is absent.
DATASOURCE_URL="${SPRING_DATASOURCE_URL:-}"
if [ -n "$DATASOURCE_URL" ]; then
  # Remove accidental whitespace/newline characters from copied environment values.
  DATASOURCE_URL=$(printf '%s' "$DATASOURCE_URL" | sed 's/[[:space:]]*$//')

  case "$DATASOURCE_URL" in
    postgresql://*) DATASOURCE_URL="jdbc:${DATASOURCE_URL}" ;;
    postgres://*) DATASOURCE_URL="jdbc:postgresql://${DATASOURCE_URL#postgres://}" ;;
    jdbc:postgresql://*) ;;
    *)
      echo "ERROR: SPRING_DATASOURCE_URL must use postgresql://, postgres://, or jdbc:postgresql://" >&2
      exit 1
      ;;
  esac

  # Some Render dashboard values were observed with the literal suffix
  # "Error" appended to the database URL. Remove that artifact before export.
  case "$DATASOURCE_URL" in
    *Error) DATASOURCE_URL="${DATASOURCE_URL%Error}" ;;
  esac

  case "$DATASOURCE_URL" in
    jdbc:postgresql://*/*) ;;
    *)
      echo "ERROR: SPRING_DATASOURCE_URL is not a complete JDBC URL: $DATASOURCE_URL" >&2
      exit 1
      ;;
  esac

  export SPRING_DATASOURCE_URL="$DATASOURCE_URL"
fi

exec java ${JAVA_OPTS:-} ${JAVA_TOOL_OPTIONS:-} -jar /app/app.jar
