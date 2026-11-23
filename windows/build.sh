#!/bin/bash
# Builds SimpleScript.exe. The device and debloat lists come from ../SimpleScript.sh,
# so the Mac/Linux and Windows versions always offer the same things.
set -e
cd "$(dirname "$0")"
cp ../SimpleScript.sh lists.sh
GOOS=windows GOARCH=amd64 go build -trimpath -ldflags "-s -w" -o SimpleScript.exe .
GOOS=windows GOARCH=arm64 go build -trimpath -ldflags "-s -w" -o SimpleScript-arm64.exe .
rm -f lists.sh
echo "Built windows/SimpleScript.exe and windows/SimpleScript-arm64.exe"
