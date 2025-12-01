#!/usr/bin/env sh

##############################################################################
## Download gradle-wrapper.jar on demand to avoid committing binaries.     ##
## If the JAR is missing, we pull it from the configured distribution ZIP  ##
## instead of Maven Central (which can serve 404s for this artifact).      ##
##############################################################################

APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
WRAPPER_JAR_B64="$APP_HOME/gradle/wrapper/gradle-wrapper.jar.base64"
PROPERTIES_FILE="$APP_HOME/gradle/wrapper/gradle-wrapper.properties"

distributionUrl=""
if [ -f "$PROPERTIES_FILE" ]; then
  distributionUrl=$(sed -n 's/^distributionUrl=//p' "$PROPERTIES_FILE" | tail -n 1 | sed 's#\\:#:#g')
fi

download_wrapper_from_distribution() {
  tmp_dir=$(mktemp -d 2>/dev/null || mktemp -d -t gradle-wrapper)
  [ -d "$tmp_dir" ] || return 1

  cleanup_tmp_dir() {
    rm -rf "$tmp_dir"
  }

  zip_path="$tmp_dir/gradle-dist.zip"

  if command -v curl >/dev/null 2>&1; then
    curl -fL "$distributionUrl" -o "$zip_path" || { cleanup_tmp_dir; return 1; }
  elif command -v wget >/dev/null 2>&1; then
    wget "$distributionUrl" -O "$zip_path" || { cleanup_tmp_dir; return 1; }
  else
    cleanup_tmp_dir
    return 1
  fi

  if ! command -v unzip >/dev/null 2>&1; then
    cleanup_tmp_dir
    return 1
  fi

  unzip -j "$zip_path" "gradle-*/lib/gradle-wrapper-*.jar" -d "$tmp_dir" >/dev/null 2>&1 || { cleanup_tmp_dir; return 1; }

  jar_path=$(printf "%s\n" "$tmp_dir"/gradle-wrapper-*.jar | head -n 1)
  [ -f "$jar_path" ] || { cleanup_tmp_dir; return 1; }

  mkdir -p "$(dirname "$WRAPPER_JAR")"
  mv "$jar_path" "$WRAPPER_JAR"
  cleanup_tmp_dir
  return 0
}

if [ ! -f "$WRAPPER_JAR" ]; then
  echo "gradle-wrapper.jar missing, restoring from base64 payload..." >&2
  if command -v base64 >/dev/null 2>&1 && [ -f "$WRAPPER_JAR_B64" ]; then
    if base64 -d "$WRAPPER_JAR_B64" > "$WRAPPER_JAR" 2>/dev/null; then
      echo "gradle-wrapper.jar restored from base64." >&2
    else
      echo "Failed to decode gradle-wrapper.jar from base64; trying distribution download." >&2
      rm -f "$WRAPPER_JAR"
    fi
  fi

  if [ ! -f "$WRAPPER_JAR" ]; then
    echo "Extracting gradle-wrapper.jar from distribution..." >&2
    if [ -z "$distributionUrl" ] || ! download_wrapper_from_distribution; then
      echo "Failed to provision gradle-wrapper.jar; check network access to the Gradle distribution." >&2
      exit 1
    fi
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
