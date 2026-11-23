#!/bin/bash
# Re-renders docs/home-screen.png (the picture in the README) from docs/home-screen.html.
# Update home-screen.html whenever the home screen's design changes, then run this. Needs Google Chrome.
cd "$(dirname "$0")"
CHROME="${CHROME:-google-chrome}"
[ -x "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" ] && CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
"$CHROME" --headless=new --hide-scrollbars --force-device-scale-factor=1.5 --window-size=1280,720 \
  --screenshot="$PWD/home-screen.png" "file://$PWD/home-screen.html"
