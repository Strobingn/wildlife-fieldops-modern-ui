#!/usr/bin/env bash
# Idempotent Cloud Agent / CI bootstrap for Wildlife FieldOps (native Android).
#
# Safe to re-run on a prepared disk: missing pieces are installed, existing
# JDK 17 / Android SDK packages / Gradle caches are reused.
#
# Reads compileSdk / targetSdk from app/build.gradle.kts and installs the
# matching SDK platform plus build-tools (same pins as
# .github/workflows/build-android.yml).
#
# Builds preserve disk, not shell exports. Source .cursor/env.sh in new shells:
#   source .cursor/env.sh
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

CURSOR_DIR="${REPO_ROOT}/.cursor"
ENV_FILE="${CURSOR_DIR}/env.sh"
GRADLE_FILE="${REPO_ROOT}/app/build.gradle.kts"

ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${HOME}/Android/Sdk}"
ANDROID_HOME="${ANDROID_SDK_ROOT}"

CMDLINE_TOOLS_VERSION="${CMDLINE_TOOLS_VERSION:-15859902}"
CMDLINE_TOOLS_URL="${CMDLINE_TOOLS_URL:-https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip}"
CMDLINE_TOOLS_SHA256="${CMDLINE_TOOLS_SHA256:-4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583}"

log() { printf '[install-android-sdk] %s\n' "$*"; }

require_file() {
  if [[ ! -f "$1" ]]; then
    log "ERROR: expected file missing: $1"
    exit 1
  fi
}

read_gradle_int() {
  local key="$1"
  sed -n "s/^[[:space:]]*${key}[[:space:]]*=[[:space:]]*\\([0-9][0-9]*\\).*/\\1/p" "$GRADLE_FILE" | head -n 1
}

find_java17_home() {
  local candidate
  for candidate in \
    "${JAVA_HOME:-}" \
    /usr/lib/jvm/java-17-openjdk-amd64 \
    /usr/lib/jvm/java-17-openjdk \
    /usr/lib/jvm/temurin-17-jdk-amd64 \
    /usr/lib/jvm/temurin-17 \
    /opt/java/openjdk
  do
    if [[ -n "$candidate" && -x "${candidate}/bin/javac" ]]; then
      if "${candidate}/bin/javac" -version 2>&1 | grep -Eq 'javac 17\.'; then
        printf '%s\n' "$candidate"
        return 0
      fi
    fi
  done
  return 1
}

install_openjdk_17() {
  local java_home
  if java_home="$(find_java17_home)"; then
    log "OpenJDK 17 already present at ${java_home}"
    JAVA_17_HOME="$java_home"
    return 0
  fi

  log "Installing OpenJDK 17 via apt"
  sudo apt-get update -y
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    openjdk-17-jdk-headless \
    ca-certificates \
    unzip \
    curl

  JAVA_17_HOME="$(find_java17_home)" || {
    log "ERROR: OpenJDK 17 install finished but javac 17 was not found"
    exit 1
  }
  log "OpenJDK 17 installed at ${JAVA_17_HOME}"
}

ensure_cmdline_tools() {
  local sdkmanager="${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager"
  if [[ -x "$sdkmanager" ]]; then
    log "Android command-line tools already present"
    return 0
  fi

  log "Downloading Android command-line tools ${CMDLINE_TOOLS_VERSION}"
  mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools"
  local tmp_dir zip_path
  tmp_dir="$(mktemp -d)"
  zip_path="${tmp_dir}/commandlinetools.zip"
  curl --retry 3 --retry-delay 5 -fsSL "$CMDLINE_TOOLS_URL" -o "$zip_path"
  echo "${CMDLINE_TOOLS_SHA256}  ${zip_path}" | sha256sum -c -
  unzip -q "$zip_path" -d "$tmp_dir"
  rm -rf "${ANDROID_SDK_ROOT}/cmdline-tools/latest"
  mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools/latest"
  # Zip root is cmdline-tools/{bin,lib,...}; sdkmanager expects cmdline-tools/latest/...
  mv "${tmp_dir}/cmdline-tools/"* "${ANDROID_SDK_ROOT}/cmdline-tools/latest/"
  rm -rf "$tmp_dir"
}

accept_sdk_licenses() {
  mkdir -p "${ANDROID_SDK_ROOT}/licenses"
  # Standard CI license hashes so sdkmanager is non-interactive.
  printf '24333f8a63b6825ea9c5514f83c2829b004d1fee\n' > "${ANDROID_SDK_ROOT}/licenses/android-sdk-license"
  printf '84831b940964616600abbc39c8ed08d562425809\n' > "${ANDROID_SDK_ROOT}/licenses/android-sdk-preview-license"
  printf 'd56f59cc819d9e5d87753d9679e925d86d65803c\n' > "${ANDROID_SDK_ROOT}/licenses/android-sdk-arm-dbt-license"

  local sdkmanager="${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager"
  # sdkmanager --licenses exits non-zero if `yes` closes the pipe; that is expected.
  set +o pipefail
  yes | "$sdkmanager" --sdk_root="${ANDROID_SDK_ROOT}" --licenses >/dev/null
  set -o pipefail
  log "Android SDK licenses accepted"
}

sdk_package_installed() {
  local marker="$1"
  [[ -e "$marker" ]]
}

install_sdk_packages() {
  local compile_sdk="$1"
  local build_tools="$2"
  local sdkmanager="${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager"
  local packages=()

  if ! sdk_package_installed "${ANDROID_SDK_ROOT}/platforms/android-${compile_sdk}/android.jar"; then
    packages+=("platforms;android-${compile_sdk}")
  fi
  if ! sdk_package_installed "${ANDROID_SDK_ROOT}/build-tools/${build_tools}/aapt"; then
    packages+=("build-tools;${build_tools}")
  fi
  if ! sdk_package_installed "${ANDROID_SDK_ROOT}/platform-tools/adb"; then
    packages+=("platform-tools")
  fi
  if ! sdk_package_installed "${ANDROID_SDK_ROOT}/extras/google/m2repository/source.properties"; then
    packages+=("extras;google;m2repository")
  fi

  if [[ ${#packages[@]} -eq 0 ]]; then
    log "Android SDK platform ${compile_sdk} + build-tools ${build_tools} already installed"
    return 0
  fi

  log "Installing Android SDK packages: ${packages[*]}"
  "$sdkmanager" --sdk_root="${ANDROID_SDK_ROOT}" --install "${packages[@]}"
}

write_env_file() {
  mkdir -p "$CURSOR_DIR"
  cat > "$ENV_FILE" <<EOF
# Generated by .cursor/install-android-sdk.sh — source this in Cloud Agent shells.
# Builds preserve disk, not exported environment variables.
export JAVA_HOME="${JAVA_17_HOME}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT}"
export ANDROID_HOME="${ANDROID_SDK_ROOT}"
export PATH="\${JAVA_HOME}/bin:\${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:\${ANDROID_SDK_ROOT}/platform-tools:\${ANDROID_SDK_ROOT}/build-tools/${BUILD_TOOLS}:\${PATH}"
EOF
  log "Wrote durable env exports to ${ENV_FILE}"
}

write_local_properties() {
  local props="${REPO_ROOT}/local.properties"
  local escaped="${ANDROID_SDK_ROOT//\\/\\\\}"
  if [[ -f "$props" ]] && grep -q '^sdk\.dir=' "$props"; then
    sed -i "s|^sdk\\.dir=.*|sdk.dir=${escaped}|" "$props"
  elif [[ -f "$props" ]]; then
    printf '\nsdk.dir=%s\n' "$escaped" >> "$props"
  else
    printf 'sdk.dir=%s\n' "$escaped" > "$props"
  fi
  log "Pointed local.properties sdk.dir at ${ANDROID_SDK_ROOT} (gitignored)"
}

warm_gradle_cache() {
  chmod +x "${REPO_ROOT}/gradlew"
  export JAVA_HOME="${JAVA_17_HOME}"
  export ANDROID_SDK_ROOT
  export ANDROID_HOME="${ANDROID_SDK_ROOT}"
  export PATH="${JAVA_HOME}/bin:${PATH}"

  # Lightweight compile: resolves Gradle + Android deps and compiles debug
  # Kotlin without packaging an APK or requiring Maps/API secrets.
  log "Warming Gradle cache (:app:compileDebugKotlin :observation-core:compileKotlin)"
  ./gradlew --no-daemon \
    :app:compileDebugKotlin \
    :observation-core:compileKotlin
  log "Gradle compile check succeeded"
}

require_file "$GRADLE_FILE"

COMPILE_SDK="$(read_gradle_int compileSdk)"
TARGET_SDK="$(read_gradle_int targetSdk)"
if [[ -z "$COMPILE_SDK" || -z "$TARGET_SDK" ]]; then
  log "ERROR: could not read compileSdk/targetSdk from ${GRADLE_FILE}"
  exit 1
fi
# Match CI (.github/workflows/build-android.yml): build-tools;<compileSdk>.0.0
BUILD_TOOLS="${ANDROID_BUILD_TOOLS_VERSION:-${COMPILE_SDK}.0.0}"
log "Project SDK levels: compileSdk=${COMPILE_SDK} targetSdk=${TARGET_SDK} build-tools=${BUILD_TOOLS}"

install_openjdk_17
export JAVA_HOME="${JAVA_17_HOME}"
ensure_cmdline_tools
accept_sdk_licenses
install_sdk_packages "$COMPILE_SDK" "$BUILD_TOOLS"
write_env_file
write_local_properties
warm_gradle_cache

log "Install complete. New shells should run: source ${ENV_FILE}"
