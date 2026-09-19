#!/bin/sh
set -eu

# The datasource URL is intentionally fixed in application.yml. Remove any
# stale Render variable so a corrupted value such as .../ebuddyError cannot
# override the clean JDBC URL through Spring's environment precedence rules.
unset SPRING_DATASOURCE_URL

exec java ${JAVA_OPTS:-} ${JAVA_TOOL_OPTIONS:-} -jar /app/app.jar
