#!/bin/sh

# Minimal Gradle Wrapper launcher for POSIX environments.
# The Gradle Wrapper JAR and distribution are kept under gradle/wrapper.

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

if [ ! -x "$JAVACMD" ]; then
    echo "ERROR: Java executable not found: $JAVACMD" >&2
    exit 1
fi

exec "$JAVACMD" -jar "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" "$@"
