#!/usr/bin/env sh

##############################################################################
## Download gradle-wrapper.jar on demand to avoid committing binaries.     ##
## If the JAR is missing, we pull it from the configured distribution ZIP  ##
## instead of Maven Central (which can serve 404s for this artifact).      ##
##############################################################################

APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
PROPERTIES_FILE="$APP_HOME/gradle/wrapper/gradle-wrapper.properties"

distributionUrl=""
if [ -f "$PROPERTIES_FILE" ]; then
  distributionUrl=$(sed -n 's/^distributionUrl=//p' "$PROPERTIES_FILE" | tail -n 1 | sed 's#\\:#:#g')
fi

wrapper_valid() {
  [ -f "$WRAPPER_JAR" ] || return 1
  if command -v jar >/dev/null 2>&1; then
    jar tf "$WRAPPER_JAR" 2>/dev/null | grep -q "org/gradle/wrapper/IDownload.class" || return 1
    jar tf "$WRAPPER_JAR" 2>/dev/null | grep -q "org/gradle/wrapper/GradleWrapperMain.class" || return 1
  fi
  return 0
}

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

  for pattern in "gradle-*/lib/plugins/gradle-wrapper-*.jar" "gradle-*/lib/gradle-wrapper-*.jar"; do
    unzip -j "$zip_path" "$pattern" -d "$tmp_dir" >/dev/null 2>&1 || continue

    jar_path=$(printf "%s\n" "$tmp_dir"/gradle-wrapper-*.jar | head -n 1)
    if [ -f "$jar_path" ]; then
      mkdir -p "$(dirname "$WRAPPER_JAR")"
      mv "$jar_path" "$WRAPPER_JAR"
      cleanup_tmp_dir
      return 0
    fi
  done

  cleanup_tmp_dir
  return 1
}

generate_wrapper_with_gradle() {
  gradle_cmd=$(command -v gradle || true)
  [ -n "$gradle_cmd" ] || return 1

  wrapper_version="$1"
  if [ -z "$wrapper_version" ]; then
    wrapper_version="$(printf "%s" "$distributionUrl" | sed -n 's#.*gradle-\([0-9][^-/]*\).*#\1#p' | head -n 1)"
  fi

  if [ -z "$wrapper_version" ]; then
    "$gradle_cmd" wrapper >/dev/null 2>&1 || return 1
  else
    "$gradle_cmd" wrapper --gradle-version "$wrapper_version" >/dev/null 2>&1 || return 1
  fi

  [ -f "$WRAPPER_JAR" ] || return 1
  return 0
}

if ! wrapper_valid; then
  echo "gradle-wrapper.jar missing, downloading from Gradle distribution..." >&2
  if [ -n "$distributionUrl" ] && download_wrapper_from_distribution && wrapper_valid; then
    :
  elif generate_wrapper_with_gradle; then
    :
  else
    echo "Failed to provision gradle-wrapper.jar; ensure network access to the Gradle distribution or a local Gradle installation." >&2
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

set -- $DEFAULT_JVM_OPTS -Dorg.gradle.appname="$APP_NAME" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"

exec "$JAVACMD" "$@"
