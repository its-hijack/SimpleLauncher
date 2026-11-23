#!/bin/bash
# =====================================================================================
#  SimpleScript, by Cherry Universe
#  Sets up SimpleLauncher on your TV, step by step: gets the tools your computer needs,
#  connects to your TV, then installs SimpleLauncher. Supported devices get extras.
#
#  Run it from Terminal:   bash SimpleScript.sh
#  Works on macOS and Linux.
# =====================================================================================

if [ -z "$BASH_VERSION" ]; then
  echo "Please run SimpleScript with bash:  bash SimpleScript.sh"
  exit 1
fi

SCRIPT_VERSION="1.0"
REPO="its-hijack/SimpleLauncher"
REPO_URL="https://github.com/$REPO"
SL_PKG="com.simplehome.tv"
SL_SERVICE="com.simplehome.tv/com.simplehome.tv.HomeButtonService"
DATA_DIR="$HOME/.simplescript"

# -------------------------------------------------------------------------------------
#  Supported devices
#
#  Only devices listed here are offered Debloat, Restore and HomeCatch, because each one
#  has been tested. Anything else can still install SimpleLauncher as a normal app.
#
#  model code | name | Fire OS version tested | debloat | HomeCatch
#  (The model code is what `adb shell getprop ro.product.model` prints.)
# -------------------------------------------------------------------------------------
SUPPORTED_DEVICES=(
  "AFTSS|Fire TV Stick HD|7|yes|yes"
)

# -------------------------------------------------------------------------------------
#  Devices being tested
#
#  HomeCatch is offered on these while it's tested, but nothing else: no extras, and
#  they aren't called supported. Move a line up to SUPPORTED_DEVICES once it's confirmed.
#
#  model code | name | Fire OS version (* = any version)
# -------------------------------------------------------------------------------------
TESTING_DEVICES=(
  "AFTSS|Fire TV Stick HD|8"
  "AFTARK|Fire TV (model AFTARK)|*"
)

# Amazon's automatic Fire OS updater. Switching it off stops updates installing by themselves
# (they can undo changes and break HomeCatch). Offered when installing with HomeCatch.
UPDATER_PKG="com.amazon.tv.forcedotaupdater.v2"

# -------------------------------------------------------------------------------------
#  Debloat list
#
#  group | package | what it is | recommended (y = switch off) | how (disable or uninstall)
#
#  Add a line here and everyone is asked about it the next time they debloat, marked NEW.
#  Only things actually on the TV are asked about. Nothing is deleted except "uninstall"
#  lines (helper apps you installed yourself); everything else can be switched back on.
# -------------------------------------------------------------------------------------
DEBLOAT_GROUPS=(
  "ads|Adverts and tracking|Sponsored rows, autoplaying previews and the data Amazon collects about what you watch."
  "nags|Pop-ups and help apps|Tutorials, \"what's new\" screens and support apps you rarely need."
  "apps|Amazon apps|Amazon's own apps. Keep any you use."
  "alexa|Alexa and smart home|Alexa alerts, Echo multi-room audio and smart-home controls. Keep these if you use Alexa with other devices."
  "updates|Forced updates|Amazon updates can undo your changes and break HomeCatch."
  "helpers|Helper apps|Apps you may have installed while setting things up."
)
DEBLOAT_LIST=(
  "ads|com.amazon.kso.blackbird|Sponsored content on the home screen|y|disable"
  "ads|com.amazon.sneakpeek|Autoplaying video previews|y|disable"
  "ads|com.amazon.hybridadidservice|Advertising ID|y|disable"
  "ads|com.amazon.tv.acr|Tracking what's on your screen|y|disable"
  "ads|com.amazon.media.recommendations|\"Recommended for you\" rows|y|disable"
  "ads|com.amazon.shoptv.client|Shopping|y|disable"
  "ads|com.amazon.shoptv.firetv.client|Shopping (Fire TV)|y|disable"
  "ads|com.amazon.tv.fw.metrics|Usage metrics|y|disable"
  "ads|com.amazon.wirelessmetrics.service|Wi-Fi metrics|y|disable"
  "ads|com.amazon.neo.minerva|Telemetry|y|disable"
  "ads|com.amazon.neo.minerva.logcat|Telemetry logs|y|disable"
  "ads|com.amazon.perfc|Performance data|y|disable"
  "ads|com.amazon.perfcollection|Performance data collection|y|disable"
  "ads|com.amazon.dp.logger|Logging|y|disable"
  "ads|com.amazon.logan|Logging|y|disable"
  "ads|com.amazon.wifilocker|Saving your Wi-Fi passwords to Amazon|y|disable"
  "nags|com.amazon.storm.lightning.tutorial|Tutorials|y|disable"
  "nags|com.amazon.tmm.tutorial|Tutorials|y|disable"
  "nags|com.amazon.tv.releasenotes|\"What's new\" pop-ups|y|disable"
  "nags|com.amazon.tv.support|Help app|y|disable"
  "nags|com.amazon.firetv.troubleshooting|Troubleshooting app|y|disable"
  "nags|com.amazon.tv.website_launcher|Opens Amazon web pages|y|disable"
  "nags|com.amazon.connectivitydiag|Connection diagnostics|y|disable"
  "nags|com.amazon.ods.kindleconnect|Kindle connect|y|disable"
  "nags|com.amazon.stillwatching.activity|\"Are you still watching?\"|y|disable"
  "apps|com.amazon.firebat|Prime Video|n|disable"
  "apps|com.amazon.bueller.music|Amazon Music|n|disable"
  "apps|com.amazon.bueller.photos|Amazon Photos|n|disable"
  "apps|com.amazon.cloud9|Silk browser|n|disable"
  "apps|com.amazon.minitv.android.app|miniTV|n|disable"
  "apps|com.amazon.gamehub|Games hub|n|disable"
  "apps|com.amazon.tv.livetv|Live TV|n|disable"
  "apps|com.amazon.tahoe|Amazon Kids|n|disable"
  "apps|com.amazon.ftv.screensaver|Screensaver|n|disable"
  "alexa|com.amazon.tv.alexaalerts|Alexa alarms, timers and reminders|n|disable"
  "alexa|com.amazon.tv.alexanotifications|Alexa notifications|n|disable"
  "alexa|com.amazon.whad|Echo multi-room audio|n|disable"
  "alexa|com.amazon.wha.mediabrowserservice|Echo multi-room audio (media)|n|disable"
  "alexa|com.amazon.whasettings|Echo multi-room audio (settings)|n|disable"
  "alexa|com.amazon.spotify.mediabrowserservice|Spotify through Alexa|n|disable"
  "alexa|com.amazon.tv.matter|Smart-home control|n|disable"
  "alexa|com.amazon.tv.mattercohost|Smart-home control (hub)|n|disable"
  "updates|com.amazon.tv.forcedotaupdater.v2|Forced Fire OS updates|y|disable"
  "helpers|com.wolf.minilm|Launcher Manager Mini (not needed with SimpleLauncher)|n|uninstall"
  "helpers|com.esaba.downloader|Downloader|n|uninstall"
)

# =====================================================================================
#  Looks
# =====================================================================================
if [ -t 1 ]; then
  B=$'\033[1m'; D=$'\033[2m'; R=$'\033[0m'
  GREEN=$'\033[32m'; RED=$'\033[31m'; YELLOW=$'\033[33m'; BLUE=$'\033[36m'
else
  B=""; D=""; R=""; GREEN=""; RED=""; YELLOW=""; BLUE=""
fi

say()     { printf '%s\n' "$*"; }
blank()   { printf '\n'; }
ok()      { printf '  %s✓%s %s\n' "$GREEN" "$R" "$*"; }
bad()     { printf '  %s✗%s %s\n' "$RED" "$R" "$*"; }
note()    { printf '  %s•%s %s\n' "$BLUE" "$R" "$*"; }
warn()    { printf '  %s!%s %s\n' "$YELLOW" "$R" "$*"; }
dim()     { printf '%s%s%s\n' "$D" "$*" "$R"; }
heading() { blank; printf '%s%s%s\n' "$B" "$*" "$R"; printf '%s\n' "$(printf '%*s' "${#1}" '' | tr ' ' '-')"; }
step()    { blank; printf '%s%sStep %s of 4 · %s%s\n' "$B" "$BLUE" "$1" "$2" "$R"; }

# ask_yn "Question" y|n  → returns 0 for yes. Enter takes the default (shown in capitals).
ask_yn() {
  local q="$1" def="${2:-y}" hint ans
  if [ "$def" = "y" ]; then hint="Y/n"; else hint="y/N"; fi
  while true; do
    read -r -p "  $q [$hint] " ans
    ans=$(printf '%s' "$ans" | tr '[:upper:]' '[:lower:]')
    [ -z "$ans" ] && ans="$def"
    case "$ans" in
      y|yes) return 0 ;;
      n|no) return 1 ;;
      *) say "  Please type y or n." ;;
    esac
  done
}

pause() { read -r -p "  ${1:-Press Return to carry on.} " _; }

# choose "Prompt" count → sets CHOICE to 1..count, or q
choose() {
  local prompt="$1" max="$2" ans
  while true; do
    read -r -p "  $prompt " ans
    ans=$(printf '%s' "$ans" | tr '[:upper:]' '[:lower:]')
    if [ "$ans" = "q" ]; then CHOICE=q; return; fi
    if printf '%s' "$ans" | grep -qE '^[0-9]+$' && [ "$ans" -ge 1 ] && [ "$ans" -le "$max" ]; then
      CHOICE="$ans"; return
    fi
    say "  Please type a number from 1 to $max, or q to quit."
  done
}

trap 'blank; say "Stopped. Anything not finished was left as it was."; exit 130' INT

# =====================================================================================
#  Step 1: the tools this computer needs
# =====================================================================================
OS="$(uname -s)"
ADB=""

find_adb() {
  if [ -n "$SIMPLESCRIPT_ADB" ] && [ -x "$SIMPLESCRIPT_ADB" ]; then ADB="$SIMPLESCRIPT_ADB"; return 0; fi
  if command -v adb >/dev/null 2>&1; then ADB="$(command -v adb)"; return 0; fi
  local p
  for p in "$DATA_DIR/platform-tools/adb" "$HOME/Library/Android/sdk/platform-tools/adb" "$HOME/Android/Sdk/platform-tools/adb"; do
    if [ -x "$p" ]; then ADB="$p"; return 0; fi
  done
  return 1
}

need() { command -v "$1" >/dev/null 2>&1; }

install_adb_zip() {
  local zip
  case "$OS" in
    Darwin) zip="platform-tools-latest-darwin.zip" ;;
    *) zip="platform-tools-latest-linux.zip" ;;
  esac
  if ! need curl || ! need unzip; then
    bad "This needs curl and unzip, which aren't on this computer."
    return 1
  fi
  note "Downloading Google's platform tools (about 15 MB)…"
  mkdir -p "$DATA_DIR"
  local tmp="$DATA_DIR/platform-tools.zip"
  if ! curl -fL --progress-bar -o "$tmp" "https://dl.google.com/android/repository/$zip"; then
    bad "The download didn't work. Check this computer is online."
    rm -f "$tmp"
    return 1
  fi
  rm -rf "$DATA_DIR/platform-tools"
  if ! unzip -q "$tmp" -d "$DATA_DIR"; then
    bad "Couldn't unpack the download."
    rm -f "$tmp"
    return 1
  fi
  rm -f "$tmp"
  ADB="$DATA_DIR/platform-tools/adb"
  [ -x "$ADB" ]
}

install_adb() {
  blank
  say "  SimpleScript talks to your TV with ${B}ADB${R}, a free tool from Google."
  say "  It isn't on this computer yet. How would you like to get it?"
  blank
  local options=() n=0
  if [ "$OS" = "Darwin" ] && need brew; then
    n=$((n+1)); options+=("brew"); say "  $n) Install it for me with Homebrew        ${D}(recommended)${R}"
  fi
  if [ "$OS" = "Linux" ]; then
    if need apt-get; then n=$((n+1)); options+=("apt"); say "  $n) Install it for me with apt              ${D}(asks for your password)${R}"; fi
    if need dnf; then n=$((n+1)); options+=("dnf"); say "  $n) Install it for me with dnf              ${D}(asks for your password)${R}"; fi
    if need pacman; then n=$((n+1)); options+=("pacman"); say "  $n) Install it for me with pacman           ${D}(asks for your password)${R}"; fi
  fi
  n=$((n+1)); options+=("zip");    say "  $n) Download it from Google for me         ${D}(kept in ~/.simplescript, nothing else changed)${R}"
  n=$((n+1)); options+=("manual"); say "  $n) I'll install it myself"
  blank
  choose "Choose 1-$n (or q to quit):" "$n"
  [ "$CHOICE" = "q" ] && { say "  No problem. Run SimpleScript again whenever you're ready."; exit 0; }
  case "${options[$((CHOICE-1))]}" in
    brew)
      note "Running: brew install --cask android-platform-tools"
      brew install --cask android-platform-tools || bad "Homebrew couldn't install it."
      ;;
    apt)    note "Running: sudo apt-get install -y adb"; sudo apt-get install -y adb || bad "apt couldn't install it." ;;
    dnf)    note "Running: sudo dnf install -y android-tools"; sudo dnf install -y android-tools || bad "dnf couldn't install it." ;;
    pacman) note "Running: sudo pacman -S --noconfirm android-tools"; sudo pacman -S --noconfirm android-tools || bad "pacman couldn't install it." ;;
    zip)
      say "  By downloading, you accept Google's terms for the Android SDK:"
      say "  ${D}https://developer.android.com/studio/terms${R}"
      ask_yn "Download it now?" y || { say "  OK, nothing downloaded."; exit 0; }
      install_adb_zip || true
      ;;
    manual)
      blank
      say "  1. Download ${B}SDK Platform-Tools${R} for your computer from:"
      say "     ${D}https://developer.android.com/tools/releases/platform-tools${R}"
      say "  2. Unzip it, and either add the folder to your PATH or move it to:"
      say "     ${D}$DATA_DIR/platform-tools${R}"
      pause "Press Return when that's done."
      ;;
  esac
  hash -r 2>/dev/null
  find_adb
}

check_tools() {
  step 1 "Checking this computer"
  case "$OS" in
    Darwin) ok "macOS" ;;
    Linux)  ok "Linux" ;;
    *) warn "SimpleScript is made for macOS and Linux. It may not work properly on $OS." ;;
  esac

  until find_adb; do
    bad "ADB isn't installed"
    install_adb && break
    blank
    ask_yn "ADB still isn't available. Try again?" y || { say "  Run SimpleScript again once ADB is installed."; exit 1; }
  done
  ok "ADB ready ${D}($("$ADB" version 2>/dev/null | head -1 | sed 's/Android Debug Bridge //'))${R}"

  if need curl; then
    ok "curl ready ${D}(used to download SimpleLauncher from GitHub)${R}"
  else
    warn "curl isn't installed, so SimpleLauncher can't be downloaded from GitHub. You can still install an APK file."
  fi
  "$ADB" start-server >/dev/null 2>&1
}

# =====================================================================================
#  Step 2 and 3: switching on ADB debugging, and connecting
# =====================================================================================
SERIAL=""
DEVICE_KIND=""   # fire or android

# adb never gets the keyboard (</dev/null), so it can't swallow your answers.
A() { "$ADB" -s "$SERIAL" "$@" </dev/null; }
S() { "$ADB" -s "$SERIAL" shell "$@" </dev/null 2>/dev/null | tr -d '\r'; }

state_of() { "$ADB" -s "$1" get-state </dev/null 2>/dev/null | tr -d '\r'; }

connected_devices() { "$ADB" devices </dev/null 2>/dev/null | tr -d '\r' | awk 'NR>1 && $2=="device" {print $1}'; }

guide_fire() {
  blank
  say "  On your ${B}Fire TV${R}, using the remote:"
  blank
  say "  1. Go to ${B}Settings › My Fire TV › About${R}."
  say "  2. Select your device's name (the first line, e.g. \"Fire TV Stick\") ${B}7 times${R}"
  say "     until it says you're now a developer. ${D}(Skip this if Developer options is already there.)${R}"
  say "  3. Press Back, open ${B}Developer options${R} and switch ${B}ADB debugging${R} to ${B}On${R}."
  say "  4. Go to ${B}Settings › My Fire TV › About › Network${R} and note the ${B}IP address${R}."
  dim "     It looks like 192.168.1.23"
}

guide_android() {
  blank
  say "  On your ${B}Android TV, Google TV or Android device${R}:"
  blank
  say "  1. Go to ${B}Settings › System › About${R} (on phones: ${B}Settings › About phone${R})."
  say "  2. Select ${B}Build${R} (or ${B}Build number${R}) ${B}7 times${R} until it says you're now a developer."
  say "  3. Open ${B}Settings › System › Developer options${R} and switch on ${B}USB debugging${R}."
  say "     ${D}Some TVs also have \"Network debugging\" or \"ADB over network\": switch that on too.${R}"
  say "  4. Find its ${B}IP address${R} under ${B}Settings › Network & Internet${R} (your Wi-Fi network)."
  say "     ${D}Phone or tablet? You can plug it in with a USB cable instead.${R}"
}

wait_for_approval() {
  local serial="$1" t=0 st
  st=$(state_of "$serial")
  [ "$st" = "device" ] && return 0
  if [ "$st" = "unauthorized" ] || [ "$st" = "authorizing" ]; then
    blank
    say "  ${B}Look at your TV:${R} it's asking ${B}\"Allow USB debugging?\"${R}"
    say "  Tick ${B}Always allow from this computer${R}, then select ${B}OK${R}."
  fi
  printf '  Waiting'
  while [ $t -lt 60 ]; do
    st=$(state_of "$serial")
    if [ "$st" = "device" ]; then printf '\n'; return 0; fi
    printf '.'
    sleep 2
    t=$((t+2))
  done
  printf '\n'
  return 1
}

connect_by_ip() {
  local ip out
  while true; do
    blank
    read -r -p "  Type your TV's IP address (or q to quit): " ip
    ip=$(printf '%s' "$ip" | tr -d ' ')
    [ "$ip" = "q" ] && exit 0
    if ! printf '%s' "$ip" | grep -qE '^[0-9]{1,3}(\.[0-9]{1,3}){3}(:[0-9]+)?$'; then
      say "  That doesn't look like an IP address. It's four numbers with dots, e.g. 192.168.1.23"
      continue
    fi
    case "$ip" in *:*) ;; *) ip="$ip:5555" ;; esac
    note "Connecting to $ip…"
    out=$("$ADB" connect "$ip" </dev/null 2>&1 | tr -d '\r')
    if printf '%s' "$out" | grep -qiE 'connected to|already connected'; then
      if wait_for_approval "$ip"; then
        SERIAL="$ip"
        ok "Connected"
        return 0
      fi
      bad "The TV didn't allow the connection."
      say "  If you missed the message, it may still be on screen. If you selected Cancel,"
      say "  unplug the TV for a moment, or switch ADB debugging off and on, then try again."
      "$ADB" disconnect "$ip" >/dev/null 2>&1
    else
      bad "Couldn't reach the TV at $ip."
      if printf '%s' "$out" | grep -qi 'refused'; then
        say "  The TV answered but refused. Check ${B}ADB debugging${R} is switched ${B}On${R}."
      else
        say "  Check the IP address, that the TV is switched on, and that this computer is on the same"
        say "  Wi-Fi or network as the TV."
      fi
    fi
    ask_yn "Try again?" y || exit 0
  done
}

prepare_and_connect() {
  step 2 "Getting your TV ready"
  local existing count
  existing=$(connected_devices)
  count=$(printf '%s' "$existing" | grep -c . )
  if [ "$count" -ge 1 ]; then
    say "  Already connected:"
    local i=0 s
    for s in $existing; do
      i=$((i+1))
      say "  $i) $(device_label "$s")"
    done
    say "  $((i+1))) Connect to a different device"
    blank
    choose "Which one? (1-$((i+1)), q to quit)" $((i+1))
    [ "$CHOICE" = "q" ] && exit 0
    if [ "$CHOICE" -le "$i" ]; then
      SERIAL=$(printf '%s\n' $existing | sed -n "${CHOICE}p")
      step 3 "Connecting"
      ok "Using $(device_label "$SERIAL")"
      return
    fi
  fi

  say "  What are you setting up?"
  say "  1) An Amazon Fire TV ${D}(Fire TV Stick, Cube, Fire TV smart TV)${R}"
  say "  2) Another Android TV, Google TV, phone or tablet"
  blank
  choose "Choose 1 or 2:" 2
  [ "$CHOICE" = "q" ] && exit 0
  if [ "$CHOICE" = "1" ]; then guide_fire; else guide_android; fi
  blank
  pause "Press Return when ADB debugging is on and you have the IP address."

  step 3 "Connecting"
  if [ "$CHOICE" = "2" ]; then
    existing=$(connected_devices)
    if [ -n "$existing" ]; then
      SERIAL=$(printf '%s\n' $existing | head -1)
      ok "Found $(device_label "$SERIAL") over USB"
      return
    fi
    local usb
    usb=$("$ADB" devices | tr -d '\r' | awk 'NR>1 && $2=="unauthorized" {print $1}' | head -1)
    if [ -n "$usb" ] && wait_for_approval "$usb"; then SERIAL="$usb"; ok "Connected over USB"; return; fi
  fi
  connect_by_ip
}

device_label() {
  local m mf
  m=$("$ADB" -s "$1" shell getprop ro.product.model </dev/null 2>/dev/null | tr -d '\r')
  mf=$("$ADB" -s "$1" shell getprop ro.product.manufacturer </dev/null 2>/dev/null | tr -d '\r')
  printf '%s %s %s(%s)%s' "$mf" "$m" "$D" "$1" "$R"
}

# =====================================================================================
#  Step 4: what is it, and what's it allowed
# =====================================================================================
MODEL=""; MAKER=""; DEVICE_NAME=""; ANDROID=""; SDK=0; FIRE_OS=""; FIRE_MAJOR=0
SUPPORT="none"        # full, testing, app, generic
SUPPORTED_NAME=""; CAN_DEBLOAT=no; CAN_HOMECATCH=no; SUPPORT_REASON=""

fire_major_from_sdk() {
  case "$1" in
    22) echo 5 ;; 25) echo 6 ;; 28) echo 7 ;; 30) echo 8 ;; *) echo 0 ;;
  esac
}

detect_device() {
  step 4 "Checking your device"
  MODEL=$(S getprop ro.product.model)
  MAKER=$(S getprop ro.product.manufacturer)
  ANDROID=$(S getprop ro.build.version.release)
  SDK=$(S getprop ro.build.version.sdk); SDK=${SDK:-0}
  DEVICE_NAME=$(S settings get global device_name); [ "$DEVICE_NAME" = "null" ] && DEVICE_NAME=""

  local fire=no
  if [ "$MAKER" = "Amazon" ] && printf '%s' "$MODEL" | grep -q '^AFT'; then fire=yes; fi
  if S pm list features | grep -q 'amazon.hardware.fire_tv'; then fire=yes; fi

  if [ "$fire" = "yes" ]; then
    FIRE_MAJOR=$(fire_major_from_sdk "$SDK")
    FIRE_OS=$(S getprop ro.build.version.name)
    [ -z "$FIRE_OS" ] && FIRE_OS="Fire OS $FIRE_MAJOR"
    local entry code name osv deb hc
    SUPPORT="app"
    SUPPORT_REASON="This Fire TV hasn't been tested with SimpleScript yet."
    for entry in "${SUPPORTED_DEVICES[@]}"; do
      IFS='|' read -r code name osv deb hc <<< "$entry"
      if [ "$code" = "$MODEL" ]; then
        if [ "$osv" = "$FIRE_MAJOR" ]; then
          SUPPORT="full"; SUPPORTED_NAME="$name"; CAN_DEBLOAT="$deb"; CAN_HOMECATCH="$hc"; SUPPORT_REASON=""
        else
          SUPPORTED_NAME="$name"
          SUPPORT_REASON="The $name has only been tested on Fire OS $osv, and this one runs Fire OS $FIRE_MAJOR."
        fi
      fi
    done
    if [ "$SUPPORT" = "app" ]; then
      for entry in "${TESTING_DEVICES[@]}"; do
        IFS='|' read -r code name osv <<< "$entry"
        if [ "$code" = "$MODEL" ] && { [ "$osv" = "$FIRE_MAJOR" ] || [ "$osv" = "*" ]; }; then
          SUPPORT="testing"; SUPPORTED_NAME="$name"; CAN_HOMECATCH=yes; CAN_DEBLOAT=no; SUPPORT_REASON=""
        fi
      done
    fi
  else
    SUPPORT="generic"
  fi

  blank
  if [ "$SUPPORT" = "full" ] || [ "$SUPPORT" = "testing" ]; then
    say "  ${B}$SUPPORTED_NAME${R}${DEVICE_NAME:+  ·  \"$DEVICE_NAME\"}"
  elif [ "$fire" = "yes" ]; then
    say "  ${B}Amazon Fire TV${R}${DEVICE_NAME:+  ·  \"$DEVICE_NAME\"}"
  else
    say "  ${B}$MAKER $MODEL${R}${DEVICE_NAME:+  ·  \"$DEVICE_NAME\"}"
  fi
  if [ "$fire" = "yes" ]; then
    dim "  Model $MODEL · $FIRE_OS · Android $ANDROID"
  else
    dim "  Model $MODEL · Android $ANDROID"
  fi
  blank
  case "$SUPPORT" in
    full)
      ok "Supported device"
      [ "$CAN_DEBLOAT" = "yes" ] && ok "Extras for this device: Debloat and Restore"
      [ "$CAN_HOMECATCH" = "yes" ] && ok "HomeCatch available ${D}(the Home button opens SimpleLauncher)${R}"
      ;;
    testing)
      warn "HomeCatch is being tested on the $SUPPORTED_NAME with Fire OS $FIRE_MAJOR."
      note "You can install SimpleLauncher with HomeCatch. If anything doesn't work, switch"
      note "HomeCatch off in SimpleLauncher's Settings › Danger Zone and tell us at $REPO_URL/issues"
      ;;
    app)
      warn "$SUPPORT_REASON"
      note "You can install SimpleLauncher as an app. HomeCatch stays off on devices"
      note "that haven't been tested yet, so nothing can go wrong."
      note "Want it supported? Tell us the model ($MODEL) at $REPO_URL/issues"
      ;;
    generic)
      ok "SimpleLauncher can be your home screen on this device"
      ;;
  esac

  if [ "$SDK" -lt 25 ] 2>/dev/null; then
    blank
    bad "SimpleLauncher needs Android 7.1 or newer, and this device runs Android $ANDROID."
    say "  SimpleScript can't set it up, sorry."
    exit 1
  fi
}

# =====================================================================================
#  Helpers on the TV
# =====================================================================================
installed()      { S pm list packages | grep -qx "package:$1"; }
disabled_list()  { S pm list packages -d | sed 's/^package://'; }
all_packages()   { S pm list packages | sed 's/^package://'; }
sl_installed()   { installed "$SL_PKG"; }
sl_version()     { S dumpsys package "$SL_PKG" | grep -m1 versionName | sed 's/.*versionName=//'; }

animations() {
  local k
  for k in window_animation_scale transition_animation_scale animator_duration_scale; do
    S settings put global "$k" "$1" >/dev/null
  done
}

homecatch_on() {
  local current new
  current=$(S settings get secure enabled_accessibility_services)
  if [ -z "$current" ] || [ "$current" = "null" ]; then new="$SL_SERVICE"
  elif printf '%s' "$current" | grep -q "$SL_SERVICE"; then new="$current"
  else new="$current:$SL_SERVICE"; fi
  S "settings put secure enabled_accessibility_services '$new'" >/dev/null
  S settings put secure accessibility_enabled 1 >/dev/null
  S settings get secure enabled_accessibility_services | grep -q "$SL_SERVICE"
}

homecatch_off() {
  local current new
  current=$(S settings get secure enabled_accessibility_services)
  new=$(printf '%s' "$current" | tr ':' '\n' | grep -v "^$SL_SERVICE$" | grep -v '^null$' | grep -v '^$' | paste -sd: -)
  if [ -z "$new" ]; then
    S settings delete secure enabled_accessibility_services >/dev/null
    S settings put secure accessibility_enabled 0 >/dev/null
  else
    S "settings put secure enabled_accessibility_services '$new'" >/dev/null
  fi
}

offer_restart() {
  blank
  if ask_yn "Restart the TV now so everything takes effect?" y; then
    A reboot >/dev/null 2>&1
    ok "Restarting. It'll be back in about a minute."
    say "  ${D}SimpleScript will need to reconnect afterwards. Run it again if you want to do more.${R}"
    exit 0
  fi
}

choices_file() { printf '%s/choices-%s.txt' "$DATA_DIR" "$MODEL"; }

saved_choice() {
  local f; f=$(choices_file)
  [ -f "$f" ] && grep -m1 "^$1=" "$f" | cut -d= -f2
}

# =====================================================================================
#  Debloat
# =====================================================================================
debloat() {
  heading "Debloat"
  say "  I'll go through what's on your TV. ${B}y${R} switches it off, ${B}n${R} keeps it."
  say "  Nothing is deleted, and ${B}Restore${R} switches everything back on."

  local present disabled line grp pkg desc rec how
  present=$(all_packages)
  disabled=$(disabled_list)

  # What's on this TV from the list, plus whether it's off already
  local items=()
  for line in "${DEBLOAT_LIST[@]}"; do
    IFS='|' read -r grp pkg desc rec how <<< "$line"
    printf '%s\n' "$present" | grep -qx "$pkg" || continue
    items+=("$line")
  done
  if [ ${#items[@]} -eq 0 ]; then
    ok "Nothing from the list is on this TV. It's already clean."
    return
  fi

  local have_saved=no new_count=0
  [ -f "$(choices_file)" ] && have_saved=yes
  for line in "${items[@]}"; do
    IFS='|' read -r grp pkg desc rec how <<< "$line"
    [ -z "$(saved_choice "$pkg")" ] && new_count=$((new_count+1))
  done

  blank
  say "  How would you like to choose?"
  local n=0 modes=()
  n=$((n+1)); modes+=("recommended"); say "  $n) Recommended ${D}(switch off adverts, tracking, pop-ups and forced updates; keep apps and Alexa)${R}"
  if [ "$have_saved" = "yes" ]; then
    n=$((n+1)); modes+=("saved")
    if [ "$new_count" -gt 0 ]; then
      say "  $n) Same as last time ${D}(and ask me about the $new_count new item(s))${R}"
    else
      say "  $n) Same as last time"
    fi
  fi
  n=$((n+1)); modes+=("each"); say "  $n) Let me choose each one"
  blank
  choose "Choose 1-$n (or q to go back):" "$n"
  [ "$CHOICE" = "q" ] && return
  local mode="${modes[$((CHOICE-1))]}"

  # Decide
  local decisions=() g gname gdesc current last default answer isnew
  for g in "${DEBLOAT_GROUPS[@]}"; do
    IFS='|' read -r gid gname gdesc <<< "$g"
    local shown=no
    for line in "${items[@]}"; do
      IFS='|' read -r grp pkg desc rec how <<< "$line"
      [ "$grp" = "$gid" ] || continue
      last=$(saved_choice "$pkg")
      isnew=no; [ -z "$last" ] && [ "$have_saved" = "yes" ] && isnew=yes
      default="$rec"; [ -n "$last" ] && default="$last"

      case "$mode" in
        recommended) answer="$rec" ;;
        saved) answer="$last" ;;
        each) answer="" ;;
      esac
      if [ -z "$answer" ]; then
        if [ "$shown" = "no" ]; then
          blank
          printf '  %s%s%s\n' "$B" "$gname" "$R"
          dim "  $gdesc"
          shown=yes
        fi
        current=""
        printf '%s\n' "$disabled" | grep -qx "$pkg" && current=" ${D}(off now)${R}"
        local tag=""; [ "$isnew" = "yes" ] && tag=" ${YELLOW}NEW${R}"
        local verb="Switch off"; [ "$how" = "uninstall" ] && verb="Remove"
        if ask_yn "$verb ${B}$desc${R}?$current$tag" "$default"; then answer=y; else answer=n; fi
      fi
      decisions+=("$pkg|$answer|$how|$desc")
    done
  done

  # Work out what changes
  local to_off=() to_on=() to_remove=() d p a h ds
  for d in "${decisions[@]}"; do
    IFS='|' read -r p a h ds <<< "$d"
    local is_off=no
    printf '%s\n' "$disabled" | grep -qx "$p" && is_off=yes
    if [ "$h" = "uninstall" ]; then
      [ "$a" = "y" ] && to_remove+=("$p|$ds")
    elif [ "$a" = "y" ] && [ "$is_off" = "no" ]; then
      to_off+=("$p|$ds")
    elif [ "$a" = "n" ] && [ "$is_off" = "yes" ]; then
      to_on+=("$p|$ds")
    fi
  done

  local faster=no
  blank
  if ask_yn "Also make menus feel faster ${D}(half-length animations)${R}?" y; then faster=yes; fi

  heading "Here's the plan"
  [ ${#to_off[@]} -gt 0 ] && { say "  Switch off (${#to_off[@]}):"; for d in "${to_off[@]}"; do say "    - ${d#*|}"; done; }
  [ ${#to_on[@]} -gt 0 ] && { say "  Switch back on (${#to_on[@]}):"; for d in "${to_on[@]}"; do say "    + ${d#*|}"; done; }
  [ ${#to_remove[@]} -gt 0 ] && { say "  Remove (${#to_remove[@]}):"; for d in "${to_remove[@]}"; do say "    x ${d#*|}"; done; }
  [ "$faster" = "yes" ] && say "  Faster animations"
  if [ ${#to_off[@]} -eq 0 ] && [ ${#to_on[@]} -eq 0 ] && [ ${#to_remove[@]} -eq 0 ] && [ "$faster" = "no" ]; then
    say "  Nothing to change. Your TV already matches your choices."
    save_choices "${decisions[@]}"
    return
  fi
  blank
  ask_yn "Go ahead?" y || { say "  OK, nothing was changed."; return; }

  blank
  local okc=0 failc=0
  for d in "${to_off[@]}"; do
    p="${d%%|*}"
    if S pm disable-user --user 0 "$p" | grep -q 'disabled'; then ok "Off: ${d#*|}"; okc=$((okc+1)); else bad "Fire OS refused: ${d#*|}"; failc=$((failc+1)); fi
  done
  for d in "${to_on[@]}"; do
    p="${d%%|*}"
    if S pm enable "$p" | grep -q 'enabled'; then ok "On: ${d#*|}"; okc=$((okc+1)); else bad "Couldn't switch on: ${d#*|}"; failc=$((failc+1)); fi
  done
  for d in "${to_remove[@]}"; do
    p="${d%%|*}"
    if A uninstall "$p" 2>&1 | grep -q Success; then ok "Removed: ${d#*|}"; okc=$((okc+1)); else bad "Couldn't remove: ${d#*|}"; failc=$((failc+1)); fi
  done
  if [ "$faster" = "yes" ]; then animations 0.5; ok "Faster animations"; fi
  S pm trim-caches 999G >/dev/null 2>&1

  save_choices "${decisions[@]}"
  blank
  if [ $failc -eq 0 ]; then ok "Debloat finished."; else warn "Debloat finished with $failc item(s) Fire OS wouldn't change."; fi
  offer_restart
}

save_choices() {
  mkdir -p "$DATA_DIR"
  local f; f=$(choices_file)
  local tmp="$f.tmp" d p a
  [ -f "$f" ] && cp "$f" "$tmp" || : > "$tmp"
  for d in "$@"; do
    IFS='|' read -r p a _ <<< "$d"
    grep -v "^$p=" "$tmp" > "$tmp.2"; mv "$tmp.2" "$tmp"
    printf '%s=%s\n' "$p" "$a" >> "$tmp"
  done
  mv "$tmp" "$f"
}

# =====================================================================================
#  Restore
# =====================================================================================
restore() {
  heading "Restore to stock"
  say "  This switches back on everything SimpleScript can switch off, puts the animation"
  say "  speed back to normal and gives the Home button back to Amazon."
  blank
  ask_yn "Restore your TV?" y || { say "  OK, nothing was changed."; return; }

  local disabled line grp pkg desc rec how count=0
  disabled=$(disabled_list)
  blank
  for line in "${DEBLOAT_LIST[@]}"; do
    IFS='|' read -r grp pkg desc rec how <<< "$line"
    [ "$how" = "disable" ] || continue
    printf '%s\n' "$disabled" | grep -qx "$pkg" || continue
    if S pm enable "$pkg" | grep -q enabled; then ok "On: $desc"; count=$((count+1)); else bad "Couldn't switch on: $desc"; fi
  done
  [ $count -eq 0 ] && ok "Nothing was switched off"
  animations 1.0; ok "Normal animation speed"
  if sl_installed; then
    homecatch_off; ok "HomeCatch off ${D}(the Home button opens Amazon's home screen)${R}"
    blank
    if ask_yn "Also uninstall SimpleLauncher?" n; then
      A uninstall "$SL_PKG" 2>&1 | grep -q Success && ok "SimpleLauncher removed" || bad "Couldn't remove SimpleLauncher"
    fi
  fi
  rm -f "$(choices_file)"
  dim "  Apps you removed yourself (like Downloader) aren't reinstalled. Get them from the Appstore."
  offer_restart
}

# =====================================================================================
#  Install SimpleLauncher
# =====================================================================================
APK=""

download_latest() {
  need curl || { bad "curl isn't installed, so I can't download from GitHub."; return 1; }
  note "Asking GitHub for the latest SimpleLauncher…"
  local json url tag
  json=$(curl -fsSL -H "Accept: application/vnd.github+json" "https://api.github.com/repos/$REPO/releases/latest") || {
    bad "Couldn't reach GitHub. Check this computer is online."; return 1; }
  tag=$(printf '%s' "$json" | grep -m1 '"tag_name"' | sed -E 's/.*"tag_name": *"([^"]+)".*/\1/')
  url=$(printf '%s' "$json" | grep '"browser_download_url"' | grep -m1 '\.apk"' | sed -E 's/.*"(https:[^"]+)".*/\1/')
  [ -n "$url" ] || { bad "The latest release doesn't have an APK attached."; return 1; }
  mkdir -p "$DATA_DIR/downloads"
  APK="$DATA_DIR/downloads/$(basename "$url")"
  note "Downloading build $tag…"
  curl -fL --progress-bar -o "$APK" "$url" || { bad "The download didn't finish."; rm -f "$APK"; return 1; }
  ok "Downloaded build $tag"
}

newest_local_apk() {
  ls -t "$HOME"/Desktop/SimpleLauncher-*.apk "$HOME"/Downloads/SimpleLauncher-*.apk \
        "$HOME/Desktop/SimpleLauncher/app/build/outputs/apk/debug/app-debug.apk" \
        "$HOME/Desktop/SimpleLauncher/app/build/outputs/apk/release/app-release.apk" 2>/dev/null | head -1
}

pick_local_apk() {
  local found path
  found=$(newest_local_apk)
  if [ -n "$found" ]; then
    say "  Found: ${B}$(printf '%s' "$found" | sed "s|^$HOME|~|")${R}"
    if ask_yn "Use this file?" y; then APK="$found"; return 0; fi
  fi
  while true; do
    say "  Drag the APK file into this window, or type its path, then press Return."
    read -r -p "  File: " path
    path=$(printf '%s' "$path" | sed -e "s/^[\"']//" -e "s/[\"']$//" -e 's/\\ / /g' -e 's/ *$//')
    path="${path/#\~/$HOME}"
    if [ -f "$path" ]; then APK="$path"; return 0; fi
    bad "There's no file at that path."
    ask_yn "Try again?" y || return 1
  done
}

install_launcher() {
  heading "Install SimpleLauncher"
  if sl_installed; then
    note "SimpleLauncher $(sl_version) is installed. This updates it and keeps your settings."
  fi
  blank
  say "  Where should SimpleLauncher come from?"
  say "  1) The latest release on GitHub ${D}(recommended)${R}"
  say "  2) An APK file on this computer"
  blank
  choose "Choose 1 or 2 (or q to go back):" 2
  [ "$CHOICE" = "q" ] && return
  local source="$CHOICE"

  # Ask everything up front, then do it all in one go.
  local want_homecatch=no block_updates=no
  if [ "$CAN_HOMECATCH" = "yes" ] && { [ "$SUPPORT" = "full" ] || [ "$SUPPORT" = "testing" ]; }; then
    blank
    say "  ${B}HomeCatch${R} makes the Home button on your remote open SimpleLauncher"
    say "  instead of Amazon's home screen. You can switch it off any time in"
    say "  SimpleLauncher's Settings › Danger Zone."
    [ "$SUPPORT" = "testing" ] && say "  ${D}It's still being tested on this device.${R}"
    ask_yn "Switch HomeCatch on?" y && want_homecatch=yes

    if installed "$UPDATER_PKG"; then
      if disabled_list | grep -qx "$UPDATER_PKG"; then
        note "Amazon's automatic updates are already switched off on this TV."
      else
        blank
        say "  ${B}Automatic Fire OS updates${R} install by themselves and can undo your changes or"
        say "  stop HomeCatch working. You can still update by hand in Settings › My Fire TV."
        ask_yn "Stop Fire OS updates installing automatically?" y && block_updates=yes
      fi
    fi
  fi

  blank
  if [ "$source" = "1" ]; then download_latest || return; else pick_local_apk || return; fi

  blank
  note "Installing on your TV…"
  local out
  out=$(A install -r "$APK" 2>&1 | tr -d '\r')
  if ! printf '%s' "$out" | grep -q Success; then
    if printf '%s' "$out" | grep -q 'INSTALL_FAILED_UPDATE_INCOMPATIBLE'; then
      bad "This copy is signed differently from the SimpleLauncher already on your TV."
      say "  To switch, the old one has to be removed first. ${B}This clears its settings.${R}"
      ask_yn "Remove the old SimpleLauncher and install this one?" n || return
      A uninstall "$SL_PKG" >/dev/null 2>&1
      out=$(A install "$APK" 2>&1 | tr -d '\r')
    elif printf '%s' "$out" | grep -q 'INSTALL_FAILED_VERSION_DOWNGRADE'; then
      bad "Your TV already has a newer SimpleLauncher than this file."
      return
    fi
  fi
  if ! printf '%s' "$out" | grep -q Success; then
    bad "The install didn't work:"
    dim "  $(printf '%s' "$out" | tail -1)"
    return
  fi
  ok "SimpleLauncher $(sl_version) installed"

  # Software update in Settings needs this once
  if [ "$SDK" -ge 26 ]; then
    if [ -z "$(S appops set "$SL_PKG" REQUEST_INSTALL_PACKAGES allow)" ]; then
      ok "Software update allowed ${D}(Settings › Information › Software update)${R}"
    else
      warn "Couldn't allow Software update. SimpleLauncher will ask on the TV the first time."
    fi
  fi

  case "$SUPPORT" in
    full|testing)
      if [ "$want_homecatch" = "yes" ]; then
        S pm grant "$SL_PKG" android.permission.WRITE_SECURE_SETTINGS >/dev/null
        if homecatch_on; then ok "HomeCatch on"; else bad "Fire OS didn't accept HomeCatch"; fi
      fi
      if [ "$block_updates" = "yes" ]; then
        if S pm disable-user --user 0 "$UPDATER_PKG" | grep -q disabled; then
          ok "Automatic Fire OS updates off"
        else
          bad "Fire OS wouldn't switch off automatic updates"
        fi
      fi
      ;;
    app)
      note "HomeCatch stays off on this Fire TV because it hasn't been tested yet."
      note "Open SimpleLauncher from ${B}Your Apps${R} on the Fire TV home screen."
      ;;
    generic)
      blank
      say "  To make SimpleLauncher your home screen, choose it on the TV:"
      S am start -a android.settings.HOME_SETTINGS >/dev/null 2>&1
      say "  ${B}Settings › Apps › Default apps › Home app › SimpleLauncher${R}"
      dim "  (I've tried to open that screen for you. Some TVs keep it somewhere else.)"
      ;;
  esac

  S am start -n "$SL_PKG/.MainActivity" >/dev/null 2>&1
  blank
  ok "All done. SimpleLauncher is open on your TV."
}

uninstall_launcher() {
  heading "Remove SimpleLauncher"
  ask_yn "Uninstall SimpleLauncher and its settings from this device?" n || return
  homecatch_off 2>/dev/null
  if A uninstall "$SL_PKG" 2>&1 | grep -q Success; then ok "SimpleLauncher removed"; else bad "Couldn't remove it"; fi
  if disabled_list | grep -qx "$UPDATER_PKG"; then
    if ask_yn "Also turn Amazon's automatic updates back on?" y; then
      S pm enable "$UPDATER_PKG" | grep -q enabled && ok "Automatic Fire OS updates on" || bad "Couldn't turn them back on"
    fi
  fi
}

# =====================================================================================
#  Menu
# =====================================================================================
menu() {
  while true; do
    heading "What would you like to do?"
    local actions=() n=0
    if sl_installed; then
      n=$((n+1)); actions+=("install"); say "  $n) Update SimpleLauncher ${D}(installed: $(sl_version))${R}"
    else
      n=$((n+1)); actions+=("install"); say "  $n) Install SimpleLauncher"
    fi
    if sl_installed && ! { [ "$SUPPORT" = "full" ] && [ "$CAN_DEBLOAT" = "yes" ]; }; then
      n=$((n+1)); actions+=("uninstall"); say "  $n) Remove SimpleLauncher"
    fi
    n=$((n+1)); actions+=("reboot"); say "  $n) Restart the TV"
    if [ "$SUPPORT" = "full" ] && [ "$CAN_DEBLOAT" = "yes" ]; then
      blank
      say "  ${B}Extras for your $SUPPORTED_NAME${R}"
      n=$((n+1)); actions+=("debloat"); say "  $n) Debloat ${D}(choose what to switch off)${R}"
      n=$((n+1)); actions+=("restore"); say "  $n) Restore to stock"
    fi
    say "  q) Quit"
    blank
    choose "Choose 1-$n:" "$n"
    [ "$CHOICE" = "q" ] && break
    case "${actions[$((CHOICE-1))]}" in
      install) install_launcher ;;
      debloat) debloat ;;
      restore) restore ;;
      uninstall) uninstall_launcher ;;
      reboot) A reboot >/dev/null 2>&1; ok "Restarting your TV."; break ;;
    esac
  done
  blank
  say "  Thanks for using SimpleScript. ${D}Problems or ideas: $REPO_URL/issues${R}"
}

# =====================================================================================
#  Start
# =====================================================================================
case "$1" in
  -h|--help)
    say "SimpleScript $SCRIPT_VERSION. Run it with no options and it walks you through everything:"
    say "  bash SimpleScript.sh"
    exit 0 ;;
esac

blank
say "${B}SimpleScript${R} ${D}v$SCRIPT_VERSION · by Cherry Universe${R}"
say "Sets up ${B}SimpleLauncher${R} on your TV, one step at a time."
dim "Press Ctrl+C at any point to stop."

check_tools
prepare_and_connect
detect_device
menu
