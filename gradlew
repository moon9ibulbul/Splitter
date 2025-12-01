#!/usr/bin/env sh

##############################################################################
## Download gradle-wrapper.jar on demand to avoid committing binaries.     ##
##############################################################################

APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
WRAPPER_URL="https://repo.maven.apache.org/maven2/org/gradle/gradle-wrapper/8.9/gradle-wrapper-8.9.jar"

if [ ! -f "$WRAPPER_JAR" ]; then
  echo "gradle-wrapper.jar missing, downloading..." >&2
  mkdir -p "$(dirname "$WRAPPER_JAR")"
  if command -v curl >/dev/null 2>&1; then
    curl -fL "$WRAPPER_URL" -o "$WRAPPER_JAR" || {
      echo "Failed to download gradle-wrapper.jar" >&2
      exit 1
    }
  elif command -v wget >/dev/null 2>&1; then
    wget "$WRAPPER_URL" -O "$WRAPPER_JAR" || {
      echo "Failed to download gradle-wrapper.jar" >&2
      exit 1
    }
  else
    echo "Neither curl nor wget is available to download gradle-wrapper.jar" >&2
    exit 1
  fi
fi

APP_NAME="Gradle"

DEFAULT_JVM_OPTS="-Xmx64m -Xms64m"

if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVACMD" ] ; then
        echo "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME" >&2
        exit 1
    fi
else
    JAVACMD="java"
    which java >/dev/null 2>&1 || {
        echo "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH." >&2
        exit 1
    }
fi

CLASSPATH=$WRAPPER_JAR

save () {
    for i do printf %s\\n "$i" | sed "s/'/'\\''/g;1s/^/'/;\$s/\$/' \\/" ; done
    echo ""
}
APP_ARGS=$(save "$@")

eval set -- $DEFAULT_JVM_OPTS -Dorg.gradle.appname=$APP_NAME -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"

exec "$JAVACMD" "$@"
