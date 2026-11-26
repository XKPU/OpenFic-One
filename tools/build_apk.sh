#!/usr/bin/env bash
# 编译并签名 OF-One 的 APK。工具路径全部取自环境变量或 local.properties，脚本内不含绝对路径。
set -e
set -o pipefail

BASE="$(cd "$(dirname "$0")/.." && pwd)"
BASE="$(cygpath -m "$BASE" 2>/dev/null || printf '%s' "$BASE")"
APP="$BASE/app"
OUT="$BASE/tmp/build"
PROPS="$BASE/local.properties"

die() { echo "错误：$*" >&2; exit 1; }

prop() {
  [ -f "$PROPS" ] || return 0
  sed -n "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*//p" "$PROPS" | tail -n 1 | tr -d '\r'
}

abspath() {
  case "$1" in
    /* | [A-Za-z]:[/\\]*) printf '%s' "$1" ;;
    *) printf '%s/%s' "$BASE" "$1" ;;
  esac
}

pick() {
  if [ -x "$1.exe" ]; then printf '%s.exe' "$1"
  elif [ -x "$1" ]; then printf '%s' "$1"
  else return 1
  fi
}

SDK="$ANDROID_SDK_ROOT"
[ -n "$SDK" ] || SDK="$ANDROID_SDK"
[ -n "$SDK" ] || SDK="$ANDROID_HOME"
[ -n "$SDK" ] || SDK="$(prop sdk.dir)"
[ -n "$SDK" ] || die "未找到 Android SDK：请设置 ANDROID_SDK_ROOT，或在 local.properties 写 sdk.dir=..."

ver_gt() {
  local IFS=.
  local -a a=($1) b=($2)
  local i x y
  for ((i = 0; i < ${#a[@]} || i < ${#b[@]}; i++)); do
    x=${a[i]:-0}; y=${b[i]:-0}
    x=${x%%[!0-9]*}; y=${y%%[!0-9]*}
    x=${x:-0}; y=${y:-0}
    if ((10#$x > 10#$y)); then return 0; fi
    if ((10#$x < 10#$y)); then return 1; fi
  done
  [ "${#1}" -gt "${#2}" ]
}

BT_VER="$BUILD_TOOLS"
[ -n "$BT_VER" ] || BT_VER="$(prop build.tools)"
BT=""
if [ -n "$BT_VER" ]; then
  BT="$SDK/build-tools/$BT_VER"
else
  for d in "$SDK"/build-tools/*; do
    [ -d "$d" ] || continue
    if [ -z "$BT" ] || ver_gt "${d##*/}" "${BT##*/}"; then BT="$d"; fi
  done
fi
[ -n "$BT" ] || die "未找到 build-tools，请先安装"
AAPT="$(pick "$BT/aapt")" || die "找不到 aapt：$BT"
ZIPALIGN="$(pick "$BT/zipalign")" || die "找不到 zipalign：$BT"
[ -f "$BT/lib/apksigner.jar" ] || die "找不到 apksigner.jar：$BT/lib"

PLATFORM="$COMPILE_SDK"
[ -n "$PLATFORM" ] || PLATFORM="$(prop compile.sdk)"
JAR=""
if [ -n "$PLATFORM" ]; then
  JAR="$SDK/platforms/android-$PLATFORM/android.jar"
else
  best=""
  for d in "$SDK"/platforms/android-*; do
    [ -f "$d/android.jar" ] || continue
    v="${d##*/}"
    v="${v#android-}"
    if [ -z "$best" ] || ver_gt "$v" "$best"; then best="$v"; fi
  done
  if [ -n "$best" ]; then JAR="$SDK/platforms/android-$best/android.jar"; fi
fi
[ -f "$JAR" ] || die "找不到 android.jar：请安装对应 platform 或设置 COMPILE_SDK"

JDK_HOME="$JAVA_HOME"
[ -n "$JDK_HOME" ] || JDK_HOME="$(prop java.home)"
if [ -n "$JDK_HOME" ]; then
  JBIN="$JDK_HOME/bin"
elif command -v javac >/dev/null 2>&1; then
  JBIN="$(dirname "$(command -v javac)")"
else
  die "未找到 JDK：请设置 JAVA_HOME，或在 local.properties 写 java.home=..."
fi
JAVAC="$(pick "$JBIN/javac")" || die "找不到 javac：$JBIN"
JAVA="$(pick "$JBIN/java")" || die "找不到 java：$JBIN"

PY="$PYTHON"
[ -n "$PY" ] || PY="$(prop python)"
[ -n "$PY" ] || PY=python

KS="$OFONE_KEYSTORE"
[ -n "$KS" ] || KS="$(prop keystore)"
[ -n "$KS" ] || KS="$APP/keystore/debug.keystore"
KS="$(abspath "$KS")"
ALIAS="$OFONE_KEY_ALIAS"
[ -n "$ALIAS" ] || ALIAS="$(prop keystore.alias)"
[ -n "$ALIAS" ] || ALIAS=androiddebugkey
SPASS="$OFONE_STOREPASS"
[ -n "$SPASS" ] || SPASS="$(prop keystore.storepass)"
[ -n "$SPASS" ] || SPASS=android
KPASS="$OFONE_KEYPASS"
[ -n "$KPASS" ] || KPASS="$(prop keystore.keypass)"
[ -n "$KPASS" ] || KPASS="$SPASS"
[ -f "$KS" ] || die "找不到签名密钥库：$KS（见 docs/SIGNING.md）"

rm -rf "$OUT"; mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/gen"

echo "[1/6] aapt package"
"$AAPT" package -f -m \
  -M "$APP/AndroidManifest.xml" \
  -S "$APP/res" \
  -J "$OUT/gen" \
  -A "$APP/assets" \
  -I "$JAR" \
  -0 so \
  -F "$OUT/base.apk"

echo "[2/6] javac"
"$PY" "$BASE/tools/gen_sources.py" "$APP/src" "$OUT/gen" "$OUT/sources.txt"
"$JAVAC" -source 8 -target 8 -encoding UTF-8 -nowarn \
  -bootclasspath "$JAR" -classpath "$JAR" \
  -d "$OUT/classes" "@$OUT/sources.txt"

echo "[3/6] d8"
find "$OUT/classes" -name "*.class" -print0 | xargs -0 -n 200 "$JAVA" -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --min-api 24 --lib "$JAR" --output "$OUT/dex"

echo "[4/6] add dex"
cd "$OUT/dex" && "$AAPT" add "$OUT/base.apk" classes.dex
cd "$BASE"

echo "[5/6] zipalign"
"$ZIPALIGN" -f 4 "$OUT/base.apk" "$OUT/aligned.apk"

echo "[6/6] sign"
mkdir -p "$BASE/out"
"$JAVA" -jar "$BT/lib/apksigner.jar" sign \
  --ks "$KS" --ks-pass "pass:$SPASS" \
  --ks-key-alias "$ALIAS" --key-pass "pass:$KPASS" \
  --out "$BASE/out/OpenFicOne.apk" "$OUT/aligned.apk"

ls -la "$BASE/out/OpenFicOne.apk"
